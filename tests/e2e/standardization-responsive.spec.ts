import AxeBuilder from '@axe-core/playwright';
import { mkdirSync } from 'node:fs';
import { expect, test, type Locator, type Page } from '@playwright/test';
import {
  closeFormalCatalogBrowser,
  enterMysqlDocument,
  expectNoHorizontalOverflow,
  expectSingleWorkflowPrimary,
  expectTouchTargets,
  login,
  openStandardization,
  resetBrowserState,
  selectGuanyijia,
} from './cp8-helpers.ts';

const visualDirectory = 'artifacts/cp8-visual';
mkdirSync(visualDirectory, { recursive: true });

async function bootstrap(page: Page, viewport: { width: number; height: number }, navigation: 'DESKTOP' | 'MOBILE') {
  await page.clock.setFixedTime(new Date('2026-08-18T12:00:00.000Z'));
  await page.setViewportSize(viewport);
  await resetBrowserState(page);
  await login(page);
  await closeFormalCatalogBrowser(page);
  await selectGuanyijia(page);
  await openStandardization(page, navigation);
}

async function capture(page: Page, name: string) {
  await page.screenshot({ path: `${visualDirectory}/${name}.png`, fullPage: false, animations: 'disabled' });
}

async function expectNoSeriousAxe(page: Page) {
  const result = await new AxeBuilder({ page }).include('.guanyijia-workbench-shell').analyze();
  expect(result.violations.filter(({ impact }) => impact === 'serious' || impact === 'critical')).toEqual([]);
}

async function expectReviewRowsAccessible(page: Page, inspectorOverlay = false) {
  let workflowRoot: Locator = page;
  if (inspectorOverlay) {
    // Starting the run automatically opens the first source document.  Wait
    // for that run transition to settle before opening its medium-width
    // sources-and-workflow drawer; otherwise the new run revision correctly
    // invalidates a drawer that was opened against the prior revision.
    const document = page.locator('section.guanyijia-document-review').last();
    await expect(document).toBeVisible({ timeout: 30_000 });
    // The source panel has one global toolbar trigger. It is deliberately
    // outside the document region, so do not scope the lookup to the inner
    // document section (that legacy trigger no longer exists).
    const sourceMaterials = page.locator('#guanyijia-inspector-trigger');
    await expect(sourceMaterials).toHaveCount(1);
    await expect(sourceMaterials).toBeVisible();
    await sourceMaterials.click();
    workflowRoot = page.getByRole('dialog', { name: '来源资料', exact: true });
    await expect(workflowRoot).toBeVisible();
  }
  const timeline = workflowRoot.getByRole('list', { name: '标准化运行时间线' });
  const rows = timeline.getByRole('listitem');
  const total = await rows.first().getAttribute('aria-setsize');
  expect(Number(total)).toBeGreaterThanOrEqual(await rows.count());
  await expect(rows.first()).toHaveAttribute('aria-posinset', '1');
  if (inspectorOverlay) await page.keyboard.press('Escape');
}

async function openDocumentAndExerciseAssistant(
  page: Page,
  mobile: boolean,
  inspectorOverlay = mobile,
) {
  const document = page.locator('section.guanyijia-document-review').last();
  await expect(document).toBeVisible({ timeout: 30_000 });
  if (mobile) {
    // The fullscreen document surface owns the modal semantics. The inner
    // document section remains a normal document region so the composer and
    // review content stay interactive within the same surface.
    const documentLayer = page.getByRole('dialog', { name: '当前来源文档' });
    await expect(documentLayer).toBeVisible();
    await expect(documentLayer).toHaveAttribute('aria-modal', 'true');
    await expect(document).not.toHaveAttribute('role', 'dialog');
  } else {
    await expect(document).not.toHaveAttribute('role', 'dialog');
  }
  await expect(document.getByRole('tab', { name: '审阅清单', exact: true })).toHaveAttribute('aria-selected', 'true');
  const evidenceTrigger = document.getByRole('region', { name: '审阅清单' })
    .getByRole('button', { name: '查看来源依据', exact: true }).first();
  await evidenceTrigger.click();
  await expect(document.getByRole('region', { name: '审阅清单' }).getByRole('region', { name: '来源依据', exact: true }).first()).toBeVisible();
  // At mobile widths the document is the sole fullscreen surface. Its
  // sibling source-panel trigger is intentionally outside that modal and is
  // not part of this document interaction. Desktop widths exercise the
  // inline/overlay source panel separately below.
  if (!mobile) {
    const sourceMaterials = page.locator('#guanyijia-inspector-trigger');
    if (inspectorOverlay) await sourceMaterials.click();
    const inspector = inspectorOverlay
      ? page.getByRole('dialog', { name: '来源资料', exact: true })
      : page.locator('aside[aria-label="来源资料"]');
    await expect(inspector).toBeVisible();
    await expect(inspector.locator('[data-workflow-primary="true"]')).toHaveCount(0);
    if (inspectorOverlay) {
      await expect(inspector).toHaveRole('dialog');
      await page.keyboard.press('Escape');
    } else {
      await expect(inspector).not.toHaveAttribute('role', 'dialog');
    }
  }

  // At mobile widths the document dialog owns the assistant and composer;
  // scope the lookup to that same fullscreen surface so this test cannot
  // accidentally pass against a detached/global assistant instance.
  const assistantSurface = mobile
    ? page.getByRole('dialog', { name: '当前来源文档' })
    : page;
  const assistant = assistantSurface.getByLabel('审阅助手', { exact: true });
  const composer = assistant.getByPlaceholder(/问我当前结论/u);
  await expect(composer).toBeVisible();
  await composer.fill('当前来源的快照范围是什么？');
  await assistant.getByRole('button', { name: '发送给审阅助手' }).click();
  // Assistant history is collapsed by default. Expand it after the answer
  // has been committed so the assertion observes the user-visible response.
  const historyToggle = assistant.locator('.guanyijia-review-assistant-toggle');
  await expect(historyToggle).toBeVisible();
  if ((await historyToggle.textContent())?.trim() === '展开对话') {
    await historyToggle.click();
  }
  const answer = assistant.locator('.guanyijia-review-assistant-answer').last();
  await expect(answer).toBeVisible();
  await expect(answer).toContainText('数据库');
  if (mobile) {
    // The assistant is embedded in the document's sole fullscreen layer. It
    // must not become a second competing dialog or a detached fixed companion.
    await expect(assistant).toHaveRole('region');
    await expect(assistant).not.toHaveAttribute('inert');
  } else {
    await expect(assistant).not.toHaveAttribute('role', 'dialog');
  }
  await expect(page.locator('.guanyijia-review-assistant-panel')).toHaveCount(1);
  expect(await assistant.evaluate((element) => window.getComputedStyle(element).position)).not.toMatch(/fixed|sticky/u);
  return document;
}

async function finishConflict(page: Page, mobile: boolean, title: string, strategy: string) {
  const conflict = page.getByLabel('当前来源差异');
  await expect(conflict).toBeVisible();
  await expect(conflict.getByRole('heading', { name: title })).toBeVisible();
  if (mobile) await expect(conflict.locator('xpath=..')).toHaveRole('dialog');
  await expect(conflict.getByRole('radio')).not.toHaveCount(0);
  const selected = conflict.getByRole('radio', { name: strategy });
  await selected.click();
  await expect(selected).toHaveAttribute('aria-checked', 'true');
  await expect(conflict.getByLabel('中文决定理由')).toHaveCount(0);
  await conflict.getByRole('button', { name: '保存当前决定' }).click();
}

const sourceReviewTitles = {
  GitHub代码仓库: '代码仓库审阅',
  官方业务文档: '业务说明',
  'ERP管理制度（演示）': 'ERP管理制度',
  '术语图（派生）': '企业术语图',
} as const;

async function readAndReviewSource(page: Page, sourceName: keyof typeof sourceReviewTitles, mobile: boolean) {
  const heading = page.locator('header.guanyijia-document-review-header').getByRole('heading', {
    name: sourceReviewTitles[sourceName], exact: true,
  }).last();
  await expect(heading).toBeVisible({ timeout: 30_000 });
  const document = heading.locator('xpath=ancestor::section[contains(@class,"guanyijia-document-review")]');
  if (mobile) {
    const documentLayer = page.getByRole('dialog', { name: '当前来源文档' });
    await expect(documentLayer).toBeVisible();
    await expect(documentLayer).toHaveAttribute('aria-modal', 'true');
    await expect(document).not.toHaveAttribute('role', 'dialog');
  }
  await expect(document.getByRole('tab', { name: '审阅清单', exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(document.getByRole('tab', { name: '标准化文档', exact: true })).toBeVisible();
  await expect(document.getByRole('tab', { name: '依据追踪', exact: true })).toHaveCount(0);
  const retainCurrent = document.getByRole('button', { name: '保留当前结论', exact: true });
  if (await retainCurrent.count()) await retainCurrent.click();
  await document.getByRole('button', { name: /^完成.+审阅$/u }).click();
  await expect(document).toBeHidden();
}

async function retainMysqlScriptedReview(document: Locator) {
  const retainCurrent = document.getByRole('button', { name: '保留当前结论', exact: true });
  await expect(retainCurrent).toBeVisible();
  await retainCurrent.click();
  await expect(document.getByRole('button', { name: '完成数据库审阅' })).toBeVisible();
}

async function enterGithubDocument(page: Page) {
  await enterMysqlDocument(page);
  const mysql = page.locator('section.guanyijia-document-review').last();
  await retainMysqlScriptedReview(mysql);
  await mysql.getByRole('button', { name: '完成数据库审阅' }).click();
  const github = page.locator('section.guanyijia-document-review').last();
  await expect(github.getByRole('heading', {
    name: sourceReviewTitles.GitHub代码仓库,
    exact: true,
  })).toBeVisible();
  return github;
}

async function exerciseCompleteStateMatrix(
  page: Page,
  mobile: boolean,
  navigation: 'DESKTOP' | 'MOBILE',
  inspectorOverlay: boolean,
) {
  await page.getByRole('button', { name: '开始资料整理' }).click();
  if (!mobile) await expectReviewRowsAccessible(page, inspectorOverlay);
  const mysql = await openDocumentAndExerciseAssistant(page, mobile, inspectorOverlay);
  await expectNoHorizontalOverflow(page);
  await expectSingleWorkflowPrimary(page);
  await retainMysqlScriptedReview(mysql);
  await mysql.getByRole('button', { name: '完成数据库审阅' }).click();
  await expect(mysql).toBeHidden();

  await readAndReviewSource(page, 'GitHub代码仓库', mobile);
  await finishConflict(page, mobile, '欠款字段结构冲突', '保留当前结论');
  await readAndReviewSource(page, '官方业务文档', mobile);
  await readAndReviewSource(page, 'ERP管理制度（演示）', mobile);
  await finishConflict(page, mobile, '负库存制度与实现冲突', '登记为缺口');
  await finishConflict(page, mobile, '状态 9 业务含义冲突', '登记为缺口');
  await readAndReviewSource(page, '术语图（派生）', mobile);

  const deliverable = page.getByLabel('标准化结果定版');
  await expect(deliverable).toBeVisible();
  if (mobile) await expect(deliverable).toHaveRole('dialog');
  await deliverable.getByRole('button', { name: '生成标准化结果' }).click();
  await expect(deliverable.getByRole('button', { name: '确认结果并定版' })).toBeVisible();
  await expect(deliverable.getByRole('button', { name: '提交独立审核' })).toHaveCount(0);
  await expectNoHorizontalOverflow(page);
  await expectSingleWorkflowPrimary(page);
  await deliverable.getByRole('button', { name: '确认结果并定版' }).click();
  await expect(deliverable.getByText('标准化结果已定版')).toBeVisible();
  await expect(deliverable.getByText(/审批意见|独立审核|审核人/u)).toHaveCount(0);
  await expectNoHorizontalOverflow(page);
  await expectNoSeriousAxe(page);
}

test('1440 inline Inspector、页内助手与文档保持唯一主操作', async ({ page }) => {
  await bootstrap(page, { width: 1440, height: 1000 }, 'DESKTOP');
  const inspector = page.getByRole('complementary', { name: '来源资料', exact: true });
  await expect(inspector).toBeVisible();
  await expect(inspector).not.toHaveAttribute('role', 'dialog');
  await enterMysqlDocument(page);
  await expect(page.locator('section.guanyijia-document-review').last()).not.toHaveAttribute('role', 'dialog');
  const assistant = page.getByLabel('审阅助手', { exact: true });
  await expect(assistant).toBeVisible();
  expect(await assistant.evaluate((element) => window.getComputedStyle(element).position)).not.toMatch(/fixed|sticky/u);
  await expect(page.locator('.guanyijia-review-assistant-panel')).toHaveCount(1);
  await expectSingleWorkflowPrimary(page);
  await expectNoHorizontalOverflow(page);
  await expectNoSeriousAxe(page);
  await capture(page, '01-1440-document-inline-inspector');
});

test('1024 Inspector overlay trap focus、Escape逆序恢复anchor', async ({ page }) => {
  await bootstrap(page, { width: 1024, height: 900 }, 'DESKTOP');
  await enterMysqlDocument(page);
  const evidenceTrigger = page.getByRole('region', { name: '审阅清单' }).locator('button[data-review-focus^="curated-evidence:"]').first();
  await evidenceTrigger.click();
  await expect(page.getByRole('region', { name: '审阅清单' }).getByRole('region', { name: '来源依据', exact: true }).first()).toBeVisible();
  // The source panel is controlled by the single global toolbar toggle. It
  // is intentionally not duplicated inside the current-document surface.
  const documentSourceMaterials = page.locator('#guanyijia-inspector-trigger');
  await expect(documentSourceMaterials).toHaveCount(1);
  await expect(documentSourceMaterials).toBeVisible();
  await expect(documentSourceMaterials).toHaveAccessibleName('来源资料');
  await documentSourceMaterials.click();
  const inspector = page.getByRole('dialog', { name: '来源资料' });
  await expect(inspector).toBeVisible();
  await expect(inspector).toHaveAttribute('aria-modal', 'true');
  await expect(page.locator('body')).toHaveClass(/standardization-overlay-active/u);
  await expect(page.locator('.app-sider')).toHaveAttribute('inert', '');
  await expect(page.locator('.app-header')).toHaveAttribute('inert', '');
  await page.keyboard.press('Tab');
  await expect(inspector.locator(':focus')).toHaveCount(1);
  await page.keyboard.press('Shift+Tab');
  await expect(inspector.locator(':focus')).toHaveCount(1);
  await page.keyboard.press('Escape');
  await expect(inspector).toBeHidden();
  // The visible label changes from “查看来源依据” to “收起来源依据” while
  // its material is open. Verify the original control by its stable review
  // focus anchor, rather than resolving a new text-labelled button.
  await expect(documentSourceMaterials).toBeFocused();
  await expectSingleWorkflowPrimary(page);
  await capture(page, '02-1024-inspector-overlay-closed');
});

test('1280 本次读取发现以可见的扁平列表呈现，不依赖横向滚动', async ({ page }) => {
  await bootstrap(page, { width: 1280, height: 900 }, 'DESKTOP');
  const github = await enterGithubDocument(page);
  const findings = github.getByRole('region', { name: '本次读取发现', exact: true });
  const list = findings.getByRole('list', { name: '跨来源发现', exact: true });
  await expect(list).toBeVisible();
  await expect(list.getByRole('listitem')).toHaveCount(3);
  // At 1280px the component may use its desktop table rather than the
  // compact list.  The visible controller is the contract, not a hidden
  // alternate layout branch.
  const buttons = findings.locator('button[aria-controls^="finding-materials:"]:visible');
  await expect(buttons).toHaveCount(3);
  for (const button of await buttons.all()) {
    await button.scrollIntoViewIfNeeded();
    await expect(button).toBeInViewport();
    const bounds = await button.boundingBox();
    expect(bounds).not.toBeNull();
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(1280);
  }
  const overflow = await findings.evaluate((element) => ({
    scrollWidth: element.scrollWidth,
    clientWidth: element.clientWidth,
  }));
  expect(overflow.scrollWidth).toBeLessThanOrEqual(overflow.clientWidth);
});

test('1440 本次读取发现按实际主列宽度选择一种完整可读的布局', async ({ page }) => {
  await bootstrap(page, { width: 1440, height: 1000 }, 'DESKTOP');
  const github = await enterGithubDocument(page);
  const findings = github.getByRole('region', { name: '本次读取发现', exact: true });
  const table = findings.getByRole('table');
  const list = findings.getByRole('list', { name: '跨来源发现', exact: true });
  const width = await findings.evaluate((element) => element.clientWidth);
  if (width > 1055) {
    await expect(table).toBeVisible();
    await expect(list).toBeHidden();
  } else {
    await expect(table).toBeHidden();
    await expect(list).toBeVisible();
  }
});

test('冲突决定切换保持位置并显示当前选择的业务 Git 预览', async ({ page }) => {
  await bootstrap(page, { width: 1440, height: 1000 }, 'DESKTOP');
  const github = await enterGithubDocument(page);
  const retainCurrent = github.getByRole('button', { name: '保留当前结论', exact: true });
  if (await retainCurrent.count()) await retainCurrent.click();
  await github.getByRole('button', { name: /^完成.+审阅$/u }).click();
  const conflict = page.getByLabel('当前来源差异');
  await expect(conflict.getByRole('heading', { name: '欠款字段结构冲突', exact: true })).toBeVisible();
  const scroll = page.locator('.guanyijia-workbench-scroll');
  const decisions = conflict.getByRole('radiogroup', { name: '冲突解决策略' });
  await decisions.scrollIntoViewIfNeeded();

  const switchDecision = async (label: string, expected: RegExp) => {
    const before = await Promise.all([
      scroll.evaluate((element) => element.scrollTop),
      decisions.evaluate((element) => element.getBoundingClientRect().top),
    ]);
    await conflict.getByRole('radio', { name: label, exact: true }).click();
    const preview = conflict.getByLabel('Markdown 决定预览', { exact: true });
    await expect(preview).toContainText(expected);
    const after = await Promise.all([
      scroll.evaluate((element) => element.scrollTop),
      decisions.evaluate((element) => element.getBoundingClientRect().top),
    ]);
    expect(Math.abs(after[0] - before[0])).toBeLessThanOrEqual(1);
    expect(Math.abs(after[1] - before[1])).toBeLessThanOrEqual(1);
  };

  await switchDecision('保留当前结论', /部署欠款字段基准/u);
  await switchDecision('采用新来源结论', /源码欠款字段定义/u);
  await switchDecision('登记为缺口', /待确认缺口/u);
});

test('768 Document 是100dvh全屏dialog且底层inert', async ({ page }) => {
  await bootstrap(page, { width: 768, height: 1024 }, 'DESKTOP');
  await enterMysqlDocument(page);
  const documentLayer = page.getByRole('dialog', { name: '当前来源文档' });
  await expect(documentLayer).toBeVisible();
  await expect(documentLayer).toHaveAttribute('aria-modal', 'true');
  const bounds = await documentLayer.boundingBox();
  expect(bounds?.width).toBeCloseTo(768, 0);
  expect(bounds?.height).toBeCloseTo(1024, 0);
  await expect(page.locator('.guanyijia-timeline')).toHaveAttribute('inert', '');
  await expectNoHorizontalOverflow(page);
  await capture(page, '03-768-document-fullscreen');
});

test('390 手机单列、44px触控、safe-area与composer不遮挡正文', async ({ page }) => {
  await bootstrap(page, { width: 390, height: 844 }, 'MOBILE');
  await enterMysqlDocument(page);
  const documentLayer = page.getByRole('dialog', { name: '当前来源文档' });
  await expect(documentLayer).toBeVisible();
  const assistant = documentLayer.getByLabel('审阅助手', { exact: true });
  await expect(assistant).not.toHaveAttribute('inert', '');
  const composer = assistant.locator('textarea');
  await composer.fill('移动端助手仍可与文档协同审阅');
  await expect(composer).toBeFocused();
  await expect(page.locator('.app-header')).toHaveAttribute('inert', '');
  await expect(page.locator('.mobile-bottom-nav')).toHaveAttribute('inert', '');
  await page.locator('.mobile-bottom-nav button').first().evaluate((element) => element.focus());
  await expect(composer).toBeFocused();
  await expectTouchTargets(page);
  await expectNoHorizontalOverflow(page);
  const documentBottom = await page.getByRole('dialog', { name: '当前来源文档' }).evaluate((element) => (
    element.getBoundingClientRect().bottom
  ));
  expect(documentBottom).toBeLessThanOrEqual(844.5);
  await capture(page, '04-390-document-safe-area');
});

test('320 技术ID局部换行且页面无横向溢出', async ({ page }) => {
  await bootstrap(page, { width: 320, height: 720 }, 'MOBILE');
  await enterMysqlDocument(page);
  await expectNoHorizontalOverflow(page);
  await expectTouchTargets(page);
  await capture(page, '05-320-document-technical-wrap');
});

test('720×500 lockfile Chromium 视觉快照保持full-screen合同', async ({ page, browserName }) => {
  expect(browserName).toBe('chromium');
  await bootstrap(page, { width: 720, height: 500 }, 'DESKTOP');
  await enterMysqlDocument(page);
  const document = page.getByRole('dialog', { name: '当前来源文档' });
  const bounds = await document.boundingBox();
  expect(bounds?.width).toBeCloseTo(720, 0);
  expect(bounds?.height).toBeCloseTo(500, 0);
  await expectNoHorizontalOverflow(page);
  await capture(page, '06-720x500-lockfile-chromium');
});

test('390 手机覆盖时间线、文档、冲突、助手Patch、交付物与定版状态', async ({ page }) => {
  test.setTimeout(120_000);
  await bootstrap(page, { width: 390, height: 844 }, 'MOBILE');
  await page.getByRole('button', { name: '开始资料整理' }).click();
  await expectSingleWorkflowPrimary(page);
  const mysql = await openDocumentAndExerciseAssistant(page, true);
  await retainMysqlScriptedReview(mysql);
  await mysql.getByRole('button', { name: '完成数据库审阅' }).click();
  await expect(mysql).toBeHidden();

  await readAndReviewSource(page, 'GitHub代码仓库', true);
  await finishConflict(page, true, '欠款字段结构冲突', '保留当前结论');
  await readAndReviewSource(page, '官方业务文档', true);
  await readAndReviewSource(page, 'ERP管理制度（演示）', true);
  await finishConflict(page, true, '负库存制度与实现冲突', '登记为缺口');
  await finishConflict(page, true, '状态 9 业务含义冲突', '登记为缺口');
  await readAndReviewSource(page, '术语图（派生）', true);
  const deliverable = page.getByRole('dialog', { name: '标准化结果定版' });
  await expect(deliverable).toBeVisible();
  await deliverable.getByRole('button', { name: '生成标准化结果' }).click();
  await expect(deliverable.getByRole('button', { name: '确认结果并定版' })).toBeVisible();
  await expect(deliverable.locator('[data-workflow-primary="true"]')).toHaveCount(1);
  await deliverable.getByRole('button', { name: '确认结果并定版' }).click();
  await expect(deliverable.getByText('标准化结果已定版')).toBeVisible();
  await deliverable.getByRole('button', { name: '返回时间线' }).click();
  await expect(deliverable).toBeHidden();
  await expectNoHorizontalOverflow(page);
  await expectTouchTargets(page);
  await expectNoSeriousAxe(page);
});

for (const matrix of [
  { name: '1440', viewport: { width: 1440, height: 1000 }, navigation: 'DESKTOP' as const, mobile: false, inspectorOverlay: false },
  { name: '1024', viewport: { width: 1024, height: 900 }, navigation: 'DESKTOP' as const, mobile: false, inspectorOverlay: true },
  { name: '768', viewport: { width: 768, height: 1024 }, navigation: 'DESKTOP' as const, mobile: true, inspectorOverlay: true },
  { name: '390', viewport: { width: 390, height: 844 }, navigation: 'MOBILE' as const, mobile: true, inspectorOverlay: true },
  { name: '320', viewport: { width: 320, height: 720 }, navigation: 'MOBILE' as const, mobile: true, inspectorOverlay: true },
  { name: '720×500', viewport: { width: 720, height: 500 }, navigation: 'DESKTOP' as const, mobile: true, inspectorOverlay: true },
]) {
  test(`${matrix.name} 独立上下文覆盖完整审阅状态矩阵`, async ({ page }) => {
    test.setTimeout(120_000);
    await bootstrap(page, matrix.viewport, matrix.navigation);
    await exerciseCompleteStateMatrix(page, matrix.mobile, matrix.navigation, matrix.inspectorOverlay);
  });
}
