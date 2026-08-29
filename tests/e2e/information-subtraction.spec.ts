import { expect, test, type Page } from '@playwright/test';
import {
  closeFormalCatalogBrowser,
  login,
  openStandardization,
  resetBrowserState,
  selectGuanyijia,
} from './cp8-helpers.ts';

async function bootstrap(page: Page, width: number, height = 900) {
  await page.clock.install({ time: new Date('2026-08-18T09:00:00+08:00') });
  await page.clock.setFixedTime(new Date('2026-08-18T09:00:00+08:00'));
  await page.setViewportSize({ width, height });
  await resetBrowserState(page);
  await login(page);
  await closeFormalCatalogBrowser(page);
  await selectGuanyijia(page);
  await openStandardization(page, 'DESKTOP');
}

async function startDatabaseReview(page: Page) {
  await page.getByRole('button', { name: '开始资料整理', exact: true }).click();
  const document = page.locator('section.guanyijia-document-review').last();
  await expect(document).toBeVisible({ timeout: 30_000 });
  return document;
}

test('1440 右栏只有一套来源进展，且把证据支持信息放在其下方', async ({ page }) => {
  await bootstrap(page, 1440);

  const workbench = page.locator('.guanyijia-workbench-shell');
  const toggle = workbench.locator('[data-source-panel-toggle="true"]');
  const inspector = workbench.locator('aside[aria-label="来源资料"]');

  await expect(toggle).toHaveCount(1);
  await expect(inspector).toBeVisible();
  await expect(inspector.locator('.guanyijia-facts-inspector-header')).toHaveCount(0);
  await expect(inspector.locator('.guanyijia-source-list-row')).toHaveCount(0);
  await expect(inspector.locator('.guanyijia-source-progress-panel')).toBeVisible();
  await expect(inspector.locator('.guanyijia-evidence-support-panel')).toBeVisible();
  await expect(inspector.getByRole('heading', { name: '来源进展', exact: true })).toBeVisible();
  await expect(inspector.getByRole('heading', { name: '证据与支持信息', exact: true })).toBeVisible();
  await expect(inspector.getByText('数据库', { exact: true })).toBeVisible();
  await expect(inspector.getByText('GitHub代码仓库', { exact: true })).toBeVisible();

  await startDatabaseReview(page);
  await expect(workbench.locator('[data-source-panel-toggle="true"]:visible')).toHaveCount(1);
  await expect(inspector.locator('.guanyijia-facts-inspector-header')).toHaveCount(0);
});

test('来源进展标题只展开或收起过程，不打开文档且不改变主区滚动位置', async ({ page }) => {
  await bootstrap(page, 1440);
  const document = await startDatabaseReview(page);

  const scroll = page.locator('.guanyijia-workbench-scroll');
  // 来源文档层可以在短内容时完整落入可视区，不应为了测试而强制
  // 外层工作台产生滚动；只记录当前滚动位置并确认来源定位不会改变它。
  const scrollTop = await scroll.evaluate((element) => element.scrollTop);
  await expect.poll(async () => Math.abs(
    await scroll.evaluate((element) => element.scrollTop) - scrollTop,
  )).toBeLessThanOrEqual(1);

  const sourceDisclosure = page.locator('.guanyijia-source-process-disclosure').filter({ hasText: '数据库' });
  await expect(sourceDisclosure).toHaveAttribute('aria-expanded', 'true');
  await sourceDisclosure.click();
  await expect(sourceDisclosure).toHaveAttribute('aria-expanded', 'false');
  await expect(document).toBeVisible();
  await expect(document.getByRole('heading', { name: '数据库建模审阅（真实证据节选）', exact: true })).toBeVisible();
  await expect.poll(async () => Math.abs(
    await scroll.evaluate((element) => element.scrollTop) - scrollTop,
  )).toBeLessThanOrEqual(1);
});

test('主区独立滚动，底部审阅助手不遮挡正文', async ({ page }) => {
  await bootstrap(page, 1440);
  await startDatabaseReview(page);

  const scroll = page.locator('.guanyijia-workbench-scroll');
  const assistant = page.getByLabel('审阅助手', { exact: true });
  const composer = assistant.getByPlaceholder(/问我当前结论/u);
  await expect(composer).toBeVisible();

  const layout = await Promise.all([
    scroll.evaluate((element) => ({
      bottom: element.getBoundingClientRect().bottom,
    })),
    assistant.evaluate((element) => ({
      top: element.getBoundingClientRect().top,
      bottom: element.getBoundingClientRect().bottom,
      position: window.getComputedStyle(element).position,
    })),
  ]);
  const threadBottom = await page.locator('.guanyijia-workbench-thread')
    .evaluate((element) => element.getBoundingClientRect().bottom);
  // 当前短文档可以无需外层滚动即可完整可读；助手仍必须处在文档
  // 内容之后而非覆盖其上。
  expect(layout[1].top).toBeGreaterThanOrEqual(layout[0].bottom - 1);
  expect(layout[1].bottom).toBeLessThanOrEqual(threadBottom + 1);
  expect(layout[1].position).toBe('static');
});

test('390 审阅文档与底部对话共用一个移动工作区', async ({ page }) => {
  await bootstrap(page, 390, 844);
  const document = await startDatabaseReview(page);
  const thread = page.locator('.guanyijia-workbench-thread');
  const scroll = page.locator('.guanyijia-workbench-scroll');
  const assistant = page.getByLabel('审阅助手', { exact: true });
  const composer = assistant.getByPlaceholder(/问我当前结论/u);

  await expect(composer).toBeVisible();
  const layout = await Promise.all([
    thread.evaluate((element) => ({
      bottom: element.getBoundingClientRect().bottom,
      position: window.getComputedStyle(element).position,
    })),
    document.evaluate((element) => window.getComputedStyle(element).position),
    scroll.evaluate((element) => ({
      bottom: element.getBoundingClientRect().bottom,
    })),
    assistant.evaluate((element) => ({
      top: element.getBoundingClientRect().top,
      bottom: element.getBoundingClientRect().bottom,
      position: window.getComputedStyle(element).position,
    })),
  ]);

  expect(layout[0].position).toBe('fixed');
  expect(layout[1]).toBe('static');
  // 移动端短文档同样无需伪造滚动，重点是助手保持在同一工作区且
  // 不遮挡阅读内容。
  expect(layout[3].position).toBe('static');
  expect(layout[3].top).toBeGreaterThanOrEqual(layout[2].bottom - 1);
  expect(layout[3].bottom).toBeLessThanOrEqual(layout[0].bottom + 1);
});

test('右栏默认显示当前来源概览，选择事项后显示证据摘要而不铺开源码或 DDL', async ({ page }) => {
  await bootstrap(page, 1440);
  const document = await startDatabaseReview(page);
  const matters = document.getByRole('region', { name: '审阅事项', exact: true });
  const inspector = page.locator('aside[aria-label="来源资料"]');

  await expect(document.getByRole('button', { name: /^来源材料 \d+$/u })).toHaveCount(0);
  await expect(matters.getByRole('region', { name: '来源材料', exact: true })).toHaveCount(0);
  await expect(inspector.getByText('当前来源概览', { exact: true })).toBeVisible();
  const firstClaim = matters.locator('[data-review-claim]').first();
  await firstClaim.getByRole('button', { name: '查看来源依据', exact: true }).click();
  await expect(firstClaim.getByRole('region', { name: '来源依据', exact: true })).toBeVisible();
  await expect(inspector.getByText('当前事项', { exact: true })).toBeVisible();
  const openTechnicalEvidence = inspector.getByRole('button', { name: '在正文查看技术依据', exact: true });
  await expect(openTechnicalEvidence).toBeVisible();
  await expect(inspector.locator('pre')).toHaveCount(0);
  await openTechnicalEvidence.click();
  await expect(document.getByRole('tab', { name: '标准化文档', exact: true })).toHaveAttribute('aria-selected', 'true');
});

test('审阅页标题、页签与待确认事项使用同一内容左边界', async ({ page }) => {
  await bootstrap(page, 1440);
  const document = await startDatabaseReview(page);
  const title = document.getByRole('heading', { name: '数据库建模审阅（真实证据节选）', exact: true });
  const matterTitle = document.getByRole('heading', { name: '待确认事项', exact: true });
  const firstTab = document.getByRole('tab', { name: '审阅事项', exact: true });

  const positions = await Promise.all([
    title.evaluate((element) => element.getBoundingClientRect().left),
    matterTitle.evaluate((element) => element.getBoundingClientRect().left),
    firstTab.evaluate((element) => element.getBoundingClientRect().left),
  ]);
  expect(Math.abs(positions[0] - positions[1])).toBeLessThanOrEqual(1);
  expect(Math.abs(positions[0] - positions[2])).toBeLessThanOrEqual(1);
});

test('对象目录默认展开并直接读取对象列表', async ({ page }) => {
  await bootstrap(page, 1440);
  const document = await startDatabaseReview(page);
  await document.getByRole('tab', { name: '审阅结论', exact: true }).click();
  const conclusions = document.getByRole('region', { name: '审阅结论', exact: true });
  const objectDetails = conclusions.locator('details[aria-label="对象明细"]');

  await expect(objectDetails).toHaveCount(1);
  await expect(objectDetails).toHaveAttribute('open', '');
  await expect(objectDetails.locator('table')).toBeVisible();
  await expect(objectDetails.locator('tbody tr')).toHaveCount(31);
});
