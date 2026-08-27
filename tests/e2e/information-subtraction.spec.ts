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

test('1440 来源资料只有页头一个开关，右栏没有重复标题盒', async ({ page }) => {
  await bootstrap(page, 1440);

  const workbench = page.locator('.guanyijia-workbench-shell');
  const toggle = workbench.locator('[data-source-panel-toggle="true"]');
  const inspector = workbench.locator('aside[aria-label="来源资料"]');

  await expect(toggle).toHaveCount(1);
  await expect(inspector).toBeVisible();
  await expect(inspector.locator('.guanyijia-facts-inspector-header')).toHaveCount(0);
  await expect(inspector.locator('.guanyijia-source-list-row')).toHaveCount(5);

  await startDatabaseReview(page);
  await expect(workbench.locator('[data-source-panel-toggle="true"]:visible')).toHaveCount(1);
  await expect(inspector.locator('.guanyijia-facts-inspector-header')).toHaveCount(0);
});

test('来源行只定位对应流程检查点，不打开文档且不改变主区滚动位置', async ({ page }) => {
  await bootstrap(page, 1440);
  const document = await startDatabaseReview(page);

  const scroll = page.locator('.guanyijia-workbench-scroll');
  const scrollState = await scroll.evaluate((element) => {
    const maxScrollTop = element.scrollHeight - element.clientHeight;
    const scrollTop = Math.min(96, Math.max(0, maxScrollTop));
    element.scrollTop = scrollTop;
    return { maxScrollTop, scrollTop };
  });
  expect(scrollState.maxScrollTop).toBeGreaterThan(0);
  await expect.poll(async () => Math.abs(
    await scroll.evaluate((element) => element.scrollTop) - scrollState.scrollTop,
  )).toBeLessThanOrEqual(1);

  const sourceRow = page.locator('.guanyijia-source-list-row').filter({ hasText: 'GitHub代码仓库' });
  await sourceRow.click();
  await expect(sourceRow).toHaveClass(/selected/u);
  const checkpoint = page.locator('[data-review-anchor="timeline:source:guanyijia_github"]');
  await expect(checkpoint).toHaveCount(1);
  await expect(checkpoint).toBeInViewport();
  await expect(document).toBeVisible();
  await expect(document.getByRole('heading', { name: '数据库建模审阅（真实证据节选）', exact: true })).toBeVisible();
  await expect(page.locator('.guanyijia-review-live-region')).toContainText('已定位GitHub代码仓库的处理进度');
  await expect.poll(async () => Math.abs(
    await scroll.evaluate((element) => element.scrollTop) - scrollState.scrollTop,
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
      scrollHeight: element.scrollHeight,
      clientHeight: element.clientHeight,
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
  expect(layout[0].scrollHeight).toBeGreaterThan(layout[0].clientHeight);
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
      scrollHeight: element.scrollHeight,
      clientHeight: element.clientHeight,
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
  expect(layout[2].scrollHeight).toBeGreaterThan(layout[2].clientHeight);
  expect(layout[3].position).toBe('static');
  expect(layout[3].top).toBeGreaterThanOrEqual(layout[2].bottom - 1);
  expect(layout[3].bottom).toBeLessThanOrEqual(layout[0].bottom + 1);
});

test('审阅清单不提供独立的来源材料统计或材料区域，依据只在结论内联', async ({ page }) => {
  await bootstrap(page, 1440);
  const document = await startDatabaseReview(page);
  const checklist = document.getByRole('region', { name: '审阅清单', exact: true });

  await expect(document.getByRole('button', { name: /^来源材料 \d+$/u })).toHaveCount(0);
  await expect(checklist.getByRole('region', { name: '来源材料', exact: true })).toHaveCount(0);
  const firstClaim = checklist.locator('[data-review-claim]').first();
  await firstClaim.getByRole('button', { name: '查看来源依据', exact: true }).click();
  await expect(firstClaim.getByRole('region', { name: '来源依据', exact: true })).toBeVisible();
});

test('对象明细默认折叠，展开后才读取对象列表', async ({ page }) => {
  await bootstrap(page, 1440);
  const document = await startDatabaseReview(page);
  const checklist = document.getByRole('region', { name: '审阅清单', exact: true });
  const objectDetails = checklist.locator('details[aria-label="对象明细"]');

  await expect(objectDetails).toHaveCount(1);
  await expect(objectDetails).not.toHaveAttribute('open', '');
  await expect(objectDetails.locator('table')).toBeHidden();
  await objectDetails.locator('summary').click();
  await expect(objectDetails.locator('tbody tr')).toHaveCount(31);
});
