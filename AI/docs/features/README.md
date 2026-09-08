# AI 기능 문서

기능 하나당 아래 세 문서를 같은 기능명으로 생성한다.

```text
relevance-analysis-PLAN.md
relevance-analysis-ERROR.md
relevance-analysis-REPORT.md
```

기획서 작성과 입력·출력 계약 확인이 끝난 뒤 개발을 시작하고, 오류 보고서와 결과 리포트는 작업 중·작업 완료 시 갱신한다.

## 기능 개발 순서

1. `relevance-analysis`: OCR·페이지 텍스트 정제와 현재 활동 관련성 판단
2. `learning-flow-analysis`: 페이지 이동·체류 시간 기반 학습 흐름과 목표 이탈 판단
3. `user-state-analysis`: MediaPipe 얼굴·시선·자세 상태값 보조 분석
4. `external-content-analysis`: YouTube 자막·PDF 추출 텍스트 등 외부 콘텐츠 연결

각 기능은 별도 문서와 분석 모듈로 관리한다. 관련성 분석 PLAN에 체류 시간이나 MediaPipe 상태 판단을 함께 구현하지 않는다.
