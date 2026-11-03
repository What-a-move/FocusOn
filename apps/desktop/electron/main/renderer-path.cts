import path from 'node:path'

export const APP_HOST = 'focuson'
export const DEV_RENDERER_URL = 'http://127.0.0.1:3000/'
export const PROD_RENDERER_URL = `app://${APP_HOST}/`

export function resolveRendererFile(outDirectory: string, requestUrl: string): string | null {
  let url: URL
  let pathname: string

  try {
    url = new URL(requestUrl)
    pathname = decodeURIComponent(url.pathname)
  } catch {
    return null
  }

  if (url.protocol !== 'app:' || url.hostname !== APP_HOST || url.port || url.username || url.password) {
    return null
  }

  if (pathname.includes('\0') || pathname.includes('\\')) {
    return null
  }

  const filePath = path.resolve(outDirectory, `.${pathname}`)
  const relativePath = path.relative(outDirectory, filePath)

  if (relativePath.startsWith('..') || path.isAbsolute(relativePath)) {
    return null
  }

  if (pathname.endsWith('/') || !path.extname(pathname)) {
    return path.join(filePath, 'index.html')
  }

  return filePath
}

export function isAllowedNavigation(targetUrl: string, rendererUrl: string): boolean {
  try {
    const target = new URL(targetUrl)
    const renderer = new URL(rendererUrl)
    // [주의] app:// 같은 Custom Scheme의 origin은 런타임에 'null'일 수 있어 구성 요소를 직접 비교한다.
    return target.protocol === renderer.protocol
      && target.hostname === renderer.hostname
      && target.port === renderer.port
      && target.username === ''
      && target.password === ''
  } catch {
    return false
  }
}
