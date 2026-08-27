import { expect, test, type Page } from '@playwright/test';

async function login(page: Page) {
  await page.goto('/');
  await page.getByLabel('用户名').fill('administrator');
  await page.getByLabel('密码').fill('A7m!R9x#K4qV');
  await page.locator('form button[type="submit"]').click();
  await expect(page.getByLabel('模型项目')).toBeVisible();
}

async function selectRetailProject(page: Page) {
  const openBrowser = page.getByLabel('正式模型浏览器');
  if (await openBrowser.isVisible()) await page.getByLabel('收回正式模型').click();
  await page.getByLabel('模型项目').click();
  await page.getByText('零售经营语义模型', { exact: true }).last().click();
  await expect(page.getByLabel('正式模型浏览器')).toBeVisible();
  await page.getByLabel('收回正式模型').click();
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
});

test('390px 壳层提供固定五项底栏、更多入口与完整两层上下文', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  await selectRetailProject(page);

  const mobileNav = page.getByRole('navigation', { name: '手机主导航' });
  await expect(mobileNav).toBeVisible();
  await expect(mobileNav.getByRole('button')).toHaveCount(5);
  for (const label of ['数据标准化', 'AI建模', '本体', '指标', '更多']) {
    await expect(mobileNav.getByRole('button', { name: label, exact: true })).toBeVisible();
  }

  const project = page.locator('.collaboration-header-select');
  const version = page.locator('.version-selector');
  const [projectBox, versionBox] = await Promise.all([project.boundingBox(), version.boundingBox()]);
  expect(projectBox?.width).toBeGreaterThan(150);
  expect(versionBox?.width).toBeGreaterThan(150);
  expect(versionBox!.y).toBeGreaterThanOrEqual(projectBox!.y);

  await mobileNav.getByRole('button', { name: '更多', exact: true }).click();
  const moreNav = page.getByRole('menu', { name: '更多功能' });
  await expect(moreNav).toBeVisible();
  for (const label of ['业务规则', '同义词', '时间语义']) {
    await expect(moreNav.getByText(label, { exact: true })).toBeVisible();
  }
  await moreNav.getByText('同义词', { exact: true }).click();
  await expect(page.locator('.app-page-alias')).toBeVisible();

  const navBox = await mobileNav.boundingBox();
  expect(navBox!.y + navBox!.height).toBeLessThanOrEqual(844);
  expect(navBox!.width).toBeLessThanOrEqual(390);
});

async function openMobileMorePage(page: Page, label: '业务规则' | '同义词' | '时间语义') {
  const mobileNav = page.getByRole('navigation', { name: '手机主导航' });
  await mobileNav.getByRole('button', { name: '更多', exact: true }).click();
  await page.getByRole('menu', { name: '更多功能' }).getByText(label, { exact: true }).click();
}

test('390px 指标、规则、同义词和时间语义使用共享分页信息卡', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  await selectRetailProject(page);

  await page.getByRole('navigation', { name: '手机主导航' }).getByRole('button', { name: '指标', exact: true }).click();
  const metricList = page.getByRole('list', { name: '指标列表' });
  await expect(metricList).toBeVisible();
  await expect(metricList.getByRole('listitem').first()).toBeVisible();

  await openMobileMorePage(page, '业务规则');
  const ruleList = page.getByRole('list', { name: '业务规则列表' });
  await expect(ruleList).toBeVisible();

  await openMobileMorePage(page, '同义词');
  const aliasList = page.getByRole('list', { name: '同义词列表' });
  await expect(aliasList).toBeVisible();
  await expect(aliasList).toHaveAttribute('data-page-size', '20');
  expect(await aliasList.getByRole('listitem').count()).toBeLessThanOrEqual(20);

  await openMobileMorePage(page, '时间语义');
  const timeRuleList = page.getByRole('list', { name: '时间规则列表' });
  await expect(timeRuleList).toBeVisible();
  await expect(timeRuleList.getByRole('listitem').first()).toBeVisible();
});

test('1440px 响应式数据视图保留带明确最小宽度的桌面表格', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await login(page);
  await page.locator('.app-menu .ant-menu-item[title="指标配置"]').click();

  const desktop = page.locator('[data-responsive-view="指标列表"] .responsive-data-view-desktop');
  await expect(desktop).toBeVisible();
  await expect(desktop).toHaveAttribute('data-min-table-width', /\d{3,}/);
  await expect(page.getByRole('list', { name: '指标列表' })).toBeHidden();
});

test('320px 与 768px 壳层无页面级横溢且上下文可读', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 720 });
  await login(page);
  await selectRetailProject(page);

  await expect(page.getByRole('navigation', { name: '手机主导航' })).toBeVisible();
  const widths = await page.locator('.collaboration-header-select, .version-selector').evaluateAll((elements) => (
    elements.map((element) => element.getBoundingClientRect().width)
  ));
  expect(widths).toHaveLength(2);
  expect(Math.min(...widths)).toBeGreaterThan(110);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(320);

  await page.setViewportSize({ width: 768, height: 900 });
  await expect(page.getByRole('navigation', { name: '手机主导航' })).toBeHidden();
  await expect(page.locator('.app-sider')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(768);
});

test('390px 编辑抽屉单列无横溢、页脚可见且覆盖期间隐藏底栏', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  await selectRetailProject(page);
  await page.getByText('我的草稿', { exact: true }).click();
  const collapseCatalog = page.getByLabel('收回正式模型');
  if (await collapseCatalog.isVisible()) await collapseCatalog.click();
  await page.getByRole('navigation', { name: '手机主导航' }).getByRole('button', { name: '指标', exact: true }).click();
  await page.getByRole('button', { name: '新建指标' }).click();

  const drawer = page.locator('.ant-drawer-open');
  await expect(drawer).toBeVisible();
  await expect(page.getByRole('navigation', { name: '手机主导航' })).toBeHidden();
  const dimensions = await drawer.locator('.ant-drawer-content-wrapper').evaluate((element) => ({
    width: element.getBoundingClientRect().width,
    scrollWidth: element.scrollWidth,
    clientWidth: element.clientWidth,
  }));
  expect(dimensions.width).toBeLessThanOrEqual(390.5);
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth);
  const footer = drawer.locator('.ant-drawer-footer');
  await expect(footer).toBeVisible();
  const footerBox = await footer.boundingBox();
  expect(footerBox!.y + footerBox!.height).toBeLessThanOrEqual(844);

  await footer.locator('button').first().click();
  await expect(page.getByRole('navigation', { name: '手机主导航' })).toBeVisible();
});

test('390px 登录账号抽屉操作按钮不裁切', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await page.getByRole('button', { name: '查看体验账号' }).click();

  const drawer = page.locator('.ant-drawer-open');
  await expect(drawer).toBeVisible();
  const actionBounds = await drawer.locator('.demo-account-actions').evaluateAll((groups) => groups.map((group) => ({
    left: group.getBoundingClientRect().left,
    right: group.getBoundingClientRect().right,
    buttons: [...group.querySelectorAll('button')].map((button) => ({
      left: button.getBoundingClientRect().left,
      right: button.getBoundingClientRect().right,
    })),
  })));
  for (const group of actionBounds) {
    for (const button of group.buttons) {
      expect(button.left).toBeGreaterThanOrEqual(group.left);
      expect(button.right).toBeLessThanOrEqual(group.right);
    }
  }
  expect(await drawer.locator('.ant-drawer-content-wrapper').evaluate((element) => element.scrollWidth)).toBeLessThanOrEqual(390);
});

test('技术 ID 与 Locator 提供展开和复制而不挤压中文名称', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  await selectRetailProject(page);
  await page.getByRole('navigation', { name: '手机主导航' }).getByRole('button', { name: '数据标准化', exact: true }).click();
  await page.getByRole('button', { name: '来源设置' }).click();

  await page.locator('.source-batch-technical summary').click();
  const technical = page.locator('.readable-technical-value').first();
  await expect(technical).toBeVisible();
  await expect(technical.getByRole('button', { name: /复制/ })).toBeVisible();
  const expand = technical.getByRole('button', { name: '展开' });
  if (await expand.count()) {
    await expand.click();
    await expect(technical).toHaveClass(/expanded/);
  }
  const chineseName = page.locator('.source-list-row strong').first();
  if (await chineseName.count()) {
    expect((await chineseName.evaluate((element) => getComputedStyle(element).wordBreak))).not.toBe('break-all');
  }
});

test('390px 本体关系保持列表到详情再返回，层级和值定义保留各自入口', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  await selectRetailProject(page);
  await page.getByText('我的草稿', { exact: true }).click();
  const collapseCatalog = page.getByLabel('收回正式模型');
  if (await collapseCatalog.isVisible()) await collapseCatalog.click();
  await page.getByRole('navigation', { name: '手机主导航' }).getByRole('button', { name: '本体', exact: true }).click();
  await page.getByText('本体列表', { exact: true }).click();
  await page.getByRole('button', { name: '查看本体 客户' }).click();

  await page.getByRole('tab', { name: '关系结构' }).click();
  const relationList = page.getByRole('list', { name: '关系列表' });
  await expect(relationList).toBeVisible();
  await relationList.getByRole('button', { name: /查看关系/ }).first().click();
  await expect(page.locator('.relation-detail-pane.mobile-active')).toBeVisible();
  await page.getByRole('button', { name: '返回关系列表' }).click();
  await expect(relationList).toBeVisible();

  await page.getByRole('button', { name: '返回列表' }).click();
  await page.getByRole('button', { name: '查看本体 行政区域' }).click();
  await page.getByRole('tab', { name: '层级定义' }).click();
  const hierarchyPanel = page.getByRole('tabpanel', { name: '层级定义' });
  await expect(hierarchyPanel).toBeVisible();
  await expect(hierarchyPanel.locator('.hierarchy-card-list, .ant-empty').first()).toBeVisible();
  const valueTab = page.getByRole('tab', { name: '值定义' });
  await valueTab.click();
  await expect(valueTab).toHaveAttribute('aria-selected', 'true');
  const valuePanel = page.getByRole('tabpanel', { name: '值定义' });
  await expect(valuePanel).toBeVisible();
  const valueList = valuePanel.locator('.value-definition-list');
  if (await valueList.count()) {
    await valuePanel.getByRole('button', { name: /维护值/ }).first().click();
    await expect(page.getByRole('button', { name: '返回值定义列表' })).toBeVisible();
  } else {
    await expect(valuePanel.locator('.ant-empty')).toBeVisible();
  }
});

test('390px 目标与时间卡保留桌面端的业务字段和更多操作', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  await selectRetailProject(page);
  await page.getByText('我的草稿', { exact: true }).click();
  const collapseCatalog = page.getByLabel('收回正式模型');
  if (await collapseCatalog.isVisible()) await collapseCatalog.click();

  await openMobileMorePage(page, '业务规则');
  await page.getByRole('tab', { name: /目标值维护/ }).click();
  const targetCards = page.getByRole('list', { name: /目标值列表/ }).getByRole('listitem');
  if (await targetCards.count()) {
    await expect(targetCards.first()).toContainText('维度限定');
    await expect(targetCards.first().getByRole('button', { name: /更多操作/ })).toBeVisible();
  } else {
    await expect(page.getByText('无符合条件的目标值')).toBeVisible();
  }

  await openMobileMorePage(page, '时间语义');
  const autoCards = page.getByRole('list', { name: /自动时间规则列表/ }).getByRole('listitem');
  if (await autoCards.count()) await expect(autoCards.first()).toContainText('偏移/天数');
  else await expect(page.getByText('自动生成规则（0 条）')).toBeVisible();

  await page.getByText(/自定义规则（\d+ 条）/).click();
  const customCard = page.getByRole('list', { name: '时间规则列表' }).getByRole('listitem').first();
  await expect(customCard).toContainText('预览');
  await expect(customCard.getByRole('button', { name: /更多操作/ })).toBeVisible();

  await page.getByRole('tab', { name: /时间日历/ }).click();
  await expect(page.getByRole('list', { name: '时间日历列表' }).getByRole('button', { name: /更多操作/ }).first()).toBeVisible();

  await page.getByRole('tab', { name: /节假日/ }).click();
  const holidayList = page.getByRole('list', { name: '节假日列表' });
  const holidayActions = holidayList.getByRole('button', { name: /更多操作/ });
  if (await holidayActions.count()) await expect(holidayActions.first()).toBeVisible();
  else await expect(holidayList.locator('.ant-empty-description')).toHaveText('暂无数据');
  const periodActions = page.getByRole('list', { name: '节假日年度日期列表' }).getByRole('button', { name: /更多操作/ });
  if (await periodActions.count()) await expect(periodActions.first()).toBeVisible();
});

test('390px 同义词保存当前抽屉选中的目标名称与编码', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  await selectRetailProject(page);
  await page.getByText('我的草稿', { exact: true }).click();
  const collapseCatalog = page.getByLabel('收回正式模型');
  if (await collapseCatalog.isVisible()) await collapseCatalog.click();
  await openMobileMorePage(page, '同义词');

  await page.getByRole('button', { name: '新建同义词' }).click();
  const drawer = page.locator('.ant-drawer-open');
  await drawer.getByLabel('同义词文本').fill('手机目标绑定验收');
  await drawer.getByLabel('目标类型').click();
  await page.getByText('指标', { exact: true }).last().click();
  await drawer.getByLabel('目标对象').click();
  await page.getByText('销售金额 (metric_sales_amount)', { exact: true }).last().click();
  await page.getByRole('dialog', { name: '新建同义词' }).getByRole('button', { name: /保\s*存/ }).click();

  await page.getByRole('searchbox', { name: '搜索同义词或目标对象' }).fill('手机目标绑定验收');
  await page.getByRole('searchbox', { name: '搜索同义词或目标对象' }).press('Enter');
  const created = page.getByRole('list', { name: '同义词列表' }).getByRole('listitem').filter({ hasText: '手机目标绑定验收' });
  await expect(created).toContainText('销售金额');
  await expect(created.locator('code')).toHaveText('metric_sales_amount');
});
