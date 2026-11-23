"""Upstage-only entry point for the local goal expansion comparison."""

from contextlib import contextmanager
from functools import partial

from openai import OpenAI

from AI.evaluation.goal_profile_experiment import ExperimentEmbeddings, generate_profile
from AI.evaluation.goal_profile_streamlit import main


UPSTAGE_API_URL = "https://api.upstage.ai/v1"
QUERY_MODEL = "solar-embedding-2-query"
PASSAGE_MODEL = "solar-embedding-2-passage"


@contextmanager
def upstage_embeddings(api_key):
    with OpenAI(api_key=api_key, base_url=UPSTAGE_API_URL, timeout=10, max_retries=0) as client:
        yield ExperimentEmbeddings(client, query_model=QUERY_MODEL, passage_model=PASSAGE_MODEL, dimensions=1024)


if __name__ == "__main__":
    main(
        provider="Upstage",
        key_name="UPSTAGE_API_KEY",
        embedding_models=f"{QUERY_MODEL} / {PASSAGE_MODEL}",
        profile_generator=partial(
            generate_profile, base_url=UPSTAGE_API_URL, model="solar-mini4", reasoning_effort="minimal",
        ),
        embedding_client=upstage_embeddings,
    )
