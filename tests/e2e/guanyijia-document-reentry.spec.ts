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

test('Demo 来源过程从一开始列出五个来源，并按真实阶段累计展开', async ({ page }) => {
  test.setTimeout(120_000);
  // This contract intentionally keeps real timers: the static Demo holds an
  // ACTIVE stage after its callback so reviewers can see the progression.
  await page.setViewportSize({ width: 1440, height: 900 });
  await resetBrowserState(page);
  await login(page);
  await closeFormalCatalogBrowser(page);
  await selectGuanyijia(page);
  await openStandardization(page, 'DESKTOP');

  await page.getByRole('button', { name: '开始资料整理', exact: true }).click();
  const inspector = page.locator('aside[aria-label="来源资料"]');
  if (!await inspector.isVisible()) await page.getByRole('button', { name: '来源资料', exact: true }).click();
  const timeline = inspector.getByRole('list', { name: '标准化运行时间线', exact: true });
  const sources = timeline.locator(':scope > li.guanyijia-source-process-item');
  await expect(sources).toHaveCount(5);

  const database = sources.filter({ hasText: '数据库' });
  const databaseDisclosure = database.locator('button.guanyijia-source-process-disclosure');
  const stages = database.getByRole('list', { name: '数据库处理阶段', exact: true });
  await expect(databaseDisclosure).toHaveAttribute('aria-expanded', 'true');
  await expect(stages).toContainText('读取');
  await expect(stages.getByText('分析', { exact: true })).toHaveCount(0);
  await page.waitForTimeout(350);
  await expect(stages.getByText('分析', { exact: true })).toHaveCount(0);
  await expect(stages.getByText('分析', { exact: true })).toBeVisible({ timeout: 5_000 });
  await expect(stages.locator(':scope > li')).toHaveCount(2);

  const document = page.locator('section.guanyijia-document-review').last();
  await expect(document).toBeVisible({ timeout: 30_000 });
  await expect(stages.locator(':scope > li')).toHaveCount(4);
  await expect(stages).toContainText('组织');
  await expect(stages).toContainText('审阅');
  const github = sources.filter({ hasText: 'GitHub代码仓库' });
  await expect(github.locator('button.guanyijia-source-process-disclosure')).toHaveCount(0);
  await expect(github.locator('.guanyijia-source-process-details')).toHaveCount(0);
});

test('1440 启动自动打开数据库文档，时间线和来源资料均可再次进入', async ({ page }) => {
  test.setTimeout(120_000);
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
  // document is an explicit action inside that checkpoint's disclosure.
  await inspector.locator('.guanyijia-source-list-row').filter({ hasText: '数据库' }).click();
  await expect(document).toBeHidden();
  const databaseProcess = inspector.locator('.guanyijia-source-process-item').filter({ hasText: '数据库' });
  const databaseDisclosure = databaseProcess.locator('button.guanyijia-source-process-disclosure');
  if (await databaseDisclosure.getAttribute('aria-expanded') !== 'true') await databaseDisclosure.click();
  const openDatabase = databaseProcess.getByRole('button', { name: '查看当前审阅文档', exact: true });
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

test('完成数据库审阅后自动进入下一来源，已完成来源可只读回看', async ({ page }) => {
  test.setTimeout(120_000);
  await bootstrap(page, 1440);

  await page.getByRole('button', { name: '开始资料整理' }).click();
  const document = page.locator('section.guanyijia-document-review').last();
  const header = document.locator('header.guanyijia-document-review-header');
  await expect(header.getByRole('heading', {
    name: '数据库建模审阅（真实证据节选）', exact: true,
  })).toBeVisible({ timeout: 30_000 });

  await document.getByRole('button', { name: '保留当前结论', exact: true }).click();
  await document.getByRole('button', { name: '完成数据库审阅', exact: true }).click();

  const github = page.locator('section.guanyijia-document-review').last();
  await expect(github.getByRole('heading', { name: '代码仓库审阅', exact: true })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole('button', { name: '审阅下一个来源', exact: true })).toHaveCount(0);

  const inspector = page.locator('aside[aria-label="来源资料"]');
  if (!await inspector.isVisible()) await page.getByRole('button', { name: '来源资料', exact: true }).click();
  const workflow = inspector.getByRole('list', { name: '标准化运行时间线', exact: true });
  const sourceRows = workflow.locator(':scope > li.guanyijia-source-process-item');
  await expect(sourceRows).toHaveCount(5);
  const databaseProcess = sourceRows.filter({ hasText: '数据库' });
  const githubProcess = sourceRows.filter({ hasText: 'GitHub代码仓库' });
  const databaseDisclosure = databaseProcess.locator('button.guanyijia-source-process-disclosure');
  const githubDisclosure = githubProcess.locator('button.guanyijia-source-process-disclosure');
  await expect(databaseDisclosure).toHaveAttribute('aria-expanded', 'false');
  await expect(databaseProcess.locator('.guanyijia-source-process-details')).toBeHidden();
  await expect(githubDisclosure).toHaveAttribute('aria-expanded', 'true');
  await expect(githubProcess.locator('.guanyijia-source-process-details')).toBeVisible();
  await expect(githubProcess.getByRole('list', { name: 'GitHub代码仓库处理阶段', exact: true }))
    .toContainText('读取');
  await expect(githubProcess.getByText('审阅', { exact: true })).toBeVisible();

  await github.getByRole('button', { name: '返回时间线', exact: true }).click();
  await expect(github).toBeHidden();

  // A source title is a disclosure only: reopening the completed source
  // shows its accumulated green stages, and closing that history disclosure
  // returns focus to the live GitHub source rather than opening a document.
  await databaseDisclosure.click();
  await expect(databaseDisclosure).toHaveAttribute('aria-expanded', 'true');
  await expect(databaseProcess.locator('.guanyijia-source-process-details')).toBeVisible();
  await expect(databaseProcess.getByRole('list', { name: '数据库处理阶段', exact: true }))
    .toContainText('审阅');
  await expect(github).toBeHidden();
  await databaseDisclosure.click();
  await expect(githubDisclosure).toHaveAttribute('aria-expanded', 'true');
  await expect(databaseProcess.locator('.guanyijia-source-process-details')).toBeHidden();

  await inspector.locator('.guanyijia-source-list-row').filter({ hasText: '数据库' }).click();
  if (await databaseDisclosure.getAttribute('aria-expanded') !== 'true') await databaseDisclosure.click();
  await expect(databaseDisclosure).toHaveAttribute('aria-expanded', 'true');
  await databaseProcess.getByRole('button', { name: '查看审阅文档', exact: true }).click();
  await expect(document.getByRole('heading', {
    name: '数据库建模审阅（真实证据节选）', exact: true,
  })).toBeVisible();
  await expect(document.getByText('已审阅', { exact: true })).toBeVisible();
  await expect(document.getByRole('button', { name: '审阅下一个来源', exact: true })).toHaveCount(0);
  await expect(document.getByRole('button', { name: '完成数据库审阅', exact: true })).toHaveCount(0);
});

test('当前来源的即时差异层独占主操作，关闭后恢复当前来源审阅', async ({ page }) => {
  test.setTimeout(120_000);
  await bootstrap(page, 1440);

  await page.getByRole('button', { name: '开始资料整理' }).click();
  const database = page.locator('section.guanyijia-document-review').last();
  await expect(database).toBeVisible({ timeout: 30_000 });
  await database.getByRole('button', { name: '保留当前结论', exact: true }).click();
  await database.getByRole('button', { name: '完成数据库审阅', exact: true }).click();

  const github = page.locator('section.guanyijia-document-review').last();
  await expect(github.getByRole('heading', { name: '代码仓库审阅', exact: true })).toBeVisible({ timeout: 30_000 });
  await github.getByRole('button', { name: '采用推荐修改', exact: true }).click();
  const editor = github.getByRole('region', { name: '修改负库存控制候选', exact: true });
  await expect(editor.getByRole('region', { name: '修改效果', exact: true })).toBeVisible();
  const save = editor.getByRole('button', { name: '保存修改', exact: true });
  await expect(save).toBeEnabled({ timeout: 30_000 });
  await save.click();

  await github.getByRole('region', { name: '来源差异与比较', exact: true })
    .locator('[data-review-matter="DEBT_FIELDS"]')
    .getByRole('button', { name: '处理该项', exact: true }).click();
  const conflict = page.getByLabel('当前来源差异');
  await expect(conflict.getByRole('heading', { name: '欠款字段结构冲突', exact: true })).toBeVisible();
  const visiblePrimary = page.locator('[data-workflow-primary="true"]:visible');
  await expect(visiblePrimary).toHaveCount(1);
  await expect(visiblePrimary).toHaveText('保存当前决定');
  await expect(page.getByRole('button', { name: '审阅下一个来源', exact: true })).toHaveCount(0);

  await page.getByRole('button', { name: '返回来源审阅', exact: true }).click();
  await expect(github.getByRole('heading', { name: '代码仓库审阅', exact: true })).toBeVisible();
  // Closing an unresolved formal-conflict layer returns to the source
  // document, but it must not weaken the immediate-review gate by offering
  // completion. The same conflict remains actionable and owns the one
  // workflow primary until it is saved.
  await expect(github.getByRole('button', { name: '完成GitHub代码仓库审阅', exact: true })).toHaveCount(0);
  await expect(github.getByRole('region', { name: '来源差异与比较', exact: true })
    .locator('[data-review-matter="DEBT_FIELDS"]')
    .getByRole('button', { name: '处理该项', exact: true })).toBeVisible();
  await expect(page.locator('[data-workflow-primary="true"]:visible')).toHaveText('保存当前决定');
});

test('GitHub 本来源建议保存后，可在完成来源审阅前处理首项差异', async ({ page }) => {
  test.setTimeout(120_000);
  await bootstrap(page, 1440);

  await page.getByRole('button', { name: '开始资料整理' }).click();
  const database = page.locator('section.guanyijia-document-review').last();
  await expect(database).toBeVisible({ timeout: 30_000 });
  await database.getByRole('button', { name: '保留当前结论', exact: true }).click();
  await database.getByRole('button', { name: '完成数据库审阅', exact: true }).click();

  const github = page.locator('section.guanyijia-document-review').last();
  await expect(github.getByRole('heading', { name: '代码仓库审阅', exact: true })).toBeVisible({ timeout: 30_000 });
  await github.getByRole('button', { name: '采用推荐修改', exact: true }).click();
  const editor = github.getByRole('region', { name: '修改负库存控制候选', exact: true });
  await expect(editor.getByRole('region', { name: '修改效果', exact: true })).toBeVisible();
  const save = editor.getByRole('button', { name: '保存修改', exact: true });
  await expect(save).toBeEnabled({ timeout: 30_000 });
  await save.click();

  const debt = github.getByRole('region', { name: '来源差异与比较', exact: true })
    .locator('[data-review-matter="DEBT_FIELDS"]');
  await expect(debt.getByRole('button', { name: '处理该项', exact: true }))
    .toBeVisible({ timeout: 30_000 });
  await debt.getByRole('button', { name: '处理该项', exact: true }).click();

  const conflict = page.getByLabel('当前来源差异');
  await expect(conflict.getByRole('heading', { name: '欠款字段结构冲突', exact: true })).toBeVisible();
  const visiblePrimary = page.locator('[data-workflow-primary="true"]:visible');
  await expect(visiblePrimary).toHaveCount(1);
  await expect(visiblePrimary).toHaveText('保存当前决定');
  await expect(github.getByRole('button', { name: /^完成GitHub代码仓库审阅$/u })).toBeHidden();

  await conflict.getByRole('button', { name: '保存当前决定', exact: true }).click();
  await expect(github.getByRole('heading', { name: '代码仓库审阅', exact: true })).toBeVisible();
  await expect(github.getByRole('button', { name: '完成GitHub代码仓库审阅', exact: true })).toBeVisible();
  await expect(page.getByRole('status', { name: '操作反馈', exact: true })).toContainText('可继续完成当前来源审阅');
});

test('1024 资料抽屉可关闭并从页头恢复，五个来源保持可见', async ({ page }) => {
  test.setTimeout(120_000);
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
  test.setTimeout(120_000);
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
  test.setTimeout(120_000);
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
