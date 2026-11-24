"""Generate the fixed study-topic bank used as open-set alternatives for goal matching.

A screen is attributed to a selected task only when that task explains the screen at least
as well as other study topics. This bank supplies those other topics. It is generated once
from field names only; no user goal or screen content is sent.

    PYTHONPATH=AI:. python AI/evaluation/build_topic_bank.py
"""
import asyncio
import json
import os
from pathlib import Path

from anthropic import AsyncAnthropic
from pydantic import BaseModel

from AI.src.models.screen_activity.activity import MODEL_DIR

BANK_FILE = MODEL_DIR / "topic-bank.json"
PER_FIELD = 30
FIELDS = (
    # Frontend
    "React", "Next.js", "Vue.js", "Angular", "Svelte", "HTML", "CSS 레이아웃", "CSS 스타일링과 애니메이션",
    "JavaScript 기초", "JavaScript 비동기와 고급 문법", "TypeScript", "브라우저와 Web API",
    "프론트엔드 상태 관리 라이브러리", "프론트엔드 테스트",
    # Backend
    "Spring Boot", "Spring MVC", "Spring Security", "JPA와 Hibernate", "Node.js와 Express", "NestJS", "Django",
    "FastAPI와 Flask", "REST API 설계", "GraphQL", "인증과 인가",
    # Database
    "SQL 문법", "PostgreSQL", "MySQL", "데이터베이스 설계와 정규화", "트랜잭션과 인덱스", "MongoDB", "Redis",
    # Computer science
    "자료구조", "정렬과 탐색 알고리즘", "그래프 알고리즘", "동적 계획법", "코딩테스트 구현 문제", "운영체제",
    "컴퓨터 네트워크", "컴퓨터 구조", "정보 보안", "소프트웨어 공학과 디자인 패턴",
    # DevOps and cloud
    "Git", "GitHub 협업", "Docker", "Kubernetes", "AWS", "Linux 명령어", "CI/CD",
    # Data and AI
    "Python 기초 문법", "Python 고급 문법", "pandas", "NumPy", "matplotlib과 seaborn", "통계 분석",
    "scikit-learn 머신러닝", "PyTorch", "TensorFlow와 Keras", "자연어 처리", "LLM 활용과 프롬프트", "컴퓨터 비전",
    # Mobile and other languages
    "Android 앱 기본", "Kotlin", "Jetpack Compose", "iOS와 Swift", "SwiftUI", "Flutter와 Dart", "React Native",
    "C 언어", "C++", "Java 언어", "Go 언어", "Rust", "Unity 게임 개발",
    # School subjects and certificates
    "미적분", "선형대수", "확률과 통계", "이산수학", "물리", "화학", "생명과학", "영어 문법", "토익",
    "영어 회화", "일본어", "중국어", "한국사", "세계사", "경제학", "경영학", "회계", "정보처리기사", "SQLD",
    "컴퓨터활용능력", "UI/UX 디자인", "Figma",
)
PROMPT = """학습 집중 앱 사용자가 '오늘 할 일'로 적을 만한 공부 항목을 만든다.
분야: {field}

- 서로 겹치지 않는 세부 주제 {count}개를 만든다. 같은 분야 안에서 헷갈리기 쉬운 이웃 주제도 각각 따로 포함한다.
- task: 사용자가 직접 적는 한국어 할 일 문장. 40자 이하. 사용자처럼 분야의 기술·과목 이름을 반드시 넣고 원어 그대로 쓴다.
  예) "React useEffect로 외부 시스템과 동기화하기", "Jetpack Compose remember로 상태 보존하기", "미적분 정적분으로 넓이 구하기"
- topic: 기술·과목 이름을 포함한 짧은 주제 이름. 예) "React useEffect", "Jetpack Compose remember"
"""


class Topic(BaseModel):
    task: str
    topic: str


class Topics(BaseModel):
    topics: list[Topic]


async def generate(client, model, field):
    for _ in range(3):
        try:
            result = await client.messages.parse(
                model=model, max_tokens=4096, output_format=Topics,
                messages=[{"role": "user", "content": PROMPT.format(field=field, count=PER_FIELD)}])
            if result.stop_reason == "end_turn" and result.parsed_output is not None:
                # Same length rule as a confirmed goal (2-40 characters).
                return [{"field": field, **topic.model_dump()} for topic in result.parsed_output.topics
                        if 4 <= len(topic.task.strip()) <= 40 and 2 <= len(topic.topic.strip()) <= 40]
        except Exception as exc:  # retry transient provider or schema failures
            print(field, type(exc).__name__, flush=True)
            await asyncio.sleep(2)
    raise RuntimeError(f"topic generation failed: {field}")


async def main():
    client = AsyncAnthropic(api_key=os.environ["ANTHROPIC_API_KEY"])
    model = os.environ.get("CLAUDE_MODEL", "claude-haiku-4-5-20251001")
    limit = asyncio.Semaphore(4)

    async def bounded(field):
        async with limit:
            return await generate(client, model, field)

    batches = await asyncio.gather(*(bounded(field) for field in FIELDS))
    seen, bank = set(), []
    for item in (topic for batch in batches for topic in batch):
        key = " ".join(item["task"].split()).casefold()
        if key not in seen:
            seen.add(key)
            bank.append({k: " ".join(item[k].split()) for k in ("field", "task", "topic")})
    BANK_FILE.write_text(json.dumps({"model": model, "fields": list(FIELDS), "topics": bank},
                                    ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(BANK_FILE.name, len(bank))


if __name__ == "__main__":
    asyncio.run(main())
