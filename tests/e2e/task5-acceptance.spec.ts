import { expect, test, type Page } from '@playwright/test';

async function login(page: Page) {
  await page.goto('/');
  await page.getByLabel('用户名').fill('administrator');
  await page.getByLabel('密码').fill('A7m!R9x#K4qV');
  await page.locator('form button[type="submit"]').click();
  await expect(page.getByLabel('模型项目')).toBeVisible();
}

async function openStandardization(page: Page) {
  const closeModelBrowser = page.getByLabel('收回正式模型');
  if (await closeModelBrowser.isVisible()) await closeModelBrowser.click();
  const mobile = page.getByRole('navigation', { name: '手机主导航' });
  if (await mobile.isVisible()) await mobile.getByRole('button', { name: '数据标准化', exact: true }).click();
  else await page.locator('.app-menu .ant-menu-item[title="数据标准化"]').click({ force: true });
  await expect(page.getByText('标准化进度', { exact: true })).toBeVisible();
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
});

test('360px与1024px数据标准化只通过唯一入口打开全屏审阅区', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 });
  await login(page);
  await openStandardization(page);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(360);
  const generate = page.getByRole('button', { name: '生成来源文档' });
  if (await generate.isVisible()) await generate.click();
  else await page.getByRole('button', { name: '继续审阅' }).click();
  await expect(page.locator('.standardization-inspector.overlay-open')).toBeVisible();
  await expect(page.getByRole('button', { name: '返回处理进度' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '审阅阶段' }).getByRole('button')).toHaveCount(3);
  expect(await page.locator('.standardization-inspector.overlay-open').evaluate((element) => ({
    width: element.getBoundingClientRect().width, scrollWidth: element.scrollWidth, clientWidth: element.clientWidth,
  }))).toEqual(expect.objectContaining({ width: 360, scrollWidth: 360, clientWidth: 360 }));
  await expect(page.locator('body')).toHaveClass(/standardization-overlay-active/);

  await page.setViewportSize({ width: 1024, height: 900 });
  await page.getByRole('button', { name: '返回处理进度' }).click();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(1024);
  await expect(page.locator('.standardization-inspector')).not.toBeVisible();
  await expect(page.getByRole('button', { name: '继续审阅' })).toBeVisible();
  await expect(page.locator('body')).not.toHaveClass(/standardization-overlay-active/);
});

test('1440桌面数据标准化删除伪操作并支持审阅侧栏真实收起和恢复', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await login(page);
  await openStandardization(page);
  await expect(page.getByLabel('Catalog 版本')).toHaveCount(0);
  for (const label of ['创建标准化任务', '查看资料', '打开审核检查器', '发送']) {
    await expect(page.getByRole('button', { name: label })).toHaveCount(0);
  }
  await expect(page.locator('.standardization-composer')).toHaveCount(0);
  await expect(page.getByRole('navigation', { name: '审阅阶段' }).getByRole('button')).toHaveCount(3);
  const paneHeaders = await page.locator('.standardization-pane-header').evaluateAll((elements) => elements.map((element) => ({
    top: element.getBoundingClientRect().top,
    height: element.getBoundingClientRect().height,
  })));
  expect(paneHeaders).toHaveLength(2);
  expect(Math.abs(paneHeaders[0].top - paneHeaders[1].top)).toBeLessThanOrEqual(1);
  expect(Math.abs(paneHeaders[0].height - paneHeaders[1].height)).toBeLessThanOrEqual(1);
  const alignmentStage = page.getByRole('navigation', { name: '审阅阶段' }).getByRole('button', { name: /来源差异/ });
  await alignmentStage.click();
  await expect(alignmentStage).toHaveAttribute('aria-current', 'step');

  const thread = page.locator('.standardization-thread');
  const before = await thread.boundingBox();
  await page.getByRole('button', { name: '收起审阅区' }).click();
  await expect(page.locator('.data-standardization-layout')).toHaveClass(/review-collapsed/);
  await page.waitForTimeout(220);
  const after = await thread.boundingBox();
  expect(before).not.toBeNull();
  expect(after).not.toBeNull();
  expect(after!.width).toBeGreaterThan(before!.width + 300);
  await expect(page.getByRole('button', { name: '继续审阅' })).toBeVisible();
  await page.getByRole('button', { name: '继续审阅' }).click();
  await expect(page.getByRole('button', { name: '收起审阅区' })).toBeVisible();
  await expect(alignmentStage).toHaveAttribute('aria-current', 'step');
});

test('200%等效窄屏使用三阶段全屏审阅且没有页签溢出下拉', async ({ page }) => {
  await page.setViewportSize({ width: 720, height: 500 });
  await login(page);
  await openStandardization(page);
  const generate = page.getByRole('button', { name: '生成来源文档' });
  if (await generate.isVisible()) await generate.click();
  else await page.getByRole('button', { name: '继续审阅' }).click();
  const stages = page.getByRole('navigation', { name: '审阅阶段' });
  await expect(stages.getByRole('button')).toHaveCount(3);
  await expect(stages.getByRole('button', { name: /来源文档/ })).toBeVisible();
  await expect(stages.getByRole('button', { name: /来源差异/ })).toBeVisible();
  await expect(stages.getByRole('button', { name: /输出文档/ })).toBeVisible();
  expect(await stages.evaluate((element) => element.scrollWidth)).toBeLessThanOrEqual(await stages.evaluate((element) => element.clientWidth));
  await expect(page.locator('.ant-tabs-nav-more')).toHaveCount(0);
});
