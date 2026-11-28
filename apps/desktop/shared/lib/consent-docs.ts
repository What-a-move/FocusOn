// 동의 문서. 온보딩 동의 단계의 `자세히`와 설정 `동의 내역 · 전문 보기`가 같은 내용을 보여 준다.
// 기준: Notion `개인정보 · 동의 · 데이터 보관 정책`(2.3 동의 화면 필수 표시 · 4 데이터 분류 · 5 민감 화면 · 6.1 보관 기간).
// [주의] 정책 문서가 아직 REVIEW 상태다. 출시 전 법률·개인정보 검토로 문구·기간이 바뀔 수 있다.

export type DocSection = { heading: string; items: string[] }
export type Detail = { title: string; required: boolean; summary: string; sections: DocSection[] }
export type Consent = { id: string; title: string; summary?: string; detail: Detail }

export const screenAnalysisSummary = '집중하는 동안 화면이 목표와 관련 있는지 확인해요. 관련 화면에서 확인한 학습 개념을 저장해 복습 노트를 만들어요. 화면 캡처와 글자 원문은 저장하지 않아요.'

export const requiredConsents: Consent[] = [
  {
    id: 'terms',
    title: '서비스 이용약관',
    detail: {
      title: '서비스 이용약관',
      required: true,
      summary: 'FocusOn을 쓰는 동안 지켜야 할 기본 약속이에요.',
      sections: [
        { heading: '이 약관이 정하는 것', items: [
          'FocusOn이 제공하는 기능: 목표 정하기, 집중 타이머, 화면 분석 알림, 집중 기록과 리포트',
          '서비스를 이용할 수 있는 조건과 계정을 관리하는 방법',
          '서비스가 바뀌거나 멈출 때 미리 알리는 방법',
        ] },
        { heading: '꼭 알아 두세요', items: [
          'FocusOn은 목표를 끝냈는지나 집중력을 평가하지 않아요. 알림은 다시 집중하도록 돕는 참고용이에요.',
          '다른 사람의 계정을 쓰거나 서비스 운영을 방해하면 이용이 제한될 수 있어요.',
        ] },
        { heading: '처리 항목과 보관', items: ['동의 여부와 동의한 시각만 기록해요.', '철회하거나 계정을 삭제한 뒤 3년 동안 동의 이력만 보관해요.'] },
        { heading: '동의하지 않으면', items: ['동의하지 않을 수 있어요. 다만 FocusOn을 이용할 수 없어요.'] },
      ],
    },
  },
  {
    id: 'privacy',
    title: '개인정보 처리방침',
    summary: 'Google 계정 식별자, 닉네임, 목표, 집중 기록',
    detail: {
      title: '개인정보 처리방침',
      required: true,
      summary: '계정과 집중 기록을 관리하는 데 필요한 정보만 받아요.',
      sections: [
        { heading: '왜 받나요', items: ['계정을 만들고 관리해요.', '집중 기록과 리포트를 보여 드려요.', '연결한 Mac과 Chrome 확장을 관리해요.'] },
        { heading: '무엇을 받나요', items: [
          'Google 계정 식별자, 닉네임',
          '목표, 집중 시작·종료 시각과 집중한 시간',
          '연결한 기기 정보(이 Mac, Chrome 확장)',
          '남겨 주신 회고는 FocusOn을 개선하는 데만 써요.',
        ] },
        { heading: '얼마나 보관하나요', items: [
          '집중 기록·리포트·학습 노트: 세션이 끝난 뒤 1년',
          '로그인·기기 연결 정보: 만료되거나 해제한 뒤 30일',
          '계정을 삭제하면 30일 안에 지우고, 백업은 최대 35일 뒤 자동으로 지워져요.',
        ] },
        { heading: '동의하지 않으면', items: ['동의하지 않을 수 있어요. 다만 계정을 만들 수 없어요.'] },
      ],
    },
  },
  {
    id: 'screen-analysis',
    title: '화면 분석',
    summary: '화면이 목표와 관련 있는지 확인하고 복습 노트를 만들어요',
    detail: {
      title: '화면 분석',
      required: true,
      summary: screenAnalysisSummary,
      sections: [
        { heading: '무엇을 확인하나요', items: [
          '앱 이름과 창 제목, Chrome 탭 제목과 도메인',
          '앱 정보만으로 판단하기 어려울 때만, 지금 보이는 화면의 글자',
        ] },
        { heading: '무엇을 저장하나요', items: [
          '화면이 목표와 관련 있는지 판단한 결과와 시각',
          '목표와 관련 있는 화면에서 확인한 학습 개념과 근거의 종류. 복습 노트를 만드는 데 써요.',
          '학습용으로 고르지 않은 메신저는 내용을 보지 않고 사용 시간만 딴짓으로 세요.',
        ] },
        { heading: '학습 정리 근거에 저장하지 않는 것', items: ['화면 캡처와 글자 원문, 창·탭 제목, 전체 주소', '비밀번호·인증번호·결제 정보 등 개인정보', '메시지·이메일 본문'] },
        { heading: '보지 않는 화면', items: [
          '비밀번호·결제·인증·메시지처럼 민감한 화면은 자동으로 건너뛰어요.',
          '설정에서 제외한 앱과 사이트도 내용을 보지 않고 시간만 기록해요.',
        ] },
        { heading: '얼마나 보관하나요', items: ['판단 결과와 학습 정리 근거는 세션이 끝난 뒤 90일 동안 보관해요.', '복습 노트는 세션이 끝난 뒤 1년 동안 보관해요.'] },
        { heading: '동의하지 않으면', items: ['동의하지 않을 수 있어요. 다만 집중을 시작할 수 없어요.', '동의한 뒤에도 설정에서 언제든 철회할 수 있어요.'] },
      ],
    },
  },
]

export const cameraDetail: Detail = {
  title: '카메라 분석',
  required: false,
  summary: '오래 자리를 비운 순간을 알아차리는 데만 써요. 영상은 저장하지 않아요.',
  sections: [
    { heading: '무엇을 확인하나요', items: ['얼굴이 보이는지, 시선이 화면 쪽인지 같은 상태만 이 Mac 안에서 계산해요.'] },
    { heading: '저장하지 않는 것', items: ['카메라 영상과 사진', '얼굴 특징 정보'] },
    { heading: '얼마나 보관하나요', items: ['자리 비움 같은 상태 기록은 세션이 끝난 뒤 1년'] },
    { heading: '동의하지 않으면', items: ['선택 항목이라 켜지 않아도 돼요. 화면 분석만으로 정상 이용할 수 있어요.', '설정에서 언제든 켜고 끌 수 있어요.'] },
  ],
}
