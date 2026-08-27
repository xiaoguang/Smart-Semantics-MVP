import { expect, test, type Locator, type Page } from '@playwright/test';
import {
  closeFormalCatalogBrowser,
  login,
  openStandardization,
  resetBrowserState,
  selectGuanyijia,
} from './cp8-helpers.ts';

async function bootstrapMobile(page: Page) {
  await page.clock.setFixedTime(new Date('2026-08-18T09:00:00+08:00'));
  await page.setViewportSize({ width: 390, height: 844 });
  await resetBrowserState(page);
  await login(page);
  await closeFormalCatalogBrowser(page);
  await selectGuanyijia(page);
  await openStandardization(page, 'MOBILE');
}

async function openExpandedAssistant(page: Page, turns: number) {
  await page.getByRole('button', { name: '开始资料整理', exact: true }).click();
  const document = page.getByRole('dialog', { name: '当前来源文档', exact: true });
  await expect(document).toBeVisible({ timeout: 30_000 });
  await document.getByRole('button', { name: '返回时间线', exact: true }).click();
  await expect(document).toBeHidden();

  const assistant = page.locator('#guanyijia-review-assistant');
  const composer = assistant.locator('#guanyijia-assistant-composer');
  const sendButton = assistant.locator('.guanyijia-review-assistant-composer > button');
  for (let index = 0; index < turns; index += 1) {
    const message = `第${index + 1}次询问当前结论`;
    await composer.fill(message);
    await expect(sendButton).toBeEnabled();
    await sendButton.click();
    await expect(composer).toHaveValue('');
  }

  const reopenDocument = page.locator('.guanyijia-next-task [data-workflow-primary="true"]:visible');
  await expect(reopenDocument).toHaveCount(1);
  await reopenDocument.click();
  await expect(document).toBeVisible({ timeout: 30_000 });
  const toggle = assistant.locator('.guanyijia-review-assistant-toggle');
  await expect(toggle).toHaveCount(1);
  await toggle.evaluate((element) => (element as HTMLButtonElement).click());
  await expect(assistant).toHaveClass(/expanded/u);
  return { assistant, composer, document, thread: page.locator('.guanyijia-workbench-thread') };
}

test('移动固定文档主机展开助手历史时，底部 composer 仍位于 visual viewport 内', async ({ page }) => {
  await bootstrapMobile(page);
  const { assistant, composer, thread } = await openExpandedAssistant(page, 6);
  const bounds = await Promise.all([
    thread.evaluate((element) => {
      const box = element.getBoundingClientRect();
      return { top: box.top, bottom: box.bottom };
    }),
    assistant.evaluate((element) => {
      const box = element.getBoundingClientRect();
      return { top: box.top, bottom: box.bottom };
    }),
    composer.evaluate((element) => {
      const box = element.getBoundingClientRect();
      return { top: box.top, bottom: box.bottom };
    }),
  ]);
  const visualViewport = await page.evaluate(() => {
    const viewport = window.visualViewport;
    const top = viewport?.offsetTop ?? 0;
    return { bottom: top + (viewport?.height ?? window.innerHeight) };
  });

  expect(bounds[1].bottom).toBeLessThanOrEqual(bounds[0].bottom + 1);
  expect(bounds[2].bottom).toBeLessThanOrEqual(visualViewport.bottom + 1);
});

test('展开的助手回答不会因左边距与内边距组合而越出面板', async ({ page }) => {
  await bootstrapMobile(page);
  const { assistant } = await openExpandedAssistant(page, 1);
  const answer: Locator = assistant.locator('.guanyijia-review-assistant-answer').last();
  await expect(answer).toBeVisible();

  const [panelBounds, answerBounds] = await Promise.all([
    assistant.boundingBox(),
    answer.boundingBox(),
  ]);
  expect(panelBounds).not.toBeNull();
  expect(answerBounds).not.toBeNull();
  expect(answerBounds!.x).toBeGreaterThanOrEqual(panelBounds!.x - 1);
  expect(answerBounds!.x + answerBounds!.width).toBeLessThanOrEqual(panelBounds!.x + panelBounds!.width + 1);
});
