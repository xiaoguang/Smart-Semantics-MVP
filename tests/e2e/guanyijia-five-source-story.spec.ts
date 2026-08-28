import { mkdirSync } from 'node:fs';
import { expect, test, type Locator, type Page } from '@playwright/test';
import {
  closeFormalCatalogBrowser,
  login,
  openStandardization,
  resetBrowserState,
  selectGuanyijia,
} from './cp8-helpers.ts';

const visualDirectory = 'artifacts/cp8-visual';
mkdirSync(visualDirectory, { recursive: true });

type ProtectedBaseline = {
  catalog: string;
  catalogCount: number;
  v2Count: number;
  draftCount: number;
};

async function capture(page: Page, name: string) {
  await page.screenshot({ path: `${visualDirectory}/${name}.png`, fullPage: false, animations: 'disabled' });
}

async function protectedBaseline(page: Page): Promise<ProtectedBaseline> {
  return page.evaluate(() => {
    const shared = JSON.parse(localStorage.getItem('linguan:collaboration:shared:v4') ?? '{}') as {
      catalogs?: Record<string, Array<{ catalogVersion?: string }>>;
    };
    const catalogs = shared.catalogs?.guanyijia_erp ?? [];
    const v1 = catalogs.find(({ catalogVersion }) => catalogVersion === 'V1');
    const draftCount = Object.keys(localStorage)
      .filter((key) => key.startsWith('linguan:collaboration:drafts:v4:'))
      .flatMap((key) => JSON.parse(localStorage.getItem(key) ?? '[]') as Array<{ modelSpaceId?: string }>)
      .filter(({ modelSpaceId }) => modelSpaceId === 'guanyijia_erp').length;
    return {
      catalog: JSON.stringify(v1),
      catalogCount: catalogs.length,
      v2Count: catalogs.filter(({ catalogVersion }) => catalogVersion === 'V2').length,
      draftCount,
    };
  });
}

async function ask(page: Page, message: string, expected: string | RegExp) {
  const panel = page.getByLabel('审阅助手');
  const composer = panel.getByPlaceholder(/问我当前结论/u);
  const historyItems = panel.locator('.guanyijia-review-assistant-history > article');
  const previousHistoryCount = await historyItems.count();
  await composer.fill(message);
  const send = panel.getByRole('button', { name: '发送给审阅助手' });
  await expect(send).toBeEnabled();
  await send.click();
  // History is intentionally collapsed by default. Open it after the new
  // response is committed so the assertions below verify the user-visible
  // conversation, rather than an unmounted history subtree.
  const historyToggle = panel.locator('.guanyijia-review-assistant-toggle');
  await expect(historyToggle).toBeVisible();
  if ((await historyToggle.textContent())?.trim() === '展开对话') {
    await historyToggle.click();
  }
  await expect(historyItems).toHaveCount(previousHistoryCount + 1);
  const normalizedMessage = message.normalize('NFKC').trim().replace(/\s+/gu, ' ');
  await expect(panel.locator('.guanyijia-review-assistant-question')
    .filter({ hasText: normalizedMessage }).last()).toBeVisible();
  await expect(panel.getByText(expected).last()).toBeVisible();
}

const sourceReviewTitles = {
  数据库: '数据库建模审阅（真实证据节选）',
  GitHub代码仓库: '代码仓库审阅',
  官方业务文档: '业务说明',
  'ERP管理制度（演示）': 'ERP管理制度',
  '术语图（派生）': '企业术语图',
} as const;

async function readSource(
  page: Page,
  sourceName: keyof typeof sourceReviewTitles,
  hasReviewMatters = true,
) {
  const heading = page.locator('header.guanyijia-document-review-header').getByRole('heading', {
    name: sourceReviewTitles[sourceName], exact: true,
  });
  await expect(heading).toBeVisible({ timeout: 30_000 });
  const document = heading.locator('xpath=ancestor::section[contains(@class,"guanyijia-document-review")]');
  await expect(document).toBeVisible();
  await expect(document.getByRole('tab', { name: '审阅事项', exact: true })).toHaveAttribute('aria-selected', 'true');
  if (hasReviewMatters) {
    await expect(document.getByRole('region', { name: '审阅事项', exact: true })).toBeVisible();
  }
  return document;
}

async function completeSourceReview(document: Locator, hasNextSource: boolean) {
  await document.getByRole('button', { name: /^完成.+审阅$/u }).click();
  await expect(document).toBeVisible();
  if (hasNextSource) {
    await expect(document.getByRole('button', { name: '审阅下一个来源', exact: true })).toBeVisible();
  } else {
    await expect(document.getByRole('button', { name: '审阅下一个来源', exact: true })).toHaveCount(0);
  }
}

async function readNextSource(document: Locator) {
  await document.getByRole('button', { name: '审阅下一个来源', exact: true }).click();
}

async function resolveConflict(
  page: Page,
  title: string,
  strategy: string,
) {
  const conflict = page.getByLabel('当前来源差异');
  await expect(conflict).toBeVisible();
  await expect(conflict.getByRole('heading', { name: title })).toBeVisible();
  await expect(conflict).toContainText('当前工作标准');
  await expect(conflict).toContainText('新来源结论');
  await expect(conflict.getByLabel('中文决定理由')).toHaveCount(0);
  const radios = conflict.getByRole('radio');
  const selectedRadio = conflict.getByRole('radio', { name: strategy });
  await selectedRadio.focus();
  await selectedRadio.press('Space');
  await expect(selectedRadio).toHaveAttribute('aria-checked', 'true');
  const selectedIndex = await radios.evaluateAll((elements, selectedName) => elements.findIndex(
    (element) => element.textContent?.trim() === selectedName,
  ), strategy);
  const nextRadio = radios.nth((selectedIndex + 1) % await radios.count());
  await selectedRadio.press('ArrowRight');
  await expect(nextRadio).toBeFocused();
  await nextRadio.press('ArrowLeft');
  await expect(selectedRadio).toBeFocused();
  await selectedRadio.press('Enter');
  await expect(selectedRadio).toHaveAttribute('aria-checked', 'true');
  await expect(conflict.getByLabel('策略结果')).toContainText('决定后的结果');
  await conflict.getByRole('button', { name: '保存当前决定' }).click();
}

test('唯一连续五源故事：revision、冲突、助手、作者定版与标准化文档交接', async ({ page }) => {
  test.setTimeout(240_000);
  const pageErrors: string[] = [];
  const consoleErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });
  await page.clock.install({ time: new Date('2026-08-18T09:00:00+08:00') });
  await page.clock.setFixedTime(new Date('2026-08-18T09:00:00+08:00'));
  await page.setViewportSize({ width: 1440, height: 1000 });
  await resetBrowserState(page);
  await login(page);
  await closeFormalCatalogBrowser(page);
  await selectGuanyijia(page);
  const before = await protectedBaseline(page);
  expect(before).toMatchObject({ catalogCount: 1, v2Count: 0, draftCount: 0 });
  expect(before.catalog).toContain('catalog_guanyijia_v1');
  expect(before.catalog).toContain('guanyijia:5852f56cb63073b4978f0cb168afb34a8832a1c484454db9542dde1d3379c210');
  await openStandardization(page, 'DESKTOP');

  await page.getByRole('button', { name: '开始资料整理' }).click();
  const mysql = await readSource(page, '数据库');
  // The database document is a Claim-driven review surface. Only its one
  // explicitly mapped script can change the current review revision.
  await expect(mysql).toContainText('30 / 95 张表');
  await expect(mysql.getByRole('tab', { name: '审阅事项' })).toBeVisible();
  await expect(mysql.getByRole('tab', { name: '审阅结论' })).toBeVisible();
  await expect(mysql.getByRole('tab', { name: '标准化文档' })).toBeVisible();
  await expect(mysql.getByRole('tab', { name: '依据追踪' })).toHaveCount(0);
  await expect(mysql.getByRole('button', { name: '修改识别结论' })).toHaveCount(0);
  await mysql.getByRole('tab', { name: '审阅结论', exact: true }).click();
  await expect(mysql).toContainText('账户主数据（jsh_account）');
  await mysql.getByRole('tab', { name: '审阅事项', exact: true }).click();
  await mysql.getByRole('button', { name: '保留当前结论', exact: true }).click();
  await completeSourceReview(mysql, true);
  await readNextSource(mysql);

  const github = await readSource(page, 'GitHub代码仓库');
  const gapFindings = github.getByRole('region', { name: '审阅事项', exact: true })
    .getByRole('region', { name: '本次读取发现', exact: true });
  await expect(gapFindings).toBeVisible();
  await expect(gapFindings).toContainText('不同版本记录不一致');
  await github.getByRole('tab', { name: '审阅结论', exact: true }).click();
  const findings = github.getByRole('region', { name: '本次读取发现', exact: true });
  await expect(findings).toBeVisible();
  await expect(github).toContainText('互补资料');
  await expect(github).toContainText('结构差异');
  await github.getByRole('tab', { name: '审阅事项', exact: true }).click();
  const githubBlock = github.getByRole('region', { name: '审阅事项', exact: true })
    .locator('article').filter({ hasText: '负库存' });
  await expect(githubBlock).toBeVisible();
  await githubBlock.getByRole('button', { name: '采用推荐修改' }).click();
  const editor = github.getByRole('region', { name: '修改负库存控制候选', exact: true });
  const originalLabel = await editor.getByLabel('结论名称').inputValue();
  const revisedLabel = `${originalLabel}（人工确认）`;
  await editor.getByLabel('结论名称').fill(revisedLabel);
  await editor.getByLabel('业务说明').fill('源码按租户配置项决定是否允许负库存；保留该实现事实并等待制度核对。');
  await editor.getByRole('button', { name: '预览修改' }).click();
  const preview = editor.getByLabel('修改预览');
  await expect(preview).toContainText('业务名称 · 修改前');
  await expect(preview).toContainText('业务名称 · 修改后');
  await expect(preview).toContainText('Markdown ·');
  await expect(preview).toContainText(revisedLabel);
  await capture(page, '07-five-source-github-structured-preview');
  await editor.getByRole('button', { name: '确认修改' }).click();
  await expect(github).toContainText(revisedLabel);
  await expect(page.locator('.guanyijia-review-live-region')).toHaveText('修改已确认，审阅事项、审阅结论和标准化文档已同步更新。');
  const persistedSurface = await page.evaluate(() => {
    const key = Object.keys(sessionStorage).find((candidate) => candidate.startsWith('linguan-review-surface-v1:'));
    return key ? JSON.parse(sessionStorage.getItem(key) ?? '{}') as {
      runRevision?: number;
      layers?: Array<{ kind?: string; stableId?: string }>;
    } : undefined;
  });
  expect(persistedSurface?.runRevision).toBeGreaterThan(0);
  expect(persistedSurface?.layers?.at(-1)).toMatchObject({ kind: 'DOCUMENT' });

  await page.reload();
  await openStandardization(page, 'DESKTOP');
  const restoredGithub = page.locator('section.guanyijia-document-review').last();
  await expect(restoredGithub).toBeVisible();
  await expect(restoredGithub.getByRole('tab', { name: '审阅事项', exact: true })).toHaveAttribute('aria-selected', 'true');
  await restoredGithub.getByRole('button', { name: '查看历史版本' }).click();
  const history = restoredGithub.getByLabel('历史版本');
  await expect(history).toContainText('第 1 版');
  await expect(history).toContainText('第 2 版');
  await expect(history).toContainText('历史版本');
  await expect(restoredGithub.getByRole('tab', { name: '审阅事项', exact: true })).toBeVisible();
  await expect(restoredGithub).toContainText(revisedLabel);
  await expect(restoredGithub.getByRole('button', { name: '采用推荐修改', exact: true })).toHaveCount(0);

  // The fixed story deliberately has no generic free editor. The scripted
  // revision is the only supported source-document change in this demo.
  await expect(restoredGithub.getByRole('button', { name: '修改识别结论' })).toHaveCount(0);
  await completeSourceReview(restoredGithub, true);
  await readNextSource(restoredGithub);

  const official = await readSource(page, '官方业务文档');
  await completeSourceReview(official, true);
  await readNextSource(official);

  const policy = await readSource(page, 'ERP管理制度（演示）');
  await completeSourceReview(policy, true);
  await readNextSource(policy);

  const semantica = await readSource(page, '术语图（派生）', false);
  await completeSourceReview(semantica, false);
  await semantica.getByRole('button', { name: '返回时间线', exact: true }).click();

  await ask(page, '当前差异是什么', '欠款字段结构冲突');
  await ask(page, '这项依据是什么？', /依据/u);
  await ask(page, '影响哪些对象？', /影响对象/u);
  await page.getByLabel('当前来源差异').scrollIntoViewIfNeeded();
  await capture(page, '09-five-source-debt-hunk');
  await resolveConflict(page, '欠款字段结构冲突', '保留当前结论');
  await page.getByLabel('当前来源差异').scrollIntoViewIfNeeded();
  await capture(page, '10-five-source-negative-stock-hunk');
  await resolveConflict(page, '负库存制度与实现冲突', '登记为缺口');
  await expect(page.getByLabel('当前来源差异')).toContainText('状态 9 业务含义冲突');
  await page.getByLabel('当前来源差异').scrollIntoViewIfNeeded();
  await capture(page, '11-five-source-status-nine-hunk');
  await resolveConflict(page, '状态 9 业务含义冲突', '登记为缺口');

  await expect(page.getByRole('button', { name: '生成标准化结果' })).toBeVisible();
  await capture(page, '12-five-source-ready-for-deliverable');
  await page.getByRole('button', { name: '生成标准化结果' }).click();
  await expect(page.getByRole('button', { name: '确认结果并定版' })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole('button', { name: '提交独立审核' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '通过审核并定版' })).toHaveCount(0);
  await expect(page.getByText('完整文档保留所有结论和缺口；AI 建模只读取可用于建模的已确认结论。')).toBeVisible();
  await expect(page.getByRole('button', { name: /查看(合并文档|审阅决定|证据附录|模型差异)/u })).toHaveCount(0);
  await expect(page.locator('body')).not.toContainText(/gyj-conflict-debt-schema|formalRootSourceIds|semanticDifferences|sha256:/u);
  await capture(page, '13-five-source-deliverable-content');
  await page.getByRole('button', { name: '确认结果并定版' }).click();
  const deliverableWorkspace = page.locator('.guanyijia-deliverable-workspace');
  await expect(deliverableWorkspace.getByText('标准化结果已定版')).toBeVisible({ timeout: 30_000 });
  await expect(deliverableWorkspace.getByText(/审批意见|独立审核|审核人/u)).toHaveCount(0);
  await expect(page.getByRole('button', { name: '前往 AI 建模' })).toBeVisible();
  await expect(page.getByLabel('技术详情')).toHaveCount(0);
  await capture(page, '14-five-source-author-frozen');

  await page.getByRole('button', { name: '前往 AI 建模' }).click();
  const handoff = page.locator('.modeling-document-handoff');
  await expect(handoff).toBeVisible({ timeout: 30_000 });
  await expect(handoff).toContainText(/项结论可用于建模/u);
  await expect(handoff).toContainText(/项资料缺口或无法确定内容仅保留在文档中/u);
  await expect(handoff.getByRole('button', { name: '生成建模候选' })).toBeVisible();
  await expect(handoff).not.toContainText(/sha256|[a-f0-9]{32,}/iu);
  await capture(page, '16-five-source-standardization-handoff');

  const after = await protectedBaseline(page);
  expect(after).toEqual(before);
  expect(pageErrors).toEqual([]);
  expect(consoleErrors).toEqual([]);
});
