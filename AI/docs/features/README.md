# AI 기능 문서

기능 하나당 아래 세 문서를 같은 기능명으로 생성한다.

```text
relevance-analysis-PLAN.md
relevance-analysis-ERROR.md
relevance-analysis-REPORT.md
```

기획서 작성과 입력·출력 계약 확인이 끝난 뒤 개발을 시작하고, 오류 보고서와 결과 리포트는 작업 중·작업 완료 시 갱신한다.

문서 검토·기획 단계에서는 기능별 `PLAN.md`만 둘 수 있다. 실제 구현 중 재현 가능한 오류가 발생했을 때만 `ERROR.md`를 만들고, 기능 구현이 완료된 경우에만 테스트 결과를 포함한 `REPORT.md`를 작성한다.

## 기능 개발 순서

1. `relevance-analysis`: OCR·페이지 텍스트 정제와 현재 활동 관련성 판단
2. `learning-flow-analysis`: 페이지 이동·체류 시간 기반 학습 흐름과 목표 이탈 판단
3. `user-state-analysis`: MediaPipe 얼굴·시선·자세 상태값 보조 분석
4. `external-content-analysis`: YouTube 자막·PDF 추출 텍스트 등 외부 콘텐츠 연결

각 기능은 별도 문서와 분석 모듈로 관리한다. 관련성 분석 PLAN에 체류 시간이나 MediaPipe 상태 판단을 함께 구현하지 않는다.

## 학교 캡스톤 구현 지침

- [입력 검증·전처리와 Hybrid 6번 관련성 구현·테스트 지침](learning-goal-content-relevance-evaluation-PLAN.md): 통합 #11의 이재빈 노션 두 문서 기준. 모델 없는 테스트와 실제 Qwen3 4B 평가를 구분한다.
- [학교 Windows Ollama 임베딩 설치·테스트 지침](school-ollama-embedding-SETUP.md): Qwen3 4B·FP16 설치, PowerShell API·속도 확인, Mac SSH 연결. 학교 실기 검증 전이며 추후 vLLM 전환 계획과 구분한다.
- [통합 #11 실제 구현·검증 결과](learning-goal-content-relevance-evaluation-REPORT.md): 전처리·Graph·Hybrid·Ollama Client·합성 CLI 구현과 학교 실측·공개 API 잔여 범위.
