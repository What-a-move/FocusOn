"""Three-class screen-activity predictor for a single, prevalidated foreground target."""

import hashlib
import json
import unicodedata
from pathlib import Path

import numpy as np
import torch
from sklearn.feature_extraction import FeatureHasher
from sklearn.feature_extraction.text import HashingVectorizer
from torch.nn import functional as F
from transformers import AutoModel, AutoTokenizer

from .discovery import features as discovery_features
from .evidence import features as evidence_features
from .topic_bank import TOPIC_BANK_FILE, TopicBank

ROOT = Path(__file__).resolve().parent
MODEL_DIR = ROOT / "model"
LABELS = ("STUDY", "OFF_TASK", "UNCERTAIN")
MARGIN_PP = 1.0
NATIVE = {"APP_META", "DESKTOP_APP_OCR"}
SOURCES = NATIVE | {"TAB_META", "CHROME_DOM"}
CODE_NAMES = {"code", "visual studio code", "code - insiders", "visual studio code - insiders"}
CHROME_NAMES = {
    "chrome",
    "com.google.chrome",
    "google chrome",
    "google chrome beta",
    "google chrome canary",
    "google chrome dev",
}


def parts(payload):
    metadata = payload.get("metadata") or payload
    title = metadata.get("pageTitle") or metadata.get("windowTitle") or ""
    body = (payload.get("contentPayload") or payload).get("text") or ""
    title, body = (unicodedata.normalize("NFC", text).strip() for text in (title, body))
    while title and (body == title or body.startswith((title + " ", title + "\n"))):
        body = body[len(title) :].strip()
    return title, "\n".join(text for text in (title, body) if text)


def app_identity(payload):
    return (payload.get("metadata") or payload).get("appName", "").strip().casefold()


def native_editor(payload):
    return payload.get("contentSource") in NATIVE and app_identity(payload) in CODE_NAMES


def validate_route(payload):
    source = payload.get("contentSource")
    if payload.get("guardStatus") is not None or source not in SOURCES:
        raise ValueError("분석이 허용된 전경 화면만 입력할 수 있습니다.")
    if source in NATIVE and app_identity(payload) in CHROME_NAMES:
        raise ValueError("Chrome 화면에는 탭 메타데이터 또는 DOM을 사용해 주세요.")


class ScreenEncoder:
    def __init__(self, directory):
        self.device = "mps" if torch.backends.mps.is_available() else "cpu"
        # transformers 4.57.3 warns about a Mistral regex for any local config saved by
        # 4.57.3. This XLM-R tokenizer uses Metaspace, so the flag only silences that false alarm.
        self.tokenizer = AutoTokenizer.from_pretrained(
            directory, local_files_only=True, fix_mistral_regex=False
        )
        self.model = AutoModel.from_pretrained(directory, local_files_only=True).eval().to(self.device)
        self.words = HashingVectorizer(
            n_features=4096, ngram_range=(1, 2), binary=True, alternate_sign=False, dtype=np.float32
        )
        self.chars = HashingVectorizer(
            analyzer="char", ngram_range=(3, 5), n_features=2048, alternate_sign=False, dtype=np.float32
        )
        self.sources = FeatureHasher(
            n_features=256, input_type="string", alternate_sign=False, dtype=np.float32
        )

    @torch.inference_mode()
    def embed(self, texts, *, role="passage"):
        unique = list(dict.fromkeys(text for text in texts if text))
        vectors = {"": np.zeros(self.model.config.hidden_size, dtype=np.float32)}
        for start in range(0, len(unique), 12):
            batch = unique[start : start + 12]
            tokens = self.tokenizer(
                [f"{role}: {text}" for text in batch],
                padding=True,
                truncation=True,
                max_length=192,
                return_tensors="pt",
            ).to(self.device)
            hidden = self.model(**tokens).last_hidden_state.cpu()
            mask = tokens.attention_mask.cpu().unsqueeze(-1)
            embeddings = F.normalize((hidden * mask).sum(1) / mask.sum(1), dim=1).numpy()
            vectors.update(zip(batch, embeddings, strict=True))
        return vectors

    def features(self, payloads, scales):
        screens = [parts(payload) for payload in payloads]
        titles, texts = zip(*screens)
        vectors = self.embed(text for screen in screens for text in screen)
        full = np.stack([vectors[text] for text in texts])
        tails = np.zeros_like(full)
        # The fitted head includes a separate E5 feature for the end of long screens.
        long_indices = [i for i, text in enumerate(texts) if len(text) > 720]
        if long_indices:
            endings = [parts({"text": texts[i][-650:]})[1] for i in long_indices]
            tail_vectors = self.embed(endings)
            tails[long_indices] = np.stack([tail_vectors[text] for text in endings])
        blocks = (
            full,
            np.stack([vectors[text] for text in titles]),
            self.words.transform(texts).toarray(),
            self.chars.transform(texts).toarray(),
            self.sources.transform([["source=" + p["contentSource"]] for p in payloads]).toarray(),
            evidence_features(screens),
            tails,
            discovery_features(screens),
        )
        return np.concatenate(
            [block * scale for block, scale in zip(blocks, scales, strict=True) if scale], axis=1
        )


def model_result(values):
    rank = np.argsort(values)[::-1]
    gap = float(100 * (values[rank[0]] - values[rank[1]]))
    raw = LABELS[rank[0]]
    return {
        "label": "UNCERTAIN" if gap <= MARGIN_PP else raw,
        "rawLabel": raw,
        "marginPp": gap,
        "scores": dict(zip(LABELS, map(float, values), strict=True)),
        "decisionSource": "model",
    }


class ActivityClassifier:
    def __init__(self, directory=MODEL_DIR):
        self.manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
        for key, filename in (
            ("headSHA256", "activity-head.pt"),
            ("encoderSHA256", "encoder/model.safetensors"),
            ("topicBankSHA256", TOPIC_BANK_FILE),
        ):
            with (directory / filename).open("rb") as handle:
                if hashlib.file_digest(handle, "sha256").hexdigest() != self.manifest[key]:
                    raise ValueError(f"체크포인트 SHA-256이 일치하지 않습니다 ({filename}).")
        self.encoder = ScreenEncoder(directory / "encoder")
        # Embedded once at startup; goal matching compares a screen against these topics.
        self.topic_bank = TopicBank.load(directory / TOPIC_BANK_FILE, self.encoder)
        self.head = torch.load(directory / "activity-head.pt", weights_only=True, map_location="cpu")

    def predict(self, payloads):
        # Validate every observation, including policy shortcuts, before running inference.
        for payload in payloads:
            validate_route(payload)
        rows = []
        positions = []
        for i, payload in enumerate(payloads):
            if native_editor(payload):
                rows.append(
                    {
                        "label": "STUDY",
                        "rawLabel": None,
                        "marginPp": None,
                        "scores": None,
                        "decisionSource": "native_editor_policy",
                    }
                )
            elif not parts(payload)[1]:
                rows.append(
                    {
                        "label": "UNCERTAIN",
                        "rawLabel": None,
                        "marginPp": None,
                        "scores": None,
                        "decisionSource": "empty",
                    }
                )
            else:
                positions.append(i)
                rows.append(None)
        if positions:
            matrix = self.encoder.features([payloads[i] for i in positions], self.head["scales"])
            logits = torch.from_numpy(matrix) @ self.head["weights"].T + self.head["bias"]
            for i, values in zip(positions, logits.softmax(dim=1).numpy(), strict=True):
                rows[i] = model_result(values)
        return rows
