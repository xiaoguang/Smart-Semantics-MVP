import { mkdirSync } from 'node:fs';
import { expect, test, type Page } from '@playwright/test';
import { expectNoHorizontalOverflow, login, resetBrowserState } from './cp8-helpers.ts';

const visualDirectory = 'artifacts/cp8-visual';
mkdirSync(visualDirectory, { recursive: true });

async function capture(page: Page, name: string) {
  await page.screenshot({ path: `${visualDirectory}/${name}.png`, fullPage: false, animations: 'disabled' });
}

async function selectRetail(page: Page) {
  const closeCatalog = page.getByRole('button', { name: '收回正式模型' });
  if (await closeCatalog.isVisible()) await closeCatalog.click();
  await page.locator('.collaboration-header-select').click();
  await page.getByText('零售经营语义模型', { exact: true }).last().click();
  await expect(page.locator('.collaboration-header-select')).toContainText('零售经营语义模型');
}

async function openRetailFormalModeling(page: Page) {
  await page.locator('.app-menu .ant-menu-item[title="AI 建模"]').click();
  await expect(page.getByRole('complementary', { name: '正式模型浏览器' })).toBeVisible();
}

function observeRuntimeErrors(page: Page) {
  const pageErrors: string[] = [];
  const consoleErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });
  return { pageErrors, consoleErrors };
}

async function switchUser(page: Page, username: string, password: string) {
  await page.locator('.user-avatar').click();
  await page.getByRole('menuitem', { name: '切换用户' }).click();
  const dialog = page.getByRole('dialog', { name: '切换用户' });
  await dialog.getByLabel('用户名').fill(username);
  await dialog.getByLabel('密码').fill(password);
  await dialog.getByRole('button', { name: '验证并切换' }).click();
  await expect(dialog).toBeHidden();
}

test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-08-18T12:00:00.000Z'));
  await resetBrowserState(page);
});

test('fresh context 保留零售正式 V1 identity、计数与只读边界', async ({ page }) => {
  const errors = observeRuntimeErrors(page);
  await login(page);
  await selectRetail(page);
  await openRetailFormalModeling(page);

  const browser = page.getByRole('complementary', { name: '正式模型浏览器' });
  await expect(browser).toContainText('V1 正式模型');
  await expect(browser).toContainText('已发布、不可变');
  await expect(browser).toContainText('实体7');
  await expect(browser).toContainText('事件5');
  await expect(browser).toContainText('字段84');
  await expect(browser).toContainText('关系15');
  const identity = await page.evaluate(() => {
    const shared = JSON.parse(localStorage.getItem('linguan:collaboration:shared:v4') ?? '{}') as {
      catalogs?: Record<string, Array<{ catalogId?: string; catalogVersion?: string; fingerprint?: string }>>;
    };
    return shared.catalogs?.group_retail_ops ?? [];
  });
  expect(identity).toHaveLength(1);
  expect(identity[0]).toMatchObject({
    catalogId: 'catalog_group_retail_v1',
    catalogVersion: 'V1',
    fingerprint: 'catalog_group_retail_v1',
  });
  await capture(page, '17-protected-retail-v1');
  expect(errors).toEqual({ pageErrors: [], consoleErrors: [] });
});

test('个人草稿按用户和项目隔离且不改写零售正式 Catalog', async ({ page }) => {
  test.setTimeout(60_000);
  const errors = observeRuntimeErrors(page);
  await login(page);
  await selectRetail(page);
  await openRetailFormalModeling(page);
  const before = await page.evaluate(() => localStorage.getItem('linguan:collaboration:shared:v4'));

  await page.getByText('我的草稿', { exact: true }).click();
  await expect(page.locator('.collaboration-draft-bar')).toContainText('仅自己可见');
  await expect(page.getByPlaceholder(/提问，或添加 Markdown 到个人草稿/u)).toBeVisible();
  const personalDrafts = await page.evaluate(() => {
    const raw = localStorage.getItem('linguan:collaboration:drafts:v4:user_administer') ?? '[]';
    return JSON.parse(raw) as Array<{ ownerUserId?: string; modelSpaceId?: string }>;
  });
  expect(personalDrafts).toEqual([
    expect.objectContaining({ ownerUserId: 'user_administer', modelSpaceId: 'group_retail_ops' }),
  ]);
  expect(await page.evaluate(() => localStorage.getItem('linguan:collaboration:shared:v4'))).toBe(before);

  await switchUser(page, 'kenan.zhang', 'K4z@N8c!W3sT');
  await expect(page.locator('.collaboration-header-select')).toContainText('零售经营语义模型');
  await openRetailFormalModeling(page);
  await expect(page.locator('.collaboration-view-switch .ant-segmented-item-selected')).toHaveText('正式模型');
  await expect(page.getByText('已发布 V1', { exact: true })).toBeVisible();
  await expect(page.locator('.collaboration-draft-bar')).toHaveCount(0);
  expect(await page.evaluate(() => localStorage.getItem('linguan:collaboration:drafts:v4:user_kenan_zhang'))).toBeNull();
  const adminDraftsBeforeKenan = personalDrafts;
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('linguan:collaboration:drafts:v4:user_administer') ?? '[]'))).toEqual(adminDraftsBeforeKenan);

  await page.getByText('我的草稿', { exact: true }).click();
  await expect(page.locator('.collaboration-draft-bar')).toContainText('仅自己可见');
  const kenanDrafts = await page.evaluate(() => JSON.parse(localStorage.getItem('linguan:collaboration:drafts:v4:user_kenan_zhang') ?? '[]') as Array<{ ownerUserId?: string; modelSpaceId?: string }>);
  expect(kenanDrafts).toEqual([
    expect.objectContaining({ ownerUserId: 'user_kenan_zhang', modelSpaceId: 'group_retail_ops' }),
  ]);
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('linguan:collaboration:drafts:v4:user_administer') ?? '[]'))).toEqual(adminDraftsBeforeKenan);
  expect(await page.evaluate(() => localStorage.getItem('linguan:collaboration:shared:v4'))).toBe(before);

  await switchUser(page, 'administrator', 'A7m!R9x#K4qV');
  await expect(page.locator('.collaboration-header-select')).toContainText('零售经营语义模型');
  await openRetailFormalModeling(page);
  await expect(page.locator('.collaboration-view-switch .ant-segmented-item-selected')).toHaveText('正式模型');
  await expect(page.getByText('已发布 V1', { exact: true })).toBeVisible();
  await page.getByText('我的草稿', { exact: true }).click();
  await expect(page.locator('.collaboration-draft-bar')).toContainText('仅自己可见');
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('linguan:collaboration:drafts:v4:user_administer') ?? '[]'))).toEqual(adminDraftsBeforeKenan);
  expect(await page.evaluate(() => localStorage.getItem('linguan:collaboration:shared:v4'))).toBe(before);

  await page.getByLabel('模型项目').click();
  await page.getByText('管伊佳 ERP 语义模型', { exact: true }).last().click();
  await expect(page.locator('.collaboration-draft-bar')).toHaveCount(0);
  expect(await page.evaluate(() => {
    const raw = localStorage.getItem('linguan:collaboration:drafts:v4:user_administer') ?? '[]';
    return (JSON.parse(raw) as Array<{ modelSpaceId?: string }>).filter(({ modelSpaceId }) => modelSpaceId === 'guanyijia_erp');
  })).toEqual([]);
  expect(await page.evaluate(() => localStorage.getItem('linguan:collaboration:shared:v4'))).toBe(before);
  await capture(page, '18-protected-personal-draft-isolation');
  expect(errors).toEqual({ pageErrors: [], consoleErrors: [] });
});

test('旧零售 M3 仍使用八来源审阅 runtime 而非管伊佳五源 Workbench', async ({ page }) => {
  test.setTimeout(60_000);
  const errors = observeRuntimeErrors(page);
  await login(page);
  await selectRetail(page);
  await page.locator('.app-menu .ant-menu-item[title="数据标准化"]').click();
  await page.getByRole('button', { name: '生成来源文档' }).click();

  await expect(page.getByRole('heading', { name: '审阅工作区' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '审阅阶段' }).getByRole('button')).toHaveCount(3);
  await page.getByRole('navigation', { name: '审阅阶段' }).getByRole('button', { name: /来源文档/u }).click();
  await expect(page.locator('.source-document-master-detail > nav > button')).toHaveCount(8);
  await expect(page.getByText('管伊佳数据标准化', { exact: true })).toHaveCount(0);
  await capture(page, '19-protected-retail-legacy-m3');
  expect(errors).toEqual({ pageErrors: [], consoleErrors: [] });
});

test('390px 手机零售本体详情返回后恢复同一列表且无横向溢出', async ({ page }) => {
  const errors = observeRuntimeErrors(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  await selectRetail(page);
  const closeCatalog = page.getByRole('button', { name: '收回正式模型' });
  if (await closeCatalog.isVisible()) await closeCatalog.click();
  await page.getByRole('navigation', { name: '手机主导航' })
    .getByRole('button', { name: '本体', exact: true }).click();

  const firstObject = page.getByRole('button', { name: /查看本体/u }).first();
  await firstObject.click();
  await expect(page.locator('.ontology-detail-pane')).toBeVisible();
  await page.getByRole('button', { name: '返回列表' }).click();
  await expect(firstObject).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await capture(page, '20-protected-mobile-ontology-return');
  expect(errors).toEqual({ pageErrors: [], consoleErrors: [] });
});
