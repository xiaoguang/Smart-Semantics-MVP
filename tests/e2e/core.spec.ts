import { expect, test, type Page } from '@playwright/test';

async function login(page: Page, username: string, password: string) {
  await page.goto('/');
  await page.getByLabel('用户名').fill(username);
  await page.getByLabel('密码').fill(password);
  await page.locator('form button[type="submit"]').click();
  await expect(page.getByLabel('模型项目')).toBeVisible();
}

async function selectRetailProject(page: Page) {
  const browser = page.getByLabel('正式模型浏览器');
  await expect(browser).toBeVisible();
  await page.getByLabel('收回正式模型').click();
  await expect(browser).toBeHidden();
  await page.getByLabel('模型项目').click();
  await page.getByText('零售经营语义模型', { exact: true }).last().click();
  await expect(browser).toBeVisible();
}

async function selectGuanyijiaProject(page: Page) {
  await page.getByLabel('模型项目').click();
  await page.getByText('管伊佳 ERP 语义模型', { exact: true }).last().click();
  await expect(page.getByText('已发布 V1', { exact: true })).toBeVisible();
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
});

test('管理员进入个人草稿后不白屏，输入框与唯一来源入口始终可见', async ({ page }) => {
  await login(page, 'administrator', 'A7m!R9x#K4qV');

  await page.getByText('我的草稿', { exact: true }).click();
  await expect(page.locator('.app-shell')).toBeVisible();
  await expect(page.getByText('草稿内容暂时无法显示')).toHaveCount(0);
  await expect(page.getByPlaceholder(/提问，或添加 Markdown 到个人草稿/)).toBeVisible();
  await expect(page.getByLabel('添加 Markdown')).toBeVisible();
});

test('零售正式模型与草稿严格隔离，正式态没有来源配置入口', async ({ page }) => {
  await login(page, 'administrator', 'A7m!R9x#K4qV');
  await selectRetailProject(page);

  await expect(page.getByText('正式模型', { exact: true })).toBeVisible();
  await expect(page.getByLabel('添加 Markdown')).toHaveCount(0);
  await expect(page.getByPlaceholder(/询问当前正式模型/)).toBeVisible();

  await page.getByText('我的草稿', { exact: true }).click();
  await expect(page.getByLabel('添加 Markdown')).toBeVisible();
  const input = page.getByPlaceholder(/提问，或添加 Markdown 到个人草稿/);
  await page.getByLabel('收回正式模型').click();
  const before = await input.boundingBox();
  await page.getByLabel('展开正式模型').click();
  await expect(input).toBeVisible();
  const after = await input.boundingBox();
  expect(before).not.toBeNull();
  expect(after).not.toBeNull();
  expect(after!.y + after!.height).toBeLessThanOrEqual(await page.evaluate(() => window.innerHeight));
});

test('手机本体页使用列表到详情导航，返回后恢复列表', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page, 'administrator', 'A7m!R9x#K4qV');
  await selectRetailProject(page);
  await page.getByLabel('收回正式模型').click();
  await page.getByRole('navigation', { name: '手机主导航' }).getByRole('button', { name: '本体', exact: true }).click();

  const firstObject = page.getByRole('button', { name: /查看本体/ }).first();
  await expect(firstObject).toBeVisible();
  await firstObject.click();
  await expect(page.getByRole('button', { name: '返回列表' })).toBeVisible();
  await expect(page.locator('.ontology-detail-pane')).toBeVisible();
  await page.getByRole('button', { name: '返回列表' }).click();
  await expect(firstObject).toBeVisible();
});

test('普通 HTTP 下账号复制失败会显示反馈而不是产生未处理异常', async ({ page, context }) => {
  const errors: Error[] = [];
  page.on('pageerror', (error) => errors.push(error));
  await context.grantPermissions([], { origin: 'http://127.0.0.1:5202' });
  await page.goto('/');
  await page.getByRole('button', { name: '查看体验账号' }).click();
  await page.getByLabel(/复制 .* 用户名/).first().click();
  await expect(page.locator('.ant-message')).toContainText(/已复制|无法自动复制/);
  expect(errors).toEqual([]);
});

test('Semantica内置示例从数据标准化进入来源中心并完成证据审阅', async ({ page }) => {
  await login(page, 'administrator', 'A7m!R9x#K4qV');
  await selectRetailProject(page);
  await page.locator('.app-menu .ant-menu-item[title="数据标准化"]').click({ force: true });
  await page.getByRole('button', { name: '来源设置' }).click();

  await expect(page.getByText('来源设置', { exact: true }).last()).toBeVisible();
  await page.getByRole('button', { name: /企业术语与本体图/ }).click();
  await expect(page.getByText('建模采集策略', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '加载内置示例' }).click();
  await expect(page.getByText('零售经营四源语义证据示例', { exact: true })).toBeVisible();
  await expect(page.getByText(/独立根来源/).last()).toBeVisible();

  await page.getByText(/互证结论 \d+/).click();
  await expect(page.getByText(/退款确认时点/).first()).toBeVisible();
  await page.getByRole('button', { name: '查看审阅说明书' }).click();
  await expect(page.getByText('review/04-互证结论.md', { exact: true })).toBeVisible();

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('button', { name: '导出审阅说明书 ZIP' })).toBeVisible();
});

test('数据标准化首屏进入聚焦审阅主链且不全量渲染证据', async ({ page }) => {
  await login(page, 'administrator', 'A7m!R9x#K4qV');
  await selectRetailProject(page);
  await page.locator('.app-menu .ant-menu-item[title="数据标准化"]').click({ force: true });

  const generate = page.getByRole('button', { name: '生成来源文档' });
  await expect(generate).toBeVisible();
  await generate.click();
  await expect(page.getByText(/标准建模文档 r8/)).toBeVisible();
  await expect(page.getByRole('navigation', { name: '审阅阶段' }).getByRole('button')).toHaveCount(3);
  const technical = page.locator('.standardization-review-technical');
  await technical.locator('summary').click();
  await expect(technical).toContainText('25条证据');
  expect(await page.locator('.standardization-inspector:visible *').count()).toBeLessThanOrEqual(200);
  await page.getByRole('navigation', { name: '审阅阶段' }).getByRole('button', { name: /来源文档/ }).click();
  await expect(page.locator('.source-document-master-detail > nav > button')).toHaveCount(8);
  await expect(page.locator('.evidence-index-list > article')).toHaveCount(0);
});

test('管伊佳证据基线从九段文档贯通五个正式业务页面', async ({ page }) => {
  await login(page, 'administrator', 'A7m!R9x#K4qV');
  await selectGuanyijiaProject(page);

  await page.locator('.app-menu .ant-menu-item[title="数据标准化"]').click({ force: true });
  await expect(page.getByRole('heading', { name: '数据标准化', exact: true })).toHaveCount(1);
  await expect(page.getByRole('button', { name: '开始资料整理' })).toBeVisible();

  await page.locator('.app-menu .ant-menu-item[title="本体建模"]').click({ force: true });
  await page.getByRole('button', { name: '查看本体 商品', exact: true }).click();
  await expect(page.getByText('商品', { exact: true }).last()).toBeVisible();
  await page.getByRole('tab', { name: '映射配置' }).click();
  await expect(page.getByText(/管伊佳 MySQL/).first()).toBeVisible();
  await expect(page.getByText(/来源快照 20260813T032528Z-abb0502c7d79/)).toBeVisible();

  await page.locator('.app-menu .ant-menu-item[title="指标配置"]').click({ force: true });
  await expect(page.getByRole('list', { name: '指标列表' }).or(page.getByRole('table'))).toContainText('采购金额');
  await expect(page.getByText('应收欠款', { exact: true })).toHaveCount(0);

  await page.locator('.app-menu .ant-menu-item[title="业务规则"]').click({ force: true });
  await expect(page.locator('.ant-empty:visible .ant-empty-description')).toHaveText('暂无数据');
  await page.getByRole('tab', { name: '待结构化候选 (8)' }).click();
  await expect(page.getByRole('list', { name: '待结构化规则候选列表' }).or(page.getByRole('table'))).toContainText('租户隔离');

  await page.locator('.app-menu .ant-menu-item[title="同义词"]').click({ force: true });
  await expect(page.getByRole('tab', { name: '同义词 (10)' })).toBeVisible();
  await expect(page.getByRole('list', { name: '同义词列表' }).or(page.getByRole('table'))).toContainText('供应商');

  await page.locator('.app-menu .ant-menu-item[title="时间语义"]').click({ force: true });
  await expect(page.getByText('自定义规则（9 条）', { exact: true })).toBeVisible();
  await expect(page.getByRole('list', { name: '时间规则列表' }).or(page.getByRole('table'))).toContainText('采购入库以 oper_time');
  await expect(page.getByText('未绑定日历').first()).toBeVisible();

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('list', { name: '时间规则列表' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});
