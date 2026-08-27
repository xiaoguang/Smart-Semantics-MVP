import { expect, test } from '@playwright/test';
import {
  closeFormalCatalogBrowser,
  expectNoHorizontalOverflow,
  login,
  openStandardization,
  resetBrowserState,
  selectGuanyijia,
} from './cp8-helpers.ts';

async function openSourceBoard(page: import('@playwright/test').Page, width: number) {
  await page.setViewportSize({ width, height: 900 });
  await resetBrowserState(page);
  await login(page);
  await closeFormalCatalogBrowser(page);
  await selectGuanyijia(page);
  await openStandardization(page, 'DESKTOP');
  await page.getByRole('button', { name: '运行来源', exact: true }).click();
  const board = page.getByTestId('guanyijia-source-board');
  await expect(board).toBeVisible();
  return board;
}

test('运行前以固定角色、业务实例和连接器家族编排来源', async ({ page }) => {
  const board = await openSourceBoard(page, 1440);
  await expect(board.getByRole('heading', { name: '运行来源', exact: true })).toBeVisible();
  await expect(board.getByRole('tab', { name: '连接器', exact: true })).toBeVisible();
  await expect(board.getByRole('tab', { name: '实例', exact: true })).toBeVisible();
  await expect(board.locator('[data-source-role]:visible')).toHaveCount(5);
  await expect(board.locator('[data-source-role="DATABASE"]:visible')).toContainText('数据库');
  await expect(board.locator('[data-source-role="DATABASE"]:visible')).toContainText('待读取');
  await expect(board.locator('[data-source-role="DATABASE"]:visible')).not.toContainText('已连接');
  const officialDocumentsSlot = board.locator('[data-source-role="OFFICIAL_DOCUMENTS"]:visible');
  await expect(officialDocumentsSlot).toContainText('ERP业务规范库');
  await expect(officialDocumentsSlot).toContainText('Markdown文档集 · 3篇');
  await expect(officialDocumentsSlot).not.toContainText('SharePoint');

  const databaseSlot = board.locator('[data-source-role="DATABASE"]:visible');
  await databaseSlot.getByRole('button', { name: '移除', exact: true }).click();
  await expect(databaseSlot).toContainText('未连接');
  const databaseCandidate = board.locator('.source-instance-card').filter({ hasText: /^ERP交易库/u }).first();
  await databaseCandidate.getByRole('button', { name: '添加到数据库', exact: true }).click();
  await expect(databaseSlot).toContainText('待读取');

  // DnD and the explicit add action both reach the same BIND_INSTANCE command.
  await databaseSlot.getByRole('button', { name: '移除', exact: true }).click();
  const dragHandle = databaseCandidate.getByRole('button', { name: /拖拽 ERP交易库 到数据库/u });
  const [handleBox, slotBox] = await Promise.all([dragHandle.boundingBox(), databaseSlot.boundingBox()]);
  expect(handleBox).not.toBeNull();
  expect(slotBox).not.toBeNull();
  if (handleBox && slotBox) {
    await page.mouse.move(handleBox.x + handleBox.width / 2, handleBox.y + handleBox.height / 2);
    await page.mouse.down();
    await page.mouse.move(slotBox.x + slotBox.width / 2, slotBox.y + slotBox.height / 2, { steps: 8 });
    await page.mouse.up();
  }
  await expect(databaseSlot).toContainText('待读取');

  await board.getByRole('tab', { name: '连接器', exact: true }).click();
  const connector = board.getByRole('button', { name: /关系型数据库/u });
  await expect(connector).toHaveAttribute('draggable', 'false');
  await connector.click();
  const adapterPicker = page.getByRole('dialog', { name: '选择 关系型数据库技术', exact: true });
  await expect(adapterPicker).toBeVisible();
  await adapterPicker.getByRole('button', { name: 'MySQL', exact: true }).click();
  await expect(board.getByRole('heading', { name: '创建 MySQL 实例', exact: true })).toBeVisible();
  await expect(board.locator('.source-editor-panel')).not.toContainText('管伊佳');
});

test('320 窄屏在已选来源与待选来源之间切换且返回箭头不显示文字', async ({ page }) => {
  const board = await openSourceBoard(page, 320);
  await expect(board.getByRole('tab', { name: '已选来源', exact: true })).toBeVisible();
  await board.getByRole('tab', { name: '待选来源', exact: true }).click();
  await expect(board.getByRole('tab', { name: '实例', exact: true })).toBeVisible();
  const back = board.getByRole('button', { name: '返回标准化工作区', exact: true });
  await expect(back).toBeVisible();
  await expect(back).toHaveText('');
});

test('运行来源在代表视口保持对应布局且没有页面级横向溢出', async ({ page }) => {
  test.setTimeout(60_000);
  for (const width of [1024, 900, 768, 390]) {
    const board = await openSourceBoard(page, width);
    await expectNoHorizontalOverflow(page);
    if (width >= 900) {
      await expect(board.locator('[data-source-role]:visible')).toHaveCount(5);
      await expect(board.getByRole('tab', { name: '实例', exact: true })).toBeVisible();
    } else {
      await expect(board.getByRole('tab', { name: '已选来源', exact: true })).toBeVisible();
      await expect(board.getByRole('tab', { name: '待选来源', exact: true })).toBeVisible();
    }
  }
});
