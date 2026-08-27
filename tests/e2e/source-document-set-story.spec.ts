import { expect, test, type Page } from '@playwright/test';
import {
  closeFormalCatalogBrowser,
  login,
  openStandardization,
  resetBrowserState,
  selectGuanyijia,
} from './cp8-helpers.ts';

async function readAndReview(page: Page, sourceName: string) {
  const document = page.locator('section.guanyijia-document-review').last();
  await expect(document).toBeVisible({ timeout: 30_000 });
  await expect(document).toContainText(sourceName);
  await expect(document.getByRole('tab', { name: '审阅清单', exact: true })).toHaveAttribute('aria-selected', 'true');
  const keep = document.getByRole('button', { name: '保留当前结论', exact: true });
  if (await keep.count()) await keep.click();
  await document.getByRole('button', { name: /^完成.+审阅$/u }).click();
  await expect(document).toBeHidden();
}

async function resolve(page: Page, decision: string) {
  const conflict = page.getByLabel('当前来源差异');
  await expect(conflict).toBeVisible();
  await conflict.getByRole('radio', { name: decision }).click();
  await expect(conflict.getByLabel('中文决定理由')).toHaveCount(0);
  await conflict.getByRole('button', { name: '保存当前决定' }).click();
}

test('来源文档集合可连续审阅、登记治理缺口并由作者直接定版', async ({ page }) => {
  test.setTimeout(240_000);
  const pageErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  await page.clock.install({ time: new Date('2026-08-18T09:30:00+08:00') });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await resetBrowserState(page);
  await login(page);
  await closeFormalCatalogBrowser(page);
  await selectGuanyijia(page);
  await openStandardization(page, 'DESKTOP');

  await page.getByRole('button', { name: '开始资料整理' }).click();
  await readAndReview(page, '数据库建模审阅');
  await readAndReview(page, 'GitHub代码仓库审阅');
  await resolve(page, '保留当前结论');
  await readAndReview(page, '业务文档审阅');
  await readAndReview(page, 'ERP管理制度审阅');
  await resolve(page, '登记为缺口');
  await expect(page.getByLabel('当前来源差异')).toContainText('状态 9 业务含义冲突');
  await resolve(page, '登记为缺口');
  await readAndReview(page, '术语图审阅');

  await page.getByRole('button', { name: '生成标准化结果' }).click();
  await expect(page.getByRole('button', { name: '确认结果并定版' })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole('button', { name: /独立审核|审批意见/u })).toHaveCount(0);
  await page.getByRole('button', { name: '确认结果并定版' }).click();
  await expect(page.getByText('标准化结果已定版')).toBeVisible({ timeout: 30_000 });

  await page.reload();
  await openStandardization(page, 'DESKTOP');
  await expect(page.getByText('标准化结果已定版')).toBeVisible();
  expect(pageErrors).toEqual([]);
});
