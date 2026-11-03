import assert from 'node:assert/strict'
import test from 'node:test'
import { cleanPageText, hostOf, isExcludedHost, isPrivateHost, isWebPage } from '../extension/shared/page-rules.ts'

test('웹 페이지만 분석하고 host만 남긴다', () => {
  assert.equal(isWebPage('https://tanstack.com/query/latest?x=1#a'), true)
  assert.equal(isWebPage('chrome://newtab/'), false)
  assert.equal(isWebPage('chrome-extension://abc/index.html'), false)
  assert.equal(hostOf('https://Docs.Example.com./path?q=secret'), 'docs.example.com')
  assert.equal(hostOf('not a url'), null)
})

test('제외 도메인은 같은 host와 하위 도메인만 맞고, 비슷한 이름은 맞지 않는다', () => {
  assert.equal(isExcludedHost('youtube.com', ['youtube.com']), true)
  assert.equal(isExcludedHost('m.youtube.com', ['youtube.com']), true)
  assert.equal(isExcludedHost('notyoutube.com', ['youtube.com']), false)
  assert.equal(isPrivateHost('mail.google.com'), true)
  assert.equal(isPrivateHost('docs.google.com'), false)
})

test('본문은 이메일·전화·주소·긴 토큰을 지우고 2,000자로 자른다', () => {
  const { text, sensitive } = cleanPageText(`React Query   캐시\n문의 me@example.com 010-1234-5678 https://x.io/a token abcd1234efgh5678ijkl9012mnop ${'가'.repeat(3000)}`)
  assert.equal(sensitive, false)
  assert.equal(text.includes('@'), false)
  assert.equal(text.includes('010'), false)
  assert.equal(text.includes('https'), false)
  assert.equal(text.includes('abcd1234'), false)
  assert.ok(text.startsWith('React Query 캐시 문의'))
  assert.equal(text.length, 2000)
})

test('민감한 단어나 카드 번호가 보이면 본문을 보내지 않는다', () => {
  assert.deepEqual(cleanPageText('새 비밀번호를 입력하세요'), { text: '', sensitive: true })
  assert.deepEqual(cleanPageText('Enter the verification code'), { text: '', sensitive: true })
  assert.deepEqual(cleanPageText('결제 카드 4111 1111 1111 1111'), { text: '', sensitive: true })
})
