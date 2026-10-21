'use client'

import { useRef, useState } from 'react'
import { OnboardingShell } from './OnboardingShell'

type Detail = {
  id: string
  title: string
  purpose: string
  items: string
  retention: string
  refusal: string
  impact: string
  version?: string
}

const requiredConsents: Detail[] = [
  {
    id: 'terms',
    title: '서비스 이용약관',
    purpose: '서비스 이용 조건과 책임 범위를 안내합니다.',
    items: '동의 여부와 동의 시각',
    retention: '동의 철회 또는 계정 삭제 후 3년 (동의 이력만)',
    refusal: '동의하지 않을 수 있습니다.',
    impact: 'FocusOn을 이용할 수 없습니다.',
    version: 'v1.0',
  },
  {
    id: 'privacy',
    title: '개인정보 처리방침',
    purpose: '계정 관리, 집중 기록과 리포트를 제공합니다.',
    items: 'Google 계정 식별자, 닉네임, 목표, 세션 기록, 연결 기기 정보',
    retention: '집중 기록·리포트는 세션 종료 후 1년. 계정 삭제 시 30일 안에 삭제합니다.',
    refusal: '동의하지 않을 수 있습니다.',
    impact: '계정을 만들 수 없습니다.',
    version: 'v1.0',
  },
  {
    id: 'screen-analysis',
    title: '화면 분석 동의',
    purpose: '현재 활동이 학습 목표와 관련 있는지 확인합니다.',
    items: '앱 이름, 탭 제목·도메인, 필요할 때 화면에서 읽은 글자. 화면 원문과 캡처는 저장하지 않습니다.',
    retention: '분석 결과는 세션 종료 후 90일 보관합니다.',
    refusal: '동의하지 않을 수 있습니다.',
    impact: '집중 세션을 시작할 수 없습니다.',
    version: 'v1.0',
  },
]

const cameraDetail: Detail = {
  id: 'camera',
  title: '카메라 분석',
  purpose: '화면만으로 알기 어려운 자리 비움 상태를 보조적으로 확인합니다.',
  items: '기기 안에서 분석한 얼굴·시선 상태 신호. 영상과 사진은 저장하거나 전송하지 않습니다.',
  retention: '상태 기록은 세션 종료 후 1년 보관합니다.',
  refusal: '선택 항목이므로 동의하지 않아도 됩니다.',
  impact: '화면 분석만으로 이용할 수 있습니다.',
  version: 'v1.0',
}

const optionalChoices = [
  { id: 'camera', title: '카메라 분석' },
  { id: 'notifications', title: '집중 알림' },
] as const

const permissionDetail: Detail = {
  id: 'screen-permission',
  title: '화면 기록 권한',
  purpose: '화면이 학습 목표와 관련 있는지 확인하는 데 사용합니다.',
  items: '앱 이름, 탭 제목·도메인, 필요할 때 화면에서 읽은 글자',
  retention: '화면 원문과 캡처는 저장하지 않습니다.',
  refusal: 'macOS 시스템 설정에서 언제든 변경할 수 있습니다.',
  impact: '권한이 없으면 화면 분석을 시작할 수 없습니다.',
}

export function PermissionsStep() {
  const [view, setView] = useState<'permission' | 'consents'>('permission')
  const [accepted, setAccepted] = useState<Set<string>>(new Set())
  const [optionalEnabled, setOptionalEnabled] = useState<Set<string>>(new Set())
  const [activeDetail, setActiveDetail] = useState<Detail | null>(null)
  const dialogRef = useRef<HTMLDialogElement>(null)
  const allRequiredAccepted = requiredConsents.every(({ id }) => accepted.has(id))

  function toggle(setter: (value: Set<string>) => void, current: Set<string>, id: string) {
    const next = new Set(current)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    setter(next)
  }

  function showDetail(detail: Detail) {
    setActiveDetail(detail)
    dialogRef.current?.showModal()
  }

  function toggleAllRequired() {
    setAccepted(allRequiredAccepted
      ? new Set()
      : new Set(requiredConsents.map(({ id }) => id)))
  }

  const showingConsents = view === 'consents'

  return (
    <OnboardingShell
      currentStep={2}
      prompt={showingConsents ? '필수 동의 항목을 확인해 주세요.' : '화면 분석에 필요한 권한을 확인해 주세요.'}
      backHref={showingConsents ? undefined : '/onboarding/extension/'}
      onBack={showingConsents ? () => setView('permission') : undefined}
      footer={showingConsents ? (
        <button className="btn btn-primary next" type="button" disabled>
          다음
        </button>
      ) : (
        <button className="btn btn-primary next" type="button" onClick={() => setView('consents')}>
          동의 항목 보기
        </button>
      )}
    >
      {!showingConsents ? (
        <section className="permission-page" aria-labelledby="screen-permission-title">
          <div className="substep-heading">
            <p className="substep-label">권한 확인</p>
            <span className="substep-count">1 / 2</span>
          </div>
          <div className="permission-choice" role="group" aria-labelledby="screen-permission-title">
            <span className="consent-check permission-mark" aria-hidden="true">!</span>
            <span className="consent-copy">
              <strong id="screen-permission-title">화면 기록</strong>
              <small>화면이 학습 목표와 관련 있는지 확인할 때 사용합니다.</small>
            </span>
            <span className="tag required">필수</span>
          </div>
          <div className="permission-actions">
            <span className="permission-status" role="status">
              <span className="permission-status-dot" aria-hidden="true" />
              허용 여부 확인 전
            </span>
            <button className="detail-link" type="button" onClick={() => showDetail(permissionDetail)}>
              설정 방법
            </button>
          </div>
        </section>
      ) : (
        <section className="consent-page" aria-labelledby="required-consents-title">
          <div className="substep-heading">
            <div className="substep-title-group">
              <p className="substep-label" id="required-consents-title">필수 동의</p>
              <span className="substep-count">2 / 2</span>
            </div>
          </div>
          <ul className="consent-list">
            <li>
              <button
                className="consent-choice consent-choice-all"
                type="button"
                role="checkbox"
                aria-checked={allRequiredAccepted}
                onClick={toggleAllRequired}
              >
                <span className="consent-check" aria-hidden="true">✓</span>
                <span className="consent-copy"><strong>모두 동의하기</strong></span>
              </button>
            </li>
            {requiredConsents.map((detail) => {
              const checked = accepted.has(detail.id)
              return (
                <li className="consent-item" key={detail.id}>
                  <button
                    className="consent-choice"
                    type="button"
                    role="checkbox"
                    aria-checked={checked}
                    onClick={() => toggle(setAccepted, accepted, detail.id)}
                  >
                    <span className="consent-check" aria-hidden="true">✓</span>
                    <span className="consent-copy">
                      <strong>{detail.title}</strong>
                    </span>
                    <span className="tag required">필수</span>
                  </button>
                  <button className="detail-link" type="button" onClick={() => showDetail(detail)}>
                    자세히
                  </button>
                </li>
              )
            })}
          </ul>
          <div className="optional-consents">
            <div className="consent-section-heading">
              <h2>선택 항목</h2>
              <span className="optional-note">동의하지 않아도 계속할 수 있습니다.</span>
            </div>
            <ul className="consent-list optional-list">
              {optionalChoices.map(({ id, title }) => {
                const checked = optionalEnabled.has(id)
                return (
                  <li key={id}>
                    <button
                      className="consent-choice"
                      type="button"
                      role="checkbox"
                      aria-checked={checked}
                      onClick={() => toggle(setOptionalEnabled, optionalEnabled, id)}
                    >
                      <span className="consent-check" aria-hidden="true">✓</span>
                      <span className="consent-copy">
                        <strong>{title}</strong>
                      </span>
                      <span className="tag">선택</span>
                    </button>
                    {id === 'camera' ? (
                      <button className="detail-link" type="button" onClick={() => showDetail(cameraDetail)}>
                        자세히
                      </button>
                    ) : null}
                  </li>
                )
              })}
            </ul>
          </div>
        </section>
      )}

      <dialog
        className="consent-dialog"
        ref={dialogRef}
        aria-labelledby="consent-dialog-title"
        onClose={() => setActiveDetail(null)}
      >
        {activeDetail ? (
          <>
            <h2 id="consent-dialog-title">{activeDetail.title}</h2>
            {activeDetail.id === 'screen-permission' ? (
              <p className="permission-dialog-copy">macOS 시스템 설정의 개인정보 보호 및 보안에서 FocusOn의 화면 기록을 허용해 주세요.</p>
            ) : null}
            <dl>
              <dt>목적</dt><dd>{activeDetail.purpose}</dd>
              <dt>처리 항목</dt><dd>{activeDetail.items}</dd>
              <dt>보유 기간</dt><dd>{activeDetail.retention}</dd>
              <dt>거부 권리</dt><dd>{activeDetail.refusal}</dd>
              <dt>거부 시 영향</dt><dd>{activeDetail.impact}</dd>
              {activeDetail.version ? <><dt>버전</dt><dd>{activeDetail.version}</dd></> : null}
            </dl>
            <form method="dialog">
              <button className="btn btn-primary" type="submit">확인</button>
            </form>
          </>
        ) : null}
      </dialog>
    </OnboardingShell>
  )
}
