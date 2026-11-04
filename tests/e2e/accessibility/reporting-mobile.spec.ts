import { expect, test } from '@playwright/test';

/**
 * Task 45 reporting accessibility on mobile-shaped viewports (OQ-010): essential
 * reporting journeys reflow at 200% zoom without two-dimensional scrolling, stay
 * keyboard-operable, and keep visible focus on the recurring workspace.
 */
test.describe('reporting mobile accessibility', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 384, height: 640 }); // phone width ≈ 200% zoom
    await page.goto('/');
  });

  test('reporting workspace reflows at 200% zoom without overflow', async ({ page }) => {
    await page.getByRole('button', { name: 'Reports' }).click();
    await expect(page.getByRole('heading', { name: 'Reports', exact: true })).toBeVisible();
    // WCAG 1.4.10: no horizontal scrolling at 200% zoom — vertical scrolling is normal.
    const horizontalOverflow = await page.evaluate(() => {
      const documentElement = document.documentElement;
      return documentElement.scrollWidth > documentElement.clientWidth + 1;
    });
    expect(horizontalOverflow, 'no horizontal scrolling at doubled zoom').toBe(false);
  });

  test('planning workspace remains keyboard reachable on a narrow viewport', async ({
    page,
  }) => {
    await page.getByRole('button', { name: 'Planning' }).focus();
    await expect(page.getByRole('button', { name: 'Planning' })).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('heading', { name: 'Planning', exact: true })).toBeVisible();

    // Tab reaches the first interactive control in the planning workspace.
    await page.keyboard.press('Tab');
    const focused = page.locator(':focus');
    await expect(focused).toBeVisible();
  });

  test('planning workspace reflows at 200% zoom without overflow', async ({ page }) => {
    await page.getByRole('button', { name: 'Planning' }).click();
    // WCAG 1.4.10: no horizontal scrolling at 200% zoom — vertical scrolling is normal.
    const horizontalOverflow = await page.evaluate(() => {
      const documentElement = document.documentElement;
      return documentElement.scrollWidth > documentElement.clientWidth + 1;
    });
    expect(horizontalOverflow, 'no horizontal scrolling at doubled zoom').toBe(false);
  });
});
