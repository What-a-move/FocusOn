import { defineConfig, globalIgnores } from 'eslint/config'
import nextVitals from 'eslint-config-next/core-web-vitals'

// 가벼운 FSD(apps/docs/FOLDER_STRUCTURE.md): app → features → shared 방향으로만 가져다 쓴다.
const noOtherFeature = { regex: '^\\.\\./(?!\\.\\.)', message: '다른 기능 폴더를 가져오지 마세요. 같이 쓰는 코드는 shared/로 옮겨요.' }
const noFeatures = { regex: '(^|/)features/', message: 'shared·extension은 features를 가져오지 않아요.' }

export default defineConfig([
  ...nextVitals,
  { files: ['features/**'], rules: { 'no-restricted-imports': ['error', { patterns: [noOtherFeature] }] } },
  { files: ['shared/**', 'extension/**'], rules: { 'no-restricted-imports': ['error', { patterns: [noFeatures] }] } },
  globalIgnores(['.next/**', 'out/**', 'node_modules/**']),
])
