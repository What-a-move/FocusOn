const { execFileSync } = require('node:child_process')
const { mkdirSync } = require('node:fs')
const path = require('node:path')

// 화면 관측 도우미(Swift)를 빌드한다. 지원 macOS는 14 이상이다(결정 024).
// 빌드하는 Mac의 CPU 구조로 만든다. 배포 Build는 arm64·x64를 각각 만든다(ELECTRON_ARCHITECTURE.md).
if (process.platform !== 'darwin') process.exit(0)
const out = path.join(__dirname, '../build/native/focuson-observer')
mkdirSync(path.dirname(out), { recursive: true })
const arch = process.arch === 'arm64' ? 'arm64' : 'x86_64'
execFileSync('xcrun', ['swiftc', '-O', '-target', `${arch}-apple-macos14.0`, path.join(__dirname, '../native/observer/main.swift'), '-o', out], { stdio: 'inherit' })
