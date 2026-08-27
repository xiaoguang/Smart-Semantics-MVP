import type { ReactNode } from 'react';

export default function TechnicalDisclosure({ children, summary = '技术定义' }: { children: ReactNode; summary?: string }) {
  return <details className="technical-disclosure">
    <summary>{summary}</summary>
    <div>{children}</div>
  </details>;
}
