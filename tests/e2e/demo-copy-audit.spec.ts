import { expect, test, type Page } from '@playwright/test';
import { closeFormalCatalogBrowser, login, openStandardization, resetBrowserState, selectGuanyijia } from './cp8-helpers.ts';

const forbiddenBusinessCopy = /(?:sha256:|\bcontentRef\b|\bsourceId\b|\bevidenceRef\b|\bartifactId\b|\bcheckCode\b|\bFROZEN_FILE\b|\bDEFERRED\b|\[TRACE:|&(?:amp|gt|lt|quot);|\bM4\b|\bReceipt\b|\bCatalog\b)/iu;

async function expectReadableBusinessCopy(page: Page) {
  const visible = await page.locator('.app-content').evaluate((node) => {
    const clone = node.cloneNode(true) as HTMLElement;
    clone.querySelectorAll('pre, code, script, style, [data-technical-details]').forEach((element) => element.remove());
    return clone.innerText;
  });
  expect(visible).not.toMatch(forbiddenBusinessCopy);
}

async function openPage(page: Page, title: string) {
  await page.locator(`.app-menu .ant-menu-item[title="${title}"]`).click({ force: true });
  await expect(page.getByRole('heading', { name: title, exact: true }).first()).toBeVisible();
}

test.beforeEach(async ({ page }) => {
  await resetBrowserState(page);
  await login(page);
  await selectGuanyijia(page);
  await closeFormalCatalogBrowser(page);
});

test('所有主业务页面的默认可见文案不泄漏内部标识或编码实体', async ({ page }) => {
  test.setTimeout(180_000);

  await openStandardization(page, 'DESKTOP');
  await expectReadableBusinessCopy(page);

  for (const title of ['AI 建模', '本体建模', '指标配置', '业务规则', '同义词', '时间语义']) {
    await openPage(page, title);
    await expectReadableBusinessCopy(page);
  }
});
