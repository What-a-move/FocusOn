import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: 'FocusOn Extension',
  description: 'FocusOn Chrome 학습 흐름 분석 Extension',
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  )
}
