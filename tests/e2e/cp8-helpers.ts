import { expect, type Page } from '@playwright/test';
import type {
  PersistedTechnicalDocument,
  PersistedTechnicalRun,
} from './technical-details-identity.ts';

export async function resetBrowserState(page: Page) {
  await page.goto('/');
  await page.evaluate(async () => {
    localStorage.clear();
    sessionStorage.clear();
    const databases = await indexedDB.databases();
    await Promise.all(databases.map(({ name }) => new Promise<void>((resolve, reject) => {
      if (!name) { resolve(); return; }
      const request = indexedDB.deleteDatabase(name);
      request.onsuccess = () => resolve();
      request.onblocked = () => reject(new Error(`IndexedDB ${name} delete blocked`));
      request.onerror = () => reject(request.error ?? new Error(`IndexedDB ${name} delete failed`));
    })));
  });
  await page.reload();
}

export async function readPersistedTechnicalIdentity(page: Page): Promise<{
  run: PersistedTechnicalRun;
  documents: PersistedTechnicalDocument[];
}> {
  return page.evaluate(async () => {
    const readRaw = (databaseName: string, recordKey: string) => new Promise<string>((resolve, reject) => {
      const openRequest = indexedDB.open(databaseName, 1);
      openRequest.onupgradeneeded = () => {
        if (!openRequest.result.objectStoreNames.contains('state')) {
          openRequest.result.createObjectStore('state');
        }
      };
      openRequest.onerror = () => reject(openRequest.error ?? new Error(`无法打开持久metadata: ${databaseName}`));
      openRequest.onsuccess = () => {
        const database = openRequest.result;
        const request = database.transaction('state', 'readonly').objectStore('state').get(recordKey);
        request.onerror = () => {
          database.close();
          reject(request.error ?? new Error(`无法读取持久metadata: ${databaseName}`));
        };
        request.onsuccess = () => {
          database.close();
          const value = request.result as { raw?: unknown } | undefined;
          if (!value || typeof value.raw !== 'string') {
            reject(new Error(`持久metadata缺少raw: ${databaseName}`));
            return;
          }
          resolve(value.raw);
        };
      };
    });

    const pointerKey = Object.keys(localStorage).find((key) => (
      key.startsWith('linguan:guanyijia-workbench:active:v1:')
    ));
    if (!pointerKey) throw new Error('当前管伊佳运行指针不存在');
    const pointer = JSON.parse(localStorage.getItem(pointerKey) ?? 'null') as { runId?: unknown } | null;
    if (!pointer || typeof pointer.runId !== 'string' || !pointer.runId.trim()) {
      throw new Error('当前管伊佳运行指针缺少runId');
    }
    const [runRaw, documentRaw] = await Promise.all([
      readRaw('linguan-standardization-metadata-v1', 'standardization-runs'),
      readRaw('linguan-source-document-metadata-v1', 'source-documents'),
    ]);
    const runState = JSON.parse(runRaw) as { runs?: PersistedTechnicalRun[] };
    const documentState = JSON.parse(documentRaw) as { documents?: PersistedTechnicalDocument[] };
    const run = runState.runs?.find((candidate) => candidate.runId === pointer.runId);
    if (!run) throw new Error(`持久metadata中找不到当前运行: ${pointer.runId}`);
    if (!Array.isArray(documentState.documents)) throw new Error('来源文档持久metadata缺少documents');
    return { run, documents: documentState.documents };
  });
}

export async function login(page: Page, username = 'administrator', password = 'A7m!R9x#K4qV') {
  await page.getByLabel('用户名').fill(username);
  await page.getByLabel('密码').fill(password);
  await page.locator('form button[type="submit"]').click();
  await expect(page.getByLabel('模型项目')).toBeVisible();
}

export async function closeFormalCatalogBrowser(page: Page) {
  const closeButton = page.getByRole('button', { name: '收回正式模型' });
  if (await closeButton.isVisible()) {
    await closeButton.click();
    await expect(page.getByRole('complementary', { name: '正式模型浏览器' })).toBeHidden();
  }
}

export async function selectGuanyijia(page: Page) {
  await page.locator('.collaboration-header-select').click();
  await page.getByText('管伊佳 ERP 语义模型', { exact: true }).last().click();
  await expect(page.locator('.collaboration-header-select')).toContainText('管伊佳 ERP 语义模型');
}

export async function openStandardization(page: Page, navigation: 'DESKTOP' | 'MOBILE') {
  const standardizationStart = page.getByRole('button', { name: '开始资料整理', exact: true });
  const standardizationSources = page.getByRole('button', { name: '运行来源', exact: true });
  if (await standardizationStart.isVisible() || await standardizationSources.isVisible()) {
    return;
  }
  if (navigation === 'DESKTOP') {
    const standardization = page.locator('.app-menu .ant-menu-item[title="数据标准化"]');
    if (!await standardization.evaluate((element) => element.classList.contains('ant-menu-item-selected'))) {
      await standardization.click();
    }
  } else {
    const standardization = page.getByRole('navigation', { name: '手机主导航' })
      .getByRole('button', { name: '数据标准化', exact: true });
    if (await standardization.getAttribute('aria-current') !== 'page') {
      await standardization.click();
    }
  }
  await expect(standardizationStart).toBeVisible();
}

export async function enterMysqlDocument(page: Page) {
  await page.getByRole('button', { name: '开始资料整理' }).click();
  // Starting a run binds and validates the exact frozen V6 document before
  // it opens the review surface.  This is a real asynchronous boundary, not
  // an animation, so do not race it with Playwright's generic five seconds.
  await expect(page.locator('section.guanyijia-document-review').last())
    .toBeVisible({ timeout: 30_000 });
}

export async function expectNoHorizontalOverflow(page: Page) {
  const dimensions = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }));
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth);
}

export async function expectSingleWorkflowPrimary(page: Page) {
  await expect(page.locator('[data-workflow-primary="true"]:visible')).toHaveCount(1);
}

export async function expectTouchTargets(page: Page) {
  const tooSmall = await page.locator('.guanyijia-workbench-shell button:visible, .guanyijia-workbench-shell textarea:visible')
    .evaluateAll((elements) => elements.map((element) => {
      const rect = element.getBoundingClientRect();
      return { label: element.getAttribute('aria-label') ?? element.textContent?.trim(), width: rect.width, height: rect.height };
    }).filter(({ width, height }) => width < 44 || height < 44));
  expect(tooSmall).toEqual([]);
}
