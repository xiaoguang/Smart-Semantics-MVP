import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import {
  createDemoRecoveryDomainAdapters,
  resumeDemoRecovery,
  type DemoRecoveryResult,
} from './features/demo-session/demo-recovery.ts';

function RecoveryFailure({ result }: { result: Extract<DemoRecoveryResult, { status: 'BLOCKED' | 'FAILED' }> }) {
  const detail = result.status === 'BLOCKED'
    ? '请关闭正在使用本 Demo 的其他页面后继续恢复。'
    : '本次演示尚未恢复完成，继续恢复会从中断处安全重试。';
  return <main className="demo-recovery-failure" role="alert">
    <section>
      <h1>恢复演示未完成</h1>
      <p>{detail}</p>
      <p className="demo-recovery-failure-detail">已保留恢复进度；继续恢复会重新检查本次演示所需的资料。</p>
      <button type="button" onClick={() => window.location.reload()}>继续恢复</button>
    </section>
  </main>;
}

async function mount() {
  const root = createRoot(document.getElementById('root')!);
  const adapters = createDemoRecoveryDomainAdapters(localStorage);
  const result = await resumeDemoRecovery({
    localStorage,
    sessionStorage,
    indexedDB: typeof indexedDB === 'undefined' ? undefined : indexedDB,
    ...adapters,
  });
  if (result.status === 'BLOCKED' || result.status === 'FAILED') {
    root.render(<RecoveryFailure result={result} />);
    return;
  }
  root.render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
}

void mount();
