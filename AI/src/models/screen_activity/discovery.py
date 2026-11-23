"""Text evidence for multi-item feeds, player surfaces and documents. No hostname features."""

import math
import re

import numpy as np

TILE_CUES = (
    r"추천|인기 급상승|피드|더 보기|컬렉션|발견|트렌딩|for you|trending|discover|popular now|recommended|explore",
    r"광고|스폰서|프로모션|후원|협찬|advertis(?:ement|ing)|sponsored|free trial|promoted",
    r"쇼츠|릴스|짧은 영상|shorts|clips|reels|short videos",
    r"연예|드라마|결승|적발|순위|맛집|여행|개봉|영화|경기|하이라이트|먹방|일상|챌린지|반응|베스트|화제|celebrity|gossip|trailer|comedy|highlights|prank|fashion|lifestyle|funny",
    r"재생 중|현재 재생|다음 곡|남은 시간|셔플|반복 재생|타이머|선곡표|now playing|next track|shuffle|on air|repeat one|elapsed|volume",
    r"배경음|집중 음악|로파이|공부용 음악|lo[ -]?fi|instrumental|ambient|white noise|playlist",
    r"강의|학습 과정|강좌|차시|교과|실습|문제 풀이|course|tutorial|syllabus|exercise|lecture|curriculum|lesson",
    r"세션|메서드|구성 요소|데이터 모델|설치 방법|요청 본문|매개변수|응답 코드|reference|arguments?|parameters?|method|documentation|endpoint|response body|configuration|examples?",
    r"논문|초록|실험 결과|선행 연구|정리 [0-9]|증명|abstract|introduction|methodology|experiments?|theorem|lemma|conclusion|references",
    r"\b\d{1,3}:\d\d\b|조회수|구독자|\bviews\b|\bsubscribers?\b|시간 전|일 전|hours? ago",
)
PATTERNS = [re.compile(cue, re.IGNORECASE) for cue in TILE_CUES]


def features(screens):
    out = []
    for title, full in screens:
        body = full[len(title) :].strip() if title and full.startswith(title) else full
        counts = [min(6, len(cue.findall(body))) for cue in PATTERNS]
        title_counts = [math.log1p(min(4, len(cue.findall(title)))) for cue in PATTERNS]
        # A mixed catalogue can display a music tile while a player shows controls for one item.
        catalogue = min(4, sum(n > 0 for n in (counts[0], counts[1], counts[2], counts[3], counts[9])))
        out.append(
            title_counts
            + [math.log1p(n) for n in counts]
            + [
                float(catalogue),
                float(catalogue > 1 and counts[5] > 0),
                float(catalogue > 1 and counts[6] > 0),
                float(counts[4] > 0 and counts[5] > 0),
                float((counts[7] + counts[8]) > 0 and len(body) > 250),
                math.log1p(min(12, body.count("|") + body.count(" · "))),
            ]
        )
    return np.asarray(out, dtype=np.float32)
