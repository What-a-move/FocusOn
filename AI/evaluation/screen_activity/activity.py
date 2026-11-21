"""Three-class screen-activity predictor with a one-percentage-point abstention band."""
import json
import unicodedata
from pathlib import Path

import numpy as np
import torch
from sklearn.feature_extraction.text import HashingVectorizer
from torch.nn import functional as F
from transformers import AutoModel, AutoTokenizer

ROOT = Path(__file__).resolve().parent
MODEL_DIR = ROOT / 'model'
LABELS = ('STUDY', 'OFF_TASK', 'UNCERTAIN')
NAMES = dict(zip(LABELS, ('공부 중', '딴짓', '판단 보류')))
MARGIN_PP = 1.0

def load_cases(path):
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]

def parts(payload):
    meta = payload.get('metadata') or payload
    title = meta.get('pageTitle') or meta.get('windowTitle') or ''
    body = (payload.get('contentPayload') or payload).get('text') or ''
    title, body = (unicodedata.normalize('NFC', text).strip() for text in (title, body))
    while title and (body == title or body.startswith((title+' ', title+'\n'))):
        body = body[len(title):].strip()
    return title, '\n'.join(t for t in (title, body) if t)

class ScreenEncoder:
    def __init__(self, directory):
        self.device = 'mps' if torch.backends.mps.is_available() else 'cpu'
        self.tokenizer = AutoTokenizer.from_pretrained(directory, local_files_only=True)
        self.model = AutoModel.from_pretrained(directory,local_files_only=True).eval().to(self.device)
        self.words = HashingVectorizer(n_features=4096, ngram_range=(1,2), binary=True, alternate_sign=False, dtype=np.float32)

    @torch.inference_mode()
    def features(self, payloads):
        screens = [parts(payload) for payload in payloads]
        texts = list(dict.fromkeys(text for pair in screens for text in pair if text))
        vector = {'': np.zeros(self.model.config.hidden_size, dtype=np.float32)}
        for start in range(0, len(texts), 12):
            batch = texts[start:start+12]
            x = self.tokenizer(['passage: '+t for t in batch], padding=True, truncation=True, max_length=192, return_tensors='pt').to(self.device)
            hidden = self.model(**x).last_hidden_state.cpu()
            mask = x.attention_mask.cpu().unsqueeze(-1)
            emb = F.normalize((hidden*mask).sum(1) / mask.sum(1), dim=1).numpy()
            vector.update(zip(batch, emb))
        titles, full = zip(*screens)
        return np.stack([vector[t] for t in full]), np.stack([vector[t] for t in titles]), self.words.transform(full).toarray()

def combine(features, title_scale, word_scale):
    full, titles, words = features
    arrays = [full]
    if title_scale: arrays.append(titles*title_scale)
    if word_scale: arrays.append(words*word_scale)
    return np.concatenate(arrays, axis=1)

class ActivityClassifier:
    def __init__(self,directory=MODEL_DIR):
        self.encoder = ScreenEncoder(directory/'encoder')
        self.head = torch.load(directory/'activity-head.pt', weights_only=True, map_location='cpu')

    def predict(self,payloads):
        if not payloads: return []
        x = combine(self.encoder.features(payloads), self.head['title_scale'],self.head['word_scale'])
        probs = (torch.from_numpy(x) @ self.head['weights'].T + self.head['bias']).softmax(dim=1)
        rows = []
        for payload, probs_row in zip(payloads, probs.tolist()):
            top = np.argsort(probs_row)[::-1]
            margin = 100*(probs_row[top[0]]-probs_row[top[1]])
            raw = LABELS[top[0]]
            if not parts(payload)[1]:
                rows.append(dict(label='UNCERTAIN',rawLabel=None,marginPp=None,scores=None))
                continue
            rows.append(dict(label='UNCERTAIN' if margin<=MARGIN_PP else raw,rawLabel=raw,marginPp=margin,scores=dict(zip(LABELS,probs_row))))
        return rows
