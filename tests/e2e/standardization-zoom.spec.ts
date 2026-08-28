import { mkdirSync } from 'node:fs';
import { expect, test } from '@playwright/test';
import {
  closeFormalCatalogBrowser,
  enterMysqlDocument,
  login,
  openStandardization,
  resetBrowserState,
  selectGuanyijia,
} from './cp8-helpers.ts';

const visualDirectory = 'artifacts/cp8-visual';
mkdirSync(visualDirectory, { recursive: true });

test('真实Chrome 200%缩放精确投影720×500 visual viewport，Portal与键盘遮挡后仍在可见区', async ({ page, browserName }) => {
  expect(browserName).toBe('chromium');
  await page.clock.setFixedTime(new Date('2026-08-18T12:00:00.000Z'));
  await page.setViewportSize({ width: 1440, height: 1000 });
  await resetBrowserState(page);
  await login(page);
  await closeFormalCatalogBrowser(page);
  await selectGuanyijia(page);
  await openStandardization(page, 'DESKTOP');
  await enterMysqlDocument(page);

  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Emulation.setPageScaleFactor', { pageScaleFactor: 2 });
  await cdp.send('Emulation.setSafeAreaInsetsOverride', {
    insets: { top: 24, left: 0, bottom: 34, right: 0 },
  });
  let visual = (await cdp.send('Page.getLayoutMetrics')).cssVisualViewport;
  expect(visual.scale).toBeCloseTo(2, 2);
  expect(visual.clientWidth).toBeCloseTo(720, 0);
  expect(visual.clientHeight).toBeCloseTo(500, 0);
  const document = page.getByRole('dialog', { name: '当前来源文档' });
  await expect(document).toBeVisible();

  const assertVisibleBounds = async () => {
    visual = (await cdp.send('Page.getLayoutMetrics')).cssVisualViewport;
    // Playwright bounding boxes for fixed review surfaces are relative to the
    // DOM visual viewport. `visualViewport.offsetTop` is instead a layout-
    // viewport/document offset and can change when the focused textarea is
    // scrolled into view. Comparing the two coordinate systems would mark a
    // fully visible fixed layer as out of bounds after keyboard focus.
    const visibleFrame = await page.evaluate(() => ({
      width: window.visualViewport?.width ?? window.innerWidth,
      height: window.visualViewport?.height ?? window.innerHeight,
    }));
    const locators = [
      document,
      page.locator('.guanyijia-document-header-actions'),
      page.getByLabel('审阅助手', { exact: true }),
      page.locator('.ant-message-notice:visible'),
    ];
    for (const locator of locators) {
      const count = await locator.count();
      for (let index = 0; index < count; index += 1) {
        const box = await locator.nth(index).boundingBox();
        if (!box) continue;
        expect(box.x).toBeGreaterThanOrEqual(-1);
        expect(box.x + box.width).toBeLessThanOrEqual(visibleFrame.width + 2);
        expect(box.y).toBeGreaterThanOrEqual(-1);
        expect(box.y + box.height).toBeLessThanOrEqual(visibleFrame.height + 2);
      }
    }
  };
  await assertVisibleBounds();
  await page.screenshot({
    path: `${visualDirectory}/06-200-percent-real-zoom.png`,
    fullPage: false,
    animations: 'disabled',
    caret: 'hide',
  });

  await cdp.send('Emulation.setVisibleSize', { width: 1440, height: 720 });
  visual = (await cdp.send('Page.getLayoutMetrics')).cssVisualViewport;
  // Headless Chrome ignores setVisibleSize when no outer window is attached.
  // Keep the real page-scale factor and shrink only the emulated visible
  // frame so the keyboard-occlusion contract remains a genuine 200% run.
  if (visual.clientHeight === 500) {
    await cdp.send('Emulation.setDeviceMetricsOverride', {
      width: 1440, height: 720, deviceScaleFactor: 1, mobile: false, scale: 1,
    });
    visual = (await cdp.send('Page.getLayoutMetrics')).cssVisualViewport;
  }
  expect(visual.clientWidth).toBeCloseTo(720, 0);
  expect(visual.clientHeight).toBeCloseTo(360, 0);
  await page.getByLabel('审阅助手', { exact: true }).locator('textarea').fill('键盘遮挡回归');
  await assertVisibleBounds();
});
