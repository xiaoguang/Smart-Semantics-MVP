import { mkdirSync, writeFileSync } from 'node:fs';
import { expect, test, type Page } from '@playwright/test';
import {
  closeFormalCatalogBrowser,
  enterMysqlDocument,
  login,
  openStandardization,
  readPersistedTechnicalIdentity,
  resetBrowserState,
  selectGuanyijia,
} from './cp8-helpers.ts';

async function openHarness(page: Page) {
  await page.clock.setFixedTime(new Date('2026-08-18T12:00:00.000Z'));
  await page.goto('/cp8/');
  await expect(page.getByTestId('timeline-count')).toHaveText('40');
  await expect(page.getByTestId('issue-window-count')).toHaveText('20');
}

async function replaceContentAddressedBody(page: Page, ref: string, replacement?: string) {
  return page.evaluate(async ({ contentRef, content }) => new Promise<string>((resolve, reject) => {
    const open = indexedDB.open('linguan-standardization-evidence-v1', 1);
    open.onerror = () => reject(open.error ?? new Error('无法打开标准化正文存储'));
    open.onsuccess = () => {
      const database = open.result;
      const transaction = database.transaction('content', 'readwrite');
      const store = transaction.objectStore('content');
      const read = store.get(contentRef);
      let original = '';
      read.onerror = () => transaction.abort();
      read.onsuccess = () => {
        original = typeof read.result === 'string' ? read.result : '';
        store.put(content ?? '{"tampered":true}', contentRef);
      };
      transaction.oncomplete = () => {
        database.close();
        resolve(original);
      };
      transaction.onerror = () => reject(transaction.error ?? new Error('无法替换标准化正文'));
      transaction.onabort = () => reject(transaction.error ?? new Error('标准化正文替换中止'));
    };
  }), { contentRef: ref, content: replacement });
}

test('10k issues／100k Evidence 首屏与keyset window满足硬容量预算', async ({ page }, testInfo) => {
  const pageErrors: Error[] = [];
  const consoleErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error));
  page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text()); });
  await page.addInitScript(() => {
    const durations: number[] = [];
    Object.defineProperty(window, '__cp8LongTasks', { value: durations });
    new PerformanceObserver((entries) => {
      durations.push(...entries.getEntries().map(({ duration }) => duration));
    }).observe({ type: 'longtask', buffered: true });
  });
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Performance.enable');
  const shellStartedAt = performance.now();
  await openHarness(page);
  const interactiveMs = performance.now() - shellStartedAt;
  expect(interactiveMs).toBeLessThan(2_000);

  const heapBefore = (await cdp.send('Performance.getMetrics')).metrics
    .find(({ name }) => name === 'JSHeapUsedSize')?.value ?? 0;

  await expect(page.getByTestId('evidence-index-count')).toHaveText('100000');
  await expect(page.getByTestId('materialized-count')).toHaveText('0');
  await expect(page.getByTestId('body-read-count')).toHaveText('0');
  await expect(page.getByTestId('query-count')).toHaveText('2');
  expect(Number(await page.getByTestId('payload-bytes').textContent())).toBeLessThan(256 * 1024);
  await expect(page.getByRole('list', { name: '容量时间线' }).getByRole('listitem')).toHaveCount(40);
  await expect(page.getByRole('list', { name: '问题窗口' }).getByRole('listitem')).toHaveCount(20);
  expect(await page.locator('body *').count()).toBeLessThanOrEqual(300);

  const pageDurations: number[] = [];
  for (let pageNumber = 0; pageNumber < 4; pageNumber += 1) {
    const startedAt = performance.now();
    await page.getByRole('button', { name: '读取下一页问题' }).click();
    await expect(page.getByTestId('query-count')).toHaveText(String(pageNumber + 3));
    pageDurations.push(performance.now() - startedAt);
  }
  expect(pageDurations.every((duration) => duration < 500)).toBe(true);
  await expect(page.getByTestId('issue-window-count')).toHaveText('80');
  await expect(page.getByRole('list', { name: '问题窗口' }).getByRole('listitem')).toHaveCount(50);
  const domCount = await page.locator('body *').count();
  expect(domCount).toBeLessThanOrEqual(300);
  const windowCommitMs = Number(await page.getByTestId('window-commit-ms').textContent());
  expect(windowCommitMs).toBeGreaterThan(0);
  expect(windowCommitMs).toBeLessThan(100);

  const cacheStartedAt = performance.now();
  await page.getByRole('button', { name: '读取离线缓存' }).click();
  await expect(page.getByRole('article', { name: '已校验正文' })).toContainText('CACHE:缓存中的已校验审阅正文');
  const cacheBodyMs = performance.now() - cacheStartedAt;
  expect(cacheBodyMs).toBeLessThan(300);
  await expect(page.getByTestId('body-read-count')).toHaveText('1');
  const heapAfter = (await cdp.send('Performance.getMetrics')).metrics
    .find(({ name }) => name === 'JSHeapUsedSize')?.value ?? 0;
  const longTasks = await page.evaluate(() => (
    (window as Window & { __cp8LongTasks?: number[] }).__cp8LongTasks ?? []
  ));
  const metrics = {
    browser: await page.evaluate(() => navigator.userAgent),
    interactiveMs,
    cursorPageMs: pageDurations,
    cacheBodyMs,
    windowCommitMs,
    heapBefore,
    heapAfter,
    heapDelta: heapAfter - heapBefore,
    longTasks,
    bodyReads: 1,
    payloadBytes: Number(await page.getByTestId('payload-bytes').textContent()),
    domCount,
  };
  const body = JSON.stringify(metrics, null, 2);
  mkdirSync('artifacts/cp8-metrics', { recursive: true });
  writeFileSync('artifacts/cp8-metrics/capacity.json', body);
  await testInfo.attach('cp8-capacity-metrics', { body, contentType: 'application/json' });
  expect(pageErrors).toEqual([]);
  expect(consoleErrors).toEqual([]);
});

test('offline与cursor分别可恢复且保留草稿、选择和滚动锚点', async ({ page }) => {
  const pageErrors: Error[] = [];
  const consoleErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error));
  page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text()); });
  await openHarness(page);
  const draft = page.getByLabel('助手草稿');
  await draft.fill('保留此草稿与选择');

  await page.getByRole('button', { name: '注入离线未缓存' }).click();
  let alert = page.getByRole('alert');
  await expect(alert).toHaveAttribute('data-error-code', 'OFFLINE_NOT_CACHED');
  await expect(alert.getByRole('button')).toHaveCount(1);
  await expect(alert.getByRole('button', { name: '重新读取' })).toBeVisible();
  await alert.getByRole('button', { name: '重新读取' }).click();
  await expect(page.getByRole('article', { name: '已校验正文' })).toContainText('CACHE:');

  await page.getByRole('button', { name: '注入游标过期' }).click();
  alert = page.getByRole('alert');
  await expect(alert).toHaveAttribute('data-error-code', 'CURSOR_EXPIRED');
  await alert.getByRole('button', { name: '重新读取列表' }).click();
  await expect(page.getByText('列表已更新', { exact: true })).toBeVisible();
  await expect(page.locator('[data-anchor="issue:000000005"]')).toBeVisible();
  await expect(draft).toHaveValue('保留此草稿与选择');
  expect(pageErrors).toEqual([]);
  expect(consoleErrors).toEqual([]);
});

test('checksum mismatch独立fail-closed，不显示损坏正文且不提供恢复按钮', async ({ page }) => {
  const pageErrors: Error[] = [];
  const consoleErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error));
  page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text()); });
  await openHarness(page);
  await page.getByRole('button', { name: '注入正文损坏' }).click();
  const alert = page.getByRole('alert');
  await expect(alert).toHaveAttribute('data-error-code', 'CHECKSUM_MISMATCH');
  await expect(alert).toContainText(/expected [a-f0-9]{64} \/ actual [a-f0-9]{64}/u);
  await expect(alert.getByRole('button')).toHaveCount(0);
  await expect(page.getByRole('article', { name: '已校验正文' })).toHaveCount(0);
  expect(pageErrors).toEqual([]);
  expect(consoleErrors).toEqual([]);
});

test('真实Workbench正文校验失败会阻止文档修改与流程推进，恢复原文后才解除门禁', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-08-18T12:00:00.000Z'));
  await resetBrowserState(page);
  await login(page);
  await closeFormalCatalogBrowser(page);
  await selectGuanyijia(page);
  await openStandardization(page, 'DESKTOP');
  await enterMysqlDocument(page);
  const { run, documents } = await readPersistedTechnicalIdentity(page);
  const mysql = run.sources.find(({ sourceId }) => sourceId === 'guanyijia_mysql');
  const document = documents.find(({ documentId }) => documentId === mysql?.documentId);
  expect(document?.blocksRef).toMatch(/^sha256:[a-f0-9]{64}$/u);
  const original = await replaceContentAddressedBody(page, document!.blocksRef!);
  expect(original.length).toBeGreaterThan(0);

  await page.reload();
  await closeFormalCatalogBrowser(page);
  await openStandardization(page, 'DESKTOP');
  const workbench = page.locator('.guanyijia-workbench-shell');
  await expect(workbench).toHaveAttribute('data-review-mutations-blocked', 'true');
  const checksum = page.locator('[data-error-code="CHECKSUM_MISMATCH"]');
  await expect(checksum).toBeVisible();
  await expect(checksum).toContainText('正文完整性校验失败');
  await expect(checksum).toContainText('修改、冲突处理和定版已阻止');
  await expect(page.getByRole('button', { name: '完成本份审阅' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '修改本项' })).toHaveCount(0);
  await expect(workbench.locator('[data-workflow-primary="true"]')).toHaveCount(0);

  await replaceContentAddressedBody(page, document!.blocksRef!, original);
  await page.reload();
  await closeFormalCatalogBrowser(page);
  await openStandardization(page, 'DESKTOP');
  await expect(workbench).toHaveAttribute('data-review-mutations-blocked', 'false');
  await expect(page.getByRole('button', { name: '完成本份审阅' })).toBeEnabled();
});

test('quota失败不推进真实revision并保留只读错误事实', async ({ page }) => {
  const pageErrors: Error[] = [];
  const consoleErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error));
  page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text()); });
  await openHarness(page);
  await page.getByRole('button', { name: '注入存储空间不足' }).click();
  const alert = page.getByRole('alert');
  await expect(alert).toHaveAttribute('data-error-code', 'QUOTA_EXCEEDED');
  await expect(alert).toContainText('usage 9000000 / quota 10000000');
  await expect(page.getByTestId('metadata-revision')).toHaveText('0');
  expect(pageErrors).toEqual([]);
  expect(consoleErrors).toEqual([]);
});

test('CAS冲突独立reload真实胜者，清理预览但保留草稿与锚点', async ({ page }) => {
  const pageErrors: Error[] = [];
  const consoleErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error));
  page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text()); });
  await openHarness(page);
  const draft = page.getByLabel('助手草稿');
  await draft.fill('保留此草稿与选择');
  await page.getByRole('button', { name: '注入并发版本冲突' }).click();
  await expect(page.getByTestId('preview-state')).toHaveText('PREVIEW_CLEARED');
  await expect(page.getByTestId('metadata-revision')).toHaveText('1');
  await expect(draft).toHaveValue('保留此草稿与选择');
  await expect(page.getByText('运行已刷新，写命令未自动重放', { exact: true })).toBeVisible();
  expect(pageErrors).toEqual([]);
  expect(consoleErrors).toEqual([]);
});

test('freeze pending独立reload/reconcile，完成后才允许交给AI建模', async ({ page }) => {
  const pageErrors: Error[] = [];
  const consoleErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error));
  page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text()); });
  await openHarness(page);
  const injectFreeze = page.getByRole('button', { name: '注入定版登记中' });
  await injectFreeze.click();
  await expect(injectFreeze).toBeDisabled();
  await expect(page.getByText('定版登记中', { exact: true })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole('button', { name: '继续登记定版' })).toBeVisible();
  await expect(page.getByRole('button', { name: '交给 AI 建模' })).toHaveCount(0);
  await page.getByRole('button', { name: '继续登记定版' }).click();
  await expect(page.getByText('定版登记完成', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '交给 AI 建模' })).toBeVisible();
  expect(pageErrors).toEqual([]);
  expect(consoleErrors).toEqual([]);
});

test('次级审阅索引不可用时静默回退为受限列表，不显示无效重试入口', async ({ page }) => {
  const pageErrors: Error[] = [];
  const consoleErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error));
  page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text()); });
  await openHarness(page);
  await expect(page.getByTestId('timeline-count')).toHaveText('40');
  await expect(page.getByTestId('review-index-state')).toHaveText('受限列表可用');
  await expect(page.getByTestId('issue-window-count')).toHaveText('20');
  await expect(page.getByRole('alert')).toHaveCount(0);
  await expect(page.getByRole('button', { name: /审阅索引|重试审阅索引/u })).toHaveCount(0);
  expect(pageErrors).toEqual([]);
  expect(consoleErrors).toEqual([]);
});
