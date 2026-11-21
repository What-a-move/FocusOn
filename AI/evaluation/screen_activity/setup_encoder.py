"""Install the pinned E5 encoder for the 4,743-row screen activity model."""

import json
import shutil
from hashlib import file_digest
from pathlib import Path

from huggingface_hub import snapshot_download
from transformers import AutoModel, AutoTokenizer

REPO = "intfloat/multilingual-e5-small"
REVISION = "614241f622f53c4eeff9890bdc4f31cfecc418b3"
ROOT = Path(__file__).resolve().parent
ENCODER = ROOT / "model" / "encoder"
TOKENIZER_FILES = {
    "config.json": "3005566b52c4f854e4a2aa427ce85a0c03e8585342bafc776bf92157341bdbe5",
    "sentencepiece.bpe.model": "cfc8146abe2a0488e9e2a0c56de7952f7c11ab059eca145a0a727afce0db2865",
    "special_tokens_map.json": "38d989b0fdad0fec0c67c14b1f3c8b68184022cf6d4adc5444526ced8653f738",
    "tokenizer.json": "7ef60e1b3e8f648ae4a9e1e39d3ade177352d78dbfacf217501461c2cce9af3b",
    "tokenizer_config.json": "dbdcd9767f0481fd8fd8cd6bce3e73dae7f5c44ce22ae1fde00a66498e71b454",
}
WEIGHTS_SHA256 = "1a55775f53449dac10a2bcbc312469fac40b96d53198c407081a831f81c98477"


def verify_weights(path, expected):
    with path.open("rb") as handle:
        if file_digest(handle, "sha256").hexdigest() != expected:
            raise ValueError(f"E5 checkpoint checksum mismatch: {path}")


def main():
    manifest = json.loads((ENCODER.parent / "manifest.json").read_text())
    expected = {**TOKENIZER_FILES, "model.safetensors": manifest["encoderSHA256"]}
    try:
        for name, sha in expected.items():
            verify_weights(ENCODER / name, sha)
    except (FileNotFoundError, ValueError):
        pass
    else:
        return
    snapshot = Path(snapshot_download(repo_id=REPO, revision=REVISION, allow_patterns=list(expected)))
    verify_weights(snapshot / "model.safetensors", WEIGHTS_SHA256)
    model = AutoModel.from_pretrained(snapshot, local_files_only=True)
    tokenizer = AutoTokenizer.from_pretrained(snapshot, local_files_only=True)
    # Match the tokenizer settings saved with the experiment.
    tokenizer(["passage: setup"], padding=True, truncation=True, max_length=192)
    model.save_pretrained(ENCODER)
    tokenizer.save_pretrained(ENCODER)
    # Fast tokenizers can omit the original SentencePiece vocabulary when saving.
    shutil.copyfile(snapshot / "sentencepiece.bpe.model", ENCODER / "sentencepiece.bpe.model")
    for name, sha in expected.items():
        verify_weights(ENCODER / name, sha)


if __name__ == "__main__":
    main()
