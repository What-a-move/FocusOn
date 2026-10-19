import { createHash, randomBytes, timingSafeEqual } from 'node:crypto'
import { createServer } from 'node:http'
import type { IncomingMessage, Server, ServerResponse } from 'node:http'

const CALLBACK_PATH = '/'
const GOOGLE_AUTH_URL = 'https://accounts.google.com/o/oauth2/v2/auth'
const DEFAULT_TIMEOUT_MS = 5 * 60 * 1000

export type GoogleAuthorizationCode = {
  authorizationCode: string
  codeVerifier: string
  state: string
  redirectUri: string
}

export type GoogleOAuthOutcome =
  | { type: 'code'; data: GoogleAuthorizationCode }
  | { type: 'cancelled' }
  | { type: 'failed' }

export type GoogleOAuthOptions = {
  clientId: string
  openBrowser: (url: string) => Promise<void>
  onOutcome: (outcome: GoogleOAuthOutcome) => void
  timeoutMs?: number
}

type ActiveAttempt = {
  server: Server
  timer: NodeJS.Timeout
  state: string
  codeVerifier: string
  redirectUri: string
}

function equalsSecret(received: string, expected: string): boolean {
  const receivedBytes = Buffer.from(received)
  const expectedBytes = Buffer.from(expected)
  return receivedBytes.length === expectedBytes.length && timingSafeEqual(receivedBytes, expectedBytes)
}

export function createGoogleAuthorizationUrl(clientId: string, redirectUri: string, state: string, codeVerifier: string): string {
  const challenge = createHash('sha256').update(codeVerifier).digest('base64url')
  const url = new URL(GOOGLE_AUTH_URL)
  url.searchParams.set('client_id', clientId)
  url.searchParams.set('redirect_uri', redirectUri)
  url.searchParams.set('response_type', 'code')
  url.searchParams.set('scope', 'openid email profile')
  url.searchParams.set('state', state)
  url.searchParams.set('code_challenge', challenge)
  url.searchParams.set('code_challenge_method', 'S256')
  return url.toString()
}

function reply(response: ServerResponse, status: number, message: string): void {
  response.writeHead(status, {
    'Content-Type': 'text/plain; charset=utf-8',
    'Cache-Control': 'no-store',
    'Referrer-Policy': 'no-referrer',
    'X-Content-Type-Options': 'nosniff',
  })
  response.end(message)
}

export class GoogleOAuthFlow {
  private active: ActiveAttempt | null = null
  private starting = false

  constructor(private readonly options: GoogleOAuthOptions) {}

  async start(): Promise<boolean> {
    if (this.active || this.starting) return false
    this.starting = true

    const state = randomBytes(32).toString('base64url')
    const codeVerifier = randomBytes(64).toString('base64url')
    const server = createServer((request, response) => this.handleCallback(request, response))

    try {
      // [주의] 임시 포트는 OS가 고르게 하고 외부 네트워크에 열리지 않도록 127.0.0.1에만 바인딩한다.
      await new Promise<void>((resolve, reject) => {
        server.once('error', reject)
        server.listen(0, '127.0.0.1', () => {
          server.removeListener('error', reject)
          resolve()
        })
      })
      server.on('error', () => this.finish({ type: 'failed' }))

      const address = server.address()
      if (!address || typeof address === 'string') throw new Error('OAuth callback address unavailable')
      // [주의] Google 데스크톱 앱의 loopback redirect URI는 임시 포트의 기본 주소를 사용한다.
      const redirectUri = `http://127.0.0.1:${address.port}`
      const timer = setTimeout(() => this.finish({ type: 'cancelled' }), this.options.timeoutMs ?? DEFAULT_TIMEOUT_MS)
      timer.unref()
      this.active = { server, timer, state, codeVerifier, redirectUri }

      // [주의] Google 주소는 코드에서 고정한다. Renderer가 전달한 URL은 기본 브라우저에 열지 않는다.
      await this.options.openBrowser(createGoogleAuthorizationUrl(this.options.clientId, redirectUri, state, codeVerifier))
      return true
    } catch {
      if (this.active) this.dispose()
      else server.close()
      return false
    } finally {
      this.starting = false
    }
  }

  dispose(): void {
    const attempt = this.active
    this.active = null
    if (attempt) {
      clearTimeout(attempt.timer)
      // [주의] callback 응답을 쓰는 중에도 호출되므로 연결을 강제로 끊지 않는다.
      attempt.server.close()
    }
  }

  private finish(outcome: GoogleOAuthOutcome): void {
    if (!this.active) return
    this.dispose()
    this.options.onOutcome(outcome)
  }

  private handleCallback(request: IncomingMessage, response: ServerResponse): void {
    const attempt = this.active
    if (!attempt) return reply(response, 410, '로그인 요청이 끝났어. 앱에서 다시 시도해줘.')

    // [주의] 다른 로컬 프로그램의 요청이거나 잘못된 경로라면 code/state를 읽지 않는다.
    const expectedHost = new URL(attempt.redirectUri).host
    if (request.method !== 'GET' || request.headers.host !== expectedHost) {
      return reply(response, 400, '잘못된 로그인 요청이야.')
    }

    let callbackUrl: URL
    try {
      callbackUrl = new URL(request.url ?? '/', attempt.redirectUri)
    } catch {
      return reply(response, 400, '잘못된 로그인 요청이야.')
    }
    if (callbackUrl.origin !== new URL(attempt.redirectUri).origin || callbackUrl.pathname !== CALLBACK_PATH) {
      return reply(response, 404, '로그인 주소를 찾을 수 없어.')
    }

    const returnedStates = callbackUrl.searchParams.getAll('state')
    if (returnedStates.length !== 1 || !equalsSecret(returnedStates[0], attempt.state)) {
      return reply(response, 400, '로그인 확인값이 일치하지 않아.')
    }

    if (callbackUrl.searchParams.has('error')) {
      if (callbackUrl.searchParams.getAll('error').length !== 1 || callbackUrl.searchParams.has('code')) {
        return reply(response, 400, '잘못된 로그인 결과야.')
      }
      const cancelled = callbackUrl.searchParams.get('error') === 'access_denied'
      reply(response, 200, cancelled ? '로그인이 취소됐어. 앱으로 돌아가줘.' : '로그인을 완료하지 못했어. 앱으로 돌아가줘.')
      this.finish({ type: cancelled ? 'cancelled' : 'failed' })
      return
    }

    const authorizationCodes = callbackUrl.searchParams.getAll('code')
    if (authorizationCodes.length !== 1 || !authorizationCodes[0]) return reply(response, 400, '로그인 코드가 없어.')

    // [주의] code는 응답·로그에 쓰지 않고 Main 내부 콜백에 한 번만 전달한다.
    reply(response, 200, '인증 결과를 앱으로 보냈어. 앱으로 돌아가줘.')
    this.finish({
      type: 'code',
      data: { authorizationCode: authorizationCodes[0], codeVerifier: attempt.codeVerifier, state: attempt.state, redirectUri: attempt.redirectUri },
    })
  }
}
