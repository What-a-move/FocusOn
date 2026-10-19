import { execFileSync } from 'node:child_process';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
if (process.platform !== 'darwin') throw new Error('macOS is required to build Apple Vision capture.');
const output = path.join(root, 'dist-electron/native/focuson-screen-capture');
await mkdir(path.dirname(output), { recursive: true });
execFileSync('xcrun', ['clang', '-arch', process.arch === 'arm64' ? 'arm64' : 'x86_64', '-fobjc-arc', '-fblocks', '-O2', '-mmacosx-version-min=14.0',
  path.join(root, 'electron/native/ScreenCapture.m'), '-o', output,
  '-framework', 'AppKit', '-framework', 'ApplicationServices', '-framework', 'ScreenCaptureKit', '-framework', 'Vision'], { stdio: 'inherit' });
