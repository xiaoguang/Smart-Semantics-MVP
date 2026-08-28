import { expect, test, type Locator, type Page } from '@playwright/test';
import {
  closeFormalCatalogBrowser,
  expectNoHorizontalOverflow,
  login,
  openStandardization,
  resetBrowserState,
  selectGuanyijia,
} from './cp8-helpers.ts';

async function bootstrap(page: Page, width: number, installClock = true) {
  if (installClock) {
    await page.clock.install({ time: new Date('2026-08-18T09:00:00+08:00') });
    await page.clock.setFixedTime(new Date('2026-08-18T09:00:00+08:00'));
  }
  await page.setViewportSize({ width, height: 900 });
  await resetBrowserState(page);
  await login(page);
  const closeCatalog = page.getByRole('button', { name: '收回正式模型' });
  if (await closeCatalog.count() && await closeCatalog.first().isVisible()) await closeFormalCatalogBrowser(page);
  await selectGuanyijia(page);
  await openStandardization(page, 'DESKTOP');
}

async function openCuratedReview(page: Page) {
  await page.getByRole('button', { name: '开始资料整理' }).click();
  const document = page.locator('section.guanyijia-document-review').last();
  await expect(document).toBeVisible({ timeout: 30_000 });
  return document;
}

async function completeDatabaseAndOpenGithub(page: Page, database: Locator) {
  await database.getByRole('button', { name: '保留当前结论', exact: true }).click();
  await database.getByRole('button', { name: '完成数据库审阅', exact: true }).click();
  await expect(database.getByRole('button', { name: '审阅下一个来源', exact: true })).toBeVisible();
  await database.getByRole('button', { name: '审阅下一个来源', exact: true }).click();
  const github = page.locator('section.guanyijia-document-review').last();
  await expect(github.getByRole('heading', { name: '代码仓库审阅', exact: true })).toBeVisible({ timeout: 30_000 });
  return github;
}

async function expectNoHorizontalOverflowWithin(target: Locator) {
  const dimensions = await target.evaluate((element) => ({ scrollWidth: element.scrollWidth, clientWidth: element.clientWidth }));
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth);
}

test('数据库先展示审阅事项，页头动作能定位到待确认事项', async ({ page }) => {
  test.setTimeout(120_000);
  await bootstrap(page, 1440);
  const document = await openCuratedReview(page);

  const sourceAndWorkflow = page.locator('.guanyijia-facts-inspector');
  await expect(sourceAndWorkflow).toBeVisible();
  await expect(sourceAndWorkflow.getByText('数据库', { exact: true }).first()).toBeVisible();
  await expect(sourceAndWorkflow.getByText('流程步骤', { exact: true })).toHaveCount(0);
  await expect(sourceAndWorkflow.locator('.guanyijia-timeline')).toHaveCount(1);
  await expect(page.locator('.guanyijia-workbench-thread .guanyijia-timeline')).toHaveCount(0);

  const mattersTab = document.getByRole('tab', { name: '审阅事项', exact: true });
  await expect(mattersTab).toHaveAttribute('aria-selected', 'true');
  await expect(document.getByRole('tab', { name: '审阅结论', exact: true })).toBeVisible();
  await expect(document.getByRole('tab', { name: '标准化文档', exact: true })).toBeVisible();
  await expect(document.getByRole('tab', { name: '依据追踪', exact: true })).toHaveCount(0);
  await expect(document.getByRole('button', { name: '核对 1 项建议', exact: true })).toBeVisible();
  await expect(document.getByRole('button', { name: '完成数据库审阅', exact: true })).toHaveCount(0);

  await document.getByRole('button', { name: '核对 1 项建议', exact: true }).click();
  const matters = document.getByRole('region', { name: '审阅事项', exact: true });
  const tasks = matters.getByRole('region', { name: '本来源建议', exact: true });
  const depotHead = tasks.locator('article[data-review-claim="claim:guanyijia_mysql:curated-e005"]');
  await expect(depotHead).toBeVisible();
  await expect(matters.getByRole('heading', { name: '待确认事项', exact: true })).toBeVisible();
  await expect(tasks.getByRole('heading', { name: '本来源建议', exact: true })).toBeVisible();
  await expect(depotHead.getByRole('button', { name: '采用推荐修改', exact: true })).toBeVisible();
  // The primary action takes the reviewer directly to the suggested change and
  // expands its supporting material.  The available action is therefore to
  // collapse it, not to open a second hidden panel.
  await expect(depotHead.getByRole('button', { name: '收起来源依据', exact: true })).toBeVisible();
  await expect(depotHead.getByRole('region', { name: '来源依据', exact: true })).toContainText('jsh_depot_head');
  await expect(page.getByRole('region', { name: '当前上下文', exact: true })).toHaveCount(0);
  await document.getByRole('tab', { name: '审阅结论', exact: true }).click();
  const conclusions = document.getByRole('region', { name: '审阅结论', exact: true });
  await expect(conclusions.getByRole('region', { name: '关键业务结论', exact: true })).toBeVisible();
  // Tasks and the six highlighted conclusions are deliberately not duplicated
  // in the compact object inventory.
  const objectDetails = conclusions.locator('details[aria-label="对象明细"]');
  await expect(objectDetails).toHaveCount(1);
  await expect(objectDetails).toHaveAttribute('open', '');
  await expect(objectDetails.locator('tbody tr')).toHaveCount(31);
  await expect(conclusions.getByText('## 9. 待确认事项', { exact: true })).toHaveCount(0);
  await expect(conclusions.getByText(/guanyijia_mysql:/u)).toHaveCount(0);
  // Material is part of each conclusion's inline evidence; it is not a
  // separate filter or a second reading surface.
  await expect(matters.getByRole('region', { name: '来源材料', exact: true })).toHaveCount(0);
  await expect(document.getByRole('button', { name: /^来源材料 \d+$/u })).toHaveCount(0);
});

test('标准化文档将审阅结论阅读化，并可查看同一修订的 Markdown 源文', async ({ page }) => {
  test.setTimeout(120_000);
  await bootstrap(page, 1440);
  const document = await openCuratedReview(page);

  await document.getByRole('tab', { name: '标准化文档', exact: true }).click();
  const standardized = document.getByRole('region', { name: '标准化文档', exact: true });
  await expect(standardized.getByRole('button', { name: '阅读版', exact: true })).toBeVisible();
  await expect(standardized.getByRole('button', { name: 'Markdown 源文', exact: true })).toBeVisible();
  // The rich frozen V6 content is rendered only inside this reader.  It keeps
  // the existing checklist/workbench intact while giving every source the
  // shared nine-chapter coordinate used by downstream AI modeling.
  const chapterDirectory = standardized.getByRole('navigation', { name: '标准化文档目录', exact: true });
  await expect(chapterDirectory.getByRole('link')).toHaveCount(9);
  await expect(chapterDirectory.getByRole('link', { name: '文档说明', exact: true })).toBeVisible();
  await expect(chapterDirectory.getByRole('link', { name: '待确认事项', exact: true })).toBeVisible();
  const accountObject = standardized.locator('.guanyijia-standardized-document-object').filter({ hasText: '账户信息（jsh_account）' }).first();
  await expect(accountObject).toContainText('期初金额');
  await expect(accountObject).toContainText('当前余额');
  const accountStructure = accountObject.locator('details.guanyijia-object-details');
  await expect(accountStructure).not.toHaveAttribute('open', '');
  // The wrapper summary opens the saved schema view.  The view itself has
  // nested details for the complete field list and raw DDL, so select only
  // this direct child rather than every descendant summary.
  await accountStructure.locator(':scope > summary').click();
  await expect(accountStructure.getByRole('region', { name: 'jsh_account 数据表结构', exact: true })).toContainText('current_amount');
  await expect(accountStructure.getByText('查看原始 DDL', { exact: true })).toBeVisible();
  await expect(standardized.getByRole('button', { name: '查看来源依据', exact: true })).toHaveCount(0);
  await expect(document.getByText('该业务载体存在于当前部署结构', { exact: false })).toHaveCount(0);

  await standardized.getByRole('button', { name: 'Markdown 源文', exact: true }).click();
  const source = standardized.getByLabel('当前 Markdown 源文', { exact: true });
  await expect(source).toContainText('账户信息（jsh_account）');
  await expect(source.locator('span')).not.toHaveCount(0);

  await expect(document.getByRole('tab', { name: '依据追踪', exact: true })).toHaveCount(0);
});

test('剧本修改同步到审阅事项、审阅结论与标准化文档，且移动端无横向溢出', async ({ page }) => {
  test.setTimeout(180_000);
  await bootstrap(page, 1440);
  const document = await openCuratedReview(page);
  await document.getByRole('button', { name: '核对 1 项建议', exact: true }).click();
  const depotHead = document.locator('article[data-review-claim="claim:guanyijia_mysql:curated-e005"]');
  await depotHead.getByRole('button', { name: '采用推荐修改', exact: true }).click();

  const editor = depotHead.getByRole('region', { name: '修改库存单据主表', exact: true });
  await expect(editor).toBeVisible();
  await expect(editor).toBeInViewport();
  await expect(editor.locator('input')).toBeFocused();
  await expect(editor.locator('input')).toHaveValue('库存单据表头（jsh_depot_head）');
  await expect(editor.locator('textarea')).toHaveCount(0);
  await expect(editor.getByRole('heading', { name: '可修改：业务名称', exact: true })).toBeVisible();
  await expect(editor).toContainText('保存后，审阅事项、审阅结论和标准化文档中的这条名称会更新。');

  // The page-level task action opens its supporting material immediately.
  // Only open it here when the primary action did not already do so.
  if (await editor.getByRole('button', { name: '查看来源依据', exact: true }).count()) {
    await editor.getByRole('button', { name: '查看来源依据', exact: true }).click();
  }
  await expect(editor.getByRole('region', { name: '来源依据', exact: true })).toContainText('CREATE TABLE `jsh_depot_head`');
  await expect(editor.getByRole('button', { name: '收起来源依据', exact: true })).toBeVisible();
  const evidence = editor.getByRole('region', { name: '来源依据', exact: true });
  await expect(evidence.getByText('重点字段 6/32', { exact: true })).toBeVisible();
  await expect(evidence).not.toContainText('该材料支持的结论');
  await expect(page.getByRole('status', { name: '操作反馈', exact: true })).toContainText('已打开');

  await editor.getByRole('button', { name: '预览修改', exact: true }).click();
  await expect(editor.getByRole('region', { name: '修改预览', exact: true })).toContainText('库存单据主表');
  await expect(editor.getByRole('region', { name: '修改预览', exact: true })).toContainText('库存单据表头（jsh_depot_head）');
  await expect(page.getByRole('status', { name: '操作反馈', exact: true })).toContainText('已生成修改预览');
  await editor.getByRole('button', { name: '确认修改', exact: true }).click();

  await expect(document.getByRole('button', { name: '完成数据库审阅', exact: true })).toBeVisible();
  // A completed task moves out of the pending group and remains visible as a
  // saved decision in the review-items tab.
  await expect(document.locator('[data-review-claim="claim:guanyijia_mysql:curated-e005"]')).toContainText('库存单据表头（jsh_depot_head）');
  await expect(document.getByRole('region', { name: '已处理事项', exact: true })).toBeVisible();
  await document.getByRole('tab', { name: '标准化文档', exact: true }).click();
  await expect(document.getByRole('region', { name: '标准化文档', exact: true })).toContainText('库存单据表头（jsh_depot_head）');
  await expect(document.getByRole('tab', { name: '依据追踪', exact: true })).toHaveCount(0);

  const context = page.context();
  await page.close();
  const mobilePage = await context.newPage();
  try {
    await bootstrap(mobilePage, 390, false);
    const mobileDocument = await openCuratedReview(mobilePage);
    await expect(mobileDocument.getByRole('tab', { name: '审阅事项', exact: true })).toHaveAttribute('aria-selected', 'true');
    await expectNoHorizontalOverflow(mobilePage);
    await expectNoHorizontalOverflowWithin(mobileDocument);
  } finally {
    await mobilePage.close();
  }
});

test('全部跨来源比较留在审阅事项，并以三列卡片保留可处理差异', async ({ page }) => {
  test.setTimeout(120_000);
  await bootstrap(page, 1440);
  const mysql = await openCuratedReview(page);
  const github = await completeDatabaseAndOpenGithub(page, mysql);
  const matters = github.getByRole('region', { name: '审阅事项', exact: true });
  const suggestions = matters.getByRole('region', { name: '本来源建议', exact: true });
  const findings = matters.getByRole('region', { name: '来源差异与比较', exact: true });
  await expect(suggestions).toBeVisible();
  await expect(findings).toBeVisible();
  await expect(findings).toContainText('负库存配置');
  await expect(findings).toContainText('欠款字段');
  await expect(findings).toContainText('单据状态');
  await expect(findings.locator('[data-review-matter]')).toHaveCount(3);
  const debt = findings.locator('[data-review-matter="DEBT_FIELDS"]');
  await expect(debt).toContainText('请先核对本来源建议');
  await expect(debt.getByRole('button', { name: '处理该项', exact: true })).toHaveCount(0);
  // The review-document container, not the whole application viewport, owns
  // the 959px three-column breakpoint. Collapse the two existing peripheral
  // rails to verify the wide document state without changing page layout.
  await page.getByRole('button', { name: '收起来源资料', exact: true }).click();
  await page.getByRole('button', { name: /收起导航$/u }).click();
  const reviewWidth = await github.evaluate((element) => element.clientWidth);
  expect(reviewWidth).toBeGreaterThan(959);
  const gridColumns = await github.locator('[data-review-claim="claim:guanyijia_github:github-v5-github-v5-003"]')
    .evaluate((element) => getComputedStyle(element).gridTemplateColumns);
  expect(gridColumns.trim().split(/\s+/u)).toHaveLength(3);
  const statusMaterials = findings.locator('[data-review-matter="DOCUMENT_STATUS"]')
    .getByRole('button', { name: /^(查看|收起)双方资料$/u });
  await statusMaterials.click();
  await expect(statusMaterials).toHaveText('收起双方资料');
  await expect(findings.getByRole('heading', { name: '参与判断的来源材料', exact: true })).toBeVisible();

  await github.getByRole('tab', { name: '审阅结论', exact: true }).click();
  await expect(github.getByRole('region', { name: '来源差异与比较', exact: true })).toHaveCount(0);
  await expect(github.locator('.candidate-evidence-review')).toHaveCount(0);
});

test('GitHub 剧本修改确认后仍返回审阅事项，而不是旧版识别卡', async ({ page }) => {
  test.setTimeout(180_000);
  await bootstrap(page, 1440);
  const mysql = await openCuratedReview(page);
  const github = await completeDatabaseAndOpenGithub(page, mysql);
  await github.getByRole('button', { name: '采用推荐修改', exact: true }).click();
  const editor = github.getByRole('region', { name: /修改负库存控制候选/u });
  await expect(editor).toBeVisible();
  await editor.getByRole('button', { name: '预览修改', exact: true }).click();
  await editor.getByRole('button', { name: '确认修改', exact: true }).click();

  await expect(github.getByRole('region', { name: '审阅事项', exact: true })).toBeVisible();
  // The scripted task moves to 已处理事项, while running-source comparisons
  // remain in 待确认事项 rather than vanishing with the local suggestion.
  await expect(github.getByRole('heading', { name: '待确认事项', exact: true })).toBeVisible();
  await expect(github.getByRole('heading', { name: '已处理事项', exact: true })).toBeVisible();
  await expect(github.getByText('租户级负库存控制', { exact: true })).toBeVisible();
  await expect(github.getByRole('heading', { name: '识别结果', exact: true })).toHaveCount(0);
  await expect(github.getByText('证据等级', { exact: true })).toHaveCount(0);
});

test('GitHub 来源资料只说明本次保存的代码片段', async ({ page }) => {
  test.setTimeout(120_000);
  await bootstrap(page, 1440);
  const mysql = await openCuratedReview(page);
  const github = await completeDatabaseAndOpenGithub(page, mysql);
  const negativeStockTask = github.locator('article[data-review-claim="claim:guanyijia_github:github-v5-github-v5-003"]');
  await expect(negativeStockTask).toBeVisible();
  await negativeStockTask.getByRole('button', { name: '查看来源依据', exact: true }).click();
  await expect(negativeStockTask.getByRole('region', { name: '来源依据', exact: true })).toContainText('minus_stock_flag');
  await expect(github).toContainText('已保存源码片段');
  await expect(github).not.toContainText('完整源码');
  await expect(github).not.toContainText(/relativePath|objectType|objectName/u);
});
