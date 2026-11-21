"""Install the pinned E5 encoder for the 502-row screen activity model."""
import json
from hashlib import file_digest
from pathlib import Path

from huggingface_hub import snapshot_download
from transformers import AutoModel, AutoTokenizer

REPO = "intfloat/multilingual-e5-small"
REVISION = "614241f622f53c4eeff9890bdc4f31cfecc418b3"
ROOT = Path(__file__).resolve().parent
ENCODER = ROOT / "model" / "encoder"
FILES = (
    "config.json",
    "model.safetensors",
    "sentencepiece.bpe.model",
    "special_tokens_map.json",
    "tokenizer.json",
    "tokenizer_config.json",
)
WEIGHTS_SHA256 = "1a55775f53449dac10a2bcbc312469fac40b96d53198c407081a831f81c98477"


def verify_weights(path, expected):
    with path.open("rb") as handle:
        if file_digest(handle, "sha256").hexdigest() != expected:
            raise ValueError(f"E5 checkpoint checksum mismatch: {path}")


def main():
    snapshot = Path(snapshot_download(repo_id=REPO, revision=REVISION, allow_patterns=list(FILES)))
    verify_weights(snapshot / "model.safetensors", WEIGHTS_SHA256)
    model = AutoModel.from_pretrained(snapshot, local_files_only=True)
    tokenizer = AutoTokenizer.from_pretrained(snapshot, local_files_only=True)
    # Match the tokenizer settings saved with the experiment.
    tokenizer(["passage: setup"], padding=True, truncation=True, max_length=192)
    model.save_pretrained(ENCODER)
    tokenizer.save_pretrained(ENCODER)
    manifest = json.loads((ENCODER.parent / "manifest.json").read_text())
    verify_weights(ENCODER / "model.safetensors", manifest["encoderSHA256"])


if __name__ == "__main__":
    main()
