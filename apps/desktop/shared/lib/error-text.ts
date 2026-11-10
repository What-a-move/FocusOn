// [유지보수] 오류 본문은 "왜 안 됐는지 + 무엇을 하면 되는지"를 짧게 여기서만 만든다.
// 무엇을 못 했는지(제목)는 각 화면이 정한다. 기다려도 풀리지 않는 오류에는 "잠시 후 다시"를 쓰지 않는다.
export function errorText(error: { code: string }): string {
  switch (error.code) {
    case 'LOCAL_NETWORK_UNAVAILABLE': return '인터넷 연결을 확인해 주세요.'
    // 서버가 답한 장애. 앱 안에서 난 실패는 LOCAL_APP_ERROR로 따로 온다.
    case 'SERVICE_UNAVAILABLE':
    case 'INTERNAL_ERROR': return '서버에 잠시 문제가 있어요. 잠시 후 다시 시도해 주세요.'
    case 'LOCAL_APP_ERROR': return '앱에 문제가 생겼어요. 앱을 다시 열어 주세요.'
    case 'REQUEST_TIMEOUT': return '서버 응답이 늦어요. 잠시 후 다시 시도해 주세요.'
    // 서버에 아직 없는 API(404). 기다려도 풀리지 않으니 "잠시 후"를 쓰지 않는다.
    case 'NOT_FOUND': return '아직 준비 중인 기능이에요.'
    case 'INVALID_RESPONSE': return '서버 응답을 읽지 못했어요. 앱을 다시 열어 주세요.'
    case 'TOO_MANY_REQUESTS': return '요청이 많았어요. 1분 뒤에 다시 시도해 주세요.'
    case 'VALIDATION_ERROR': return '입력한 내용을 확인해 주세요.'
    case 'AUTH_RELOGIN_REQUIRED':
    case 'AUTH_TOKEN_REUSE_DETECTED':
    case 'AUTH_OAUTH_REAUTH_REQUIRED': return '로그인이 만료됐어요. 다시 로그인해 주세요.'
    case 'AUTH_DEVICE_LIMIT_EXCEEDED': return 'Desktop 3대에 이미 로그인돼 있어요. 다른 기기를 해제해 주세요.'
    case 'LOCAL_APP_CONFIG_MISSING': return '앱 설정이 빠져 있어요. 앱을 다시 설치해 주세요.'
    case 'LOCAL_BROWSER_OPEN_FAILED': return '브라우저를 열지 못했어요. 기본 브라우저를 확인해 주세요.'
    case 'LOCAL_GOOGLE_LOGIN_FAILED': return 'Google 로그인이 끝나지 않았어요. 다시 로그인해 주세요.'
    case 'LOCAL_KEYCHAIN_UNAVAILABLE': return '키체인을 쓰지 못했어요. 키체인 접근을 허용해 주세요.'
    default: return '문제가 생겼어요. 계속되면 앱을 다시 열어 주세요.'
  }
}
