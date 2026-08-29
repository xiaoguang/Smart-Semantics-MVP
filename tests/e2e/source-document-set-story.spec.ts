import { expect, test, type Locator, type Page } from '@playwright/test';
import {
  closeFormalCatalogBrowser,
  login,
  openStandardization,
  resetBrowserState,
  selectGuanyijia,
} from './cp8-helpers.ts';

async function readAndReview(page: Page, sourceName: string, nextSourceName?: string): Promise<Locator> {
  const document = page.locator('section.guanyijia-document-review').last();
  await expect(document).toBeVisible({ timeout: 30_000 });
  await expect(document).toContainText(sourceName, { timeout: 30_000 });
  await expect(document.getByRole('tab', { name: '审阅事项', exact: true })).toHaveAttribute('aria-selected', 'true');
  const keep = document.getByRole('button', { name: '保留当前结论', exact: true });
  if (await keep.count()) await keep.click();
  await document.getByRole('button', { name: /^完成.+审阅$/u }).click();
  if (nextSourceName) {
    const next = page.locator('section.guanyijia-document-review').last();
    await expect(next).toBeVisible({ timeout: 30_000 });
    await expect(next).toContainText(nextSourceName, { timeout: 30_000 });
    return next;
  }
  return document;
}

async function resolve(page: Page, decision: string, nextTitle?: string) {
  const conflict = page.getByLabel('当前来源差异');
  await expect(conflict).toBeVisible();
  await conflict.getByRole('radio', { name: decision }).click();
  await expect(conflict.getByLabel('中文决定理由')).toHaveCount(0);
  await conflict.getByRole('button', { name: '保存当前决定' }).click();
  if (nextTitle) {
    await expect(conflict.getByRole('heading', { name: nextTitle, exact: true })).toBeVisible();
  }
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
  const github = await readAndReview(page, '数据库建模审阅', '代码仓库审阅');
  await github.getByRole('button', { name: '采用推荐修改', exact: true }).click();
  const githubEditor = github.getByRole('region', { name: '修改负库存控制候选', exact: true });
  await expect(githubEditor.getByRole('region', { name: '修改效果', exact: true })).toBeVisible();
  const saveGithubSuggestion = githubEditor.getByRole('button', { name: '保存修改', exact: true });
  await expect(saveGithubSuggestion).toBeEnabled({ timeout: 30_000 });
  await saveGithubSuggestion.click();
  const debt = github.getByRole('region', { name: '来源差异与比较', exact: true })
    .locator('[data-review-matter="DEBT_FIELDS"]');
  await expect(debt.getByRole('button', { name: '处理该项', exact: true })).toBeVisible();
  await debt.getByRole('button', { name: '处理该项', exact: true }).click();
  await resolve(page, '保留当前结论');

  await readAndReview(page, '代码仓库审阅', '业务说明');
  const policy = await readAndReview(page, '业务说明', 'ERP管理制度');
  const comparisons = policy.getByRole('region', { name: '来源差异与比较', exact: true });
  await expect(comparisons.locator('[data-review-matter="NEGATIVE_STOCK"]')
    .getByRole('button', { name: '处理该项', exact: true })).toBeVisible();
  await comparisons.locator('[data-review-matter="NEGATIVE_STOCK"]')
    .getByRole('button', { name: '处理该项', exact: true }).click();
  await resolve(page, '登记为缺口', '状态 9 业务含义冲突');
  await resolve(page, '登记为缺口');
  await readAndReview(page, 'ERP管理制度', '企业术语图');
  await readAndReview(page, '企业术语图');

  const preview = page.getByLabel('完整合并标准化结果预览', { exact: true });
  await expect(preview).toBeVisible();
  await expect(preview.getByRole('tab', { name: '审阅事项', exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(preview.locator('[data-merged-review-decision]')).toHaveCount(3);
  await preview.getByRole('tab', { name: '标准化文档', exact: true }).click();
  await expect(preview.getByRole('button', { name: 'Markdown 源文', exact: true })).toBeVisible();
  await expect(preview).toContainText('完整固定九章');
  await expect(page.getByRole('button', { name: /独立审核|审批意见/u })).toHaveCount(0);
  await page.getByRole('button', { name: '确认并定版' }).click();
  await expect(page.getByRole('heading', { name: '标准化结果已定版', exact: true }))
    .toBeVisible({ timeout: 120_000 });

  await page.reload();
  await openStandardization(page, 'DESKTOP');
  await expect(page.getByRole('heading', { name: '标准化结果已定版', exact: true })).toBeVisible();
  expect(pageErrors).toEqual([]);
});
