"""Label-agnostic counts of UI and content signals shared across languages and sites."""

import math
import re

import numpy as np

CUES = [
    r"장바구니|add to (cart|bag)|buy now|바로 구매|구매하기|배송비|무료배송|결제하기|checkout",
    r"(?:₩|\$|€)\s*\d|\d[\d,]*\s*원\b|\b(?:KRW|USD|EUR)\b",
    r"옵션 선택|상품 목록|판매가|최저가|상품평|혜택가|재고|할인|세일|사이즈|품절|free shipping|in stock|out of stock|select size|shop (men|women)|sale price",
    r"득점|실점|승점|승률|홈런|타율|타점|방어율|세트 득실|정규리그|경기 결과|경기 일정|플레이오프|오프사이드|rebounds|home runs?|touchdown|pitcher|goals? scored|innings?|assists",
    r"팀 순위|선수 기록|선수 순위|리그 순위|중계|스코어|경기 기록|선발|경기장|선수단|scoreboard|box score|standings|fixtures|play.by.play|matchday|match results|playoffs",
    r"\b\d{1,3}\s*[-:]\s*\d{1,3}\b|\d+승\s*\d+패|승\s*무\s*패|순위\s*팀|\b(?:W\s+L|PTS\s+REB|GF\s+GA)\b",
    r"공식 뮤직비디오|뮤직비디오|직캠|댄스 커버|경기 하이라이트|역전골|게임 플레이|플레이 영상|스피드런|official (music video|trailer)|fancam|dance practice|gameplay|speedrun|walkthrough gameplay|match highlights",
    r"재생 대기|선곡표|청취|주파수|곡 목록|노래 모음|재생목록|음원|전곡|연속 듣기|now playing|tune in|station directory|radio station|track list|tracklist|playlist|listen live",
    r"타이머|뽀모도로|집중 시간|휴식 시간|배경음|백색 소음|pomodoro|countdown|focus timer|study with me|break timer|white noise",
    r"업로드.{0,8}(테스트|확인)|녹화.{0,8}(시험|점검|테스트)|송출.{0,8}(시험|점검|테스트)|연결 점검|화질 확인|test (recording|upload|footage|stream)|stream (check|test)|mic check",
    r"파일 선택|파일을 선택|붙여넣|템플릿 선택|업로드하세요|불러오기|파일을 끌어|choose file|paste your|upload (a|your)|drop (a|your|files)|select template|load example|new project",
    r"공식 문서|튜토리얼|강좌|강의|실습|예제|해설|수업|학습 목표|tutorial|documentation|worked example|lesson|workshop|reference guide|getting started|learn how|code sample",
    r"구현|디버깅|행렬|역전파|최적화|복잡도|매개변수|자료 구조|테스트 코드|오류 해결|함수|implementation|dependency|constructor|gradient|unit test|debugging|regression analysis|tensor|compile",
    r"\b(def|assert|import|class|select|from|join|async|await)\s+[a-z_]|\b(const|let|var|func|return)\s+[a-z_]|=>|torch\.|np\.|pd\.|\.fit\(|\.predict\(",
]
PATTERNS = [re.compile(x, re.IGNORECASE) for x in CUES]


def features(titles_and_full):
    rows = []
    for title, full in titles_and_full:
        body = full[len(title) :] if title and full.startswith(title) else full
        rows.append(
            [math.log1p(min(8, len(rx.findall(text)))) for text in (title, body) for rx in PATTERNS]
        )
    return np.array(rows, dtype=np.float32)
