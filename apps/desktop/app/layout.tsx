import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: 'FocusOn Desktop',
  description: 'FocusOn macOS 학습 세션 앱',
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
