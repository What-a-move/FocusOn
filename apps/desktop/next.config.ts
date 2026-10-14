import type { NextConfig } from 'next'
import { resolve } from 'node:path'

const nextConfig: NextConfig = {
  output: 'export',
  turbopack: {},
  agentRules: false,
  trailingSlash: true,
  images: {
    unoptimized: true,
  },
  webpack(config, { dev }) {
    if (!dev) {
      // 개발 전용 Fixture가 운영 번들에 포함되지 않도록 빌드 시 대체한다.
      config.resolve.alias = {
        ...config.resolve.alias,
        [resolve(process.cwd(), 'app/learning-summary-demo/Demo.tsx')]: resolve(
          process.cwd(),
          'app/learning-summary-demo/UnavailableDemo.tsx',
        ),
      }
    }
    return config
  },
}

export default nextConfig
