import { expect, test, type Locator, type Page } from '@playwright/test';
import {
  closeFormalCatalogBrowser,
  login,
  openStandardization,
  resetBrowserState,
  selectGuanyijia,
} from './cp8-helpers.ts';

async function bootstrap(page: Page, width: number) {
  await page.clock.install({ time: new Date('2026-08-18T09:00:00+08:00') });
  await page.clock.setFixedTime(new Date('2026-08-18T09:00:00+08:00'));
  await page.setViewportSize({ width, height: 900 });
  await resetBrowserState(page);
  await login(page);
  const closeCatalog = page.getByRole('button', { name: '收回正式模型' });
  if (await closeCatalog.count() && await closeCatalog.first().isVisible()) {
    await closeFormalCatalogBrowser(page);
  }
  await selectGuanyijia(page);
  await openStandardization(page, 'DESKTOP');
}


async function expectNoHorizontalOverflow(page: Page) {
  const dimensions = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }));
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth);
}

async function expectNoHorizontalOverflowWithin(target: Locator) {
  const dimensions = await target.evaluate((element) => ({
    scrollWidth: element.scrollWidth,
    clientWidth: element.clientWidth,
  }));
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth);
}

test('1440 启动自动打开数据库文档，时间线和来源资料均可再次进入', async ({ page }) => {
  await bootstrap(page, 1440);

  await page.getByRole('button', { name: '运行来源', exact: true }).click();
  const sourceConfiguration = page.getByRole('heading', { name: '运行来源', exact: true })
    .locator('xpath=ancestor::section[contains(@class,"source-center-demo-configuration")]');
  await expect(sourceConfiguration).toBeVisible();
  await expect(sourceConfiguration.locator('[data-source-role]:visible')).toHaveCount(5);
  await expect(sourceConfiguration).toContainText('已选来源');
  await expect(sourceConfiguration.getByRole('tab', { name: '实例', exact: true })).toBeVisible();
  await expect(sourceConfiguration.getByRole('button', { name: /^(测试连接|重新扫描|添加来源|导入 YAML|冻结最新快照为)/u })).toHaveCount(0);
  await sourceConfiguration.getByRole('button', { name: '返回标准化工作区', exact: true }).click();
  await expect(sourceConfiguration).toBeHidden();

  await page.getByRole('button', { name: '开始资料整理' }).click();
  const document = page.locator('section.guanyijia-document-review').last();
  await expect(document).toBeVisible({ timeout: 30_000 });
  await expect(document.locator('header.guanyijia-document-review-header').getByRole('heading', {
    name: '数据库建模审阅（真实证据节选）',
    exact: true,
  })).toBeVisible();

  // The source opens on actionable review results. Source material remains
  // available from each claim rather than being an anonymous document preface.
  const resultTab = document.getByRole('tab', { name: '审阅事项', exact: true });
  await expect(resultTab).toHaveAttribute('aria-selected', 'true');
  const resultPanel = document.getByRole('region', { name: '审阅事项' });
  await expect(resultPanel).toBeVisible();
  const firstClaim = resultPanel.locator('[data-review-claim]').first();
  await expect(firstClaim).toBeVisible();

  // Curated evidence selection keeps the reader in place and expands the
  // matching material immediately below the conclusion.
  await firstClaim.getByRole('button', { name: '查看来源依据', exact: true }).click();
  await expect(resultTab).toHaveAttribute('aria-selected', 'true');
  await expect(firstClaim.getByRole('region', { name: '来源依据', exact: true })).toBeVisible();

  const inspector = page.locator('aside[aria-label="来源资料"]');
  if (!await inspector.isVisible()) await page.getByRole('button', { name: '来源资料', exact: true }).click();
  await expect(inspector).toBeVisible();
  await expect(inspector.locator('.guanyijia-source-list-row')).toHaveCount(5);
  // The redesigned side panel is a source-and-workflow navigator. Source
  // excerpts remain in the review claim where the reader requested them,
  // rather than duplicating a stale detail block in this panel.
  await inspector.locator('.guanyijia-source-list-row').filter({ hasText: 'GitHub代码仓库' }).click();
  await expect(inspector.locator('.guanyijia-source-list-row').filter({ hasText: 'GitHub代码仓库' }))
    .toContainText('待读取');

  await document.getByRole('button', { name: '返回时间线' }).click();
  await expect(document).toBeHidden();

  // Selecting a source only locates its workflow checkpoint. Reopening the
  // document is an explicit action on that checkpoint.
  await inspector.locator('.guanyijia-source-list-row').filter({ hasText: '数据库' }).click();
  await expect(document).toBeHidden();
  const openDatabase = page.getByRole('button', { name: '打开数据库', exact: true });
  await expect(openDatabase).toBeVisible();
  await openDatabase.click();
  await expect(document).toBeVisible();
  await expect(document.locator('header.guanyijia-document-review-header').getByRole('heading', {
    name: '数据库建模审阅（真实证据节选）',
    exact: true,
  })).toBeVisible();

  await document.getByRole('button', { name: '返回时间线' }).click();
  await expect(document).toBeHidden();
  await page.getByRole('button', { name: '收起来源资料', exact: true }).click();
  await expect(inspector).toBeHidden();
  await page.getByRole('button', { name: '来源资料', exact: true }).click();
  await expect(inspector).toBeVisible();
  await expect(inspector.locator('.guanyijia-source-list-row')).toHaveCount(5);
});

test('1024 资料抽屉可关闭并从页头恢复，五个来源保持可见', async ({ page }) => {
  await bootstrap(page, 1024);

  await page.getByRole('button', { name: '开始资料整理' }).click();
  const document = page.locator('section.guanyijia-document-review').last();
  await expect(document).toBeVisible({ timeout: 30_000 });
  await document.getByRole('button', { name: '返回时间线' }).click();
  await expect(document).toBeHidden();

  // Returning to the timeline unmounts the document-local trigger. The
  // persistent workbench tool row is the recovery entry for the drawer.
  const openSources = page.getByRole('button', { name: '来源资料', exact: true });
  await expect(openSources).toBeVisible();
  await openSources.click();

  const inspector = page.getByRole('dialog', { name: '来源资料' });
  await expect(inspector).toBeVisible();
  await expect(inspector.locator('.guanyijia-source-list-row')).toHaveCount(5);
  await inspector.getByRole('button', { name: '关闭来源资料' }).click();
  await expect(inspector).toBeHidden();

  await page.getByRole('button', { name: '来源资料', exact: true }).click();
  await expect(inspector).toBeVisible();
  await expect(inspector.locator('.guanyijia-source-list-row')).toHaveCount(5);
});

test('1024 同源依据只在结论下展开，不会强制打开来源资料抽屉', async ({ page }) => {
  await bootstrap(page, 1024);

  await page.getByRole('button', { name: '开始资料整理' }).click();
  const document = page.locator('section.guanyijia-document-review').last();
  await expect(document).toBeVisible({ timeout: 30_000 });

  // Close the optional materials drawer first. Opening a source excerpt is an
  // in-context reading action, not a navigation action that may interrupt the
  // reviewer by reopening this layer.
  const openSources = document.locator('#guanyijia-document-inspector-trigger');
  if (await openSources.isVisible()) await openSources.click();
  const inspector = page.getByRole('dialog', { name: '来源资料', exact: true });
  if (await inspector.isVisible()) await inspector.getByRole('button', { name: '关闭来源资料' }).click();
  await expect(inspector).toBeHidden();

  const firstClaim = document.getByRole('region', { name: '审阅事项', exact: true })
    .locator('[data-review-claim]').first();
  await firstClaim.getByRole('button', { name: '查看来源依据', exact: true }).click();

  await expect(firstClaim.getByRole('region', { name: '来源依据', exact: true })).toBeVisible();
  await expect(inspector).toBeHidden();
});

test('390 审阅事项和标准化文档保持受控横向滚动', async ({ page }) => {
  await bootstrap(page, 390);

  await page.getByRole('button', { name: '开始资料整理' }).click();
  const document = page.locator('section.guanyijia-document-review').last();
  await expect(document).toBeVisible({ timeout: 30_000 });
  await expect(document.locator('header.guanyijia-document-review-header').getByRole('heading', {
    name: '数据库建模审阅（真实证据节选）',
    exact: true,
  })).toBeVisible();

  const resultTab = document.getByRole('tab', { name: '审阅事项', exact: true });
  await expect(resultTab).toHaveAttribute('aria-selected', 'true');
  const resultPanel = document.getByRole('region', { name: '审阅事项' });
  await expect(resultPanel).toBeVisible();
  await document.getByRole('tab', { name: '标准化文档', exact: true }).click();
  const markdownPanel = document.getByRole('region', { name: '标准化文档' });
  await expect(markdownPanel.getByRole('button', { name: '阅读版', exact: true })).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await expectNoHorizontalOverflowWithin(document);
  await expectNoHorizontalOverflowWithin(markdownPanel);
});
