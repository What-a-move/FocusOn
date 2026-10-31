// [유지보수] 오류 본문은 "왜 안 됐는지 + 무엇을 하면 되는지" 한 문장으로 여기서만 만든다.
// 무엇을 못 했는지(제목)는 각 화면이 정한다. 기다려도 풀리지 않는 오류에는 "잠시 후 다시"를 쓰지 않는다.
export function errorText(error: { code: string; retryable?: boolean }): string {
  switch (error.code) {
    case 'LOCAL_NETWORK_UNAVAILABLE': return '인터넷에 연결되어 있지 않아요. 연결을 확인한 뒤 다시 시도해 주세요.'
    case 'SERVICE_UNAVAILABLE':
    case 'INTERNAL_ERROR': return 'FocusOn 서버가 지금 응답하지 않아요. 잠시 후 다시 시도해 주세요.'
    case 'REQUEST_TIMEOUT': return 'FocusOn 서버의 응답이 너무 늦어요. 잠시 후 다시 시도해 주세요.'
    case 'INVALID_RESPONSE': return '서버 응답을 읽지 못했어요. 앱을 다시 실행한 뒤 시도해 주세요.'
    case 'TOO_MANY_REQUESTS': return '짧은 시간에 요청이 많았어요. 1분쯤 뒤에 다시 시도해 주세요.'
    case 'VALIDATION_ERROR': return '입력한 값을 확인한 뒤 다시 시도해 주세요.'
    case 'AUTH_RELOGIN_REQUIRED':
    case 'AUTH_TOKEN_REUSE_DETECTED':
    case 'AUTH_OAUTH_REAUTH_REQUIRED': return '로그인 정보가 만료됐어요. Google로 다시 로그인해 주세요.'
    case 'AUTH_DEVICE_LIMIT_EXCEEDED': return '로그인 가능한 Desktop 3대를 모두 사용 중이에요. 기존 기기를 해제한 뒤 다시 시도해 주세요.'
    case 'LOCAL_APP_CONFIG_MISSING': return '이 앱에 로그인 설정이 빠져 있어 다시 시도해도 해결되지 않아요. 앱을 다시 설치해 주세요.'
    case 'LOCAL_BROWSER_OPEN_FAILED': return '브라우저를 열지 못했어요. 기본 브라우저가 설정돼 있는지 확인한 뒤 다시 시도해 주세요.'
    case 'LOCAL_GOOGLE_LOGIN_FAILED': return 'Google 로그인 창에서 로그인이 끝나지 않았어요. 다시 로그인해 주세요.'
    case 'LOCAL_KEYCHAIN_UNAVAILABLE': return 'Mac 키체인에 로그인 정보를 저장하거나 읽지 못했어요. 키체인 접근을 허용한 뒤 다시 시도해 주세요.'
    default: return error.retryable === false
      ? '알 수 없는 문제가 생겼어요. 앱을 다시 실행한 뒤 시도해 주세요.'
      : '알 수 없는 문제가 생겼어요. 잠시 후 다시 시도해 주세요.'
  }
}
