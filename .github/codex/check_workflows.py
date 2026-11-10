"""Validate the automation contract after parsing YAML (requires PyYAML)."""
from pathlib import Path
import yaml

root = Path(__file__).resolve().parents[2]
load = lambda name: yaml.load((root / '.github/workflows' / name).read_text(), Loader=yaml.BaseLoader)
review = load('ai-codex-review.yml')
draft = load('codex-reply-draft.yml')
assert review['on']['pull_request']['paths'] == ['AI/**']
assert review['on']['pull_request']['branches'] == ['dev']
assert set(review['on']['pull_request']['types']) == {'opened', 'synchronize', 'reopened', 'ready_for_review'}
assert set(draft['on']) == {'issue_comment', 'pull_request_review_comment', 'pull_request_review'}
assert review['jobs']['post_review']['if'] == "needs.review.outputs.eligible == 'true'"
assert draft['jobs']['save_draft']['if'] == "needs.draft.outputs.eligible == 'true'"
assert all(v == 'read' for v in draft['permissions'].values())
for workflow in (review, draft):
    for name, job in workflow['jobs'].items():
        if workflow is draft or name == 'review':
            assert all(v == 'read' for v in job.get('permissions', {}).values())
        codex = [s for s in job['steps'] if s.get('uses') == 'openai/codex-action@v1']
        if codex:
            assert job['steps'][-1] is codex[0]
            assert codex[0]['with']['sandbox'] == 'read-only'
            assert codex[0]['with']['safety-strategy'] == 'drop-sudo'
            assert job['timeout-minutes'] == '12'
        for step in job['steps']:
            if step.get('uses') == 'actions/checkout@v6':
                assert step['with']['persist-credentials'] == 'false'
                assert step['with'].get('ref')
for job in draft['jobs'].values():
    for step in job['steps']:
        script = step.get('with', {}).get('script', '')
        assert 'publish(' not in script and 'createComment' not in script
assert 'head changed' in (root / '.github/codex/automation.cjs').read_text()
print('Workflow contracts PASS')
