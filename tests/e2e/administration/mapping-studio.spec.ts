import { expect, test } from '@playwright/test';

/**
 * Task 57 mapping studio e2e: the studio surface shows effective-dated mapping
 * versions with provenance, the no-code/no-history-loss statement, and the visual
 * maintenance actions; the surface reflows at 200% zoom.
 */
test.describe('mapping studio', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'Planning' }).click();
  });

  test('mapping versions show effective dating, status, and provenance', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Mapping studio' })).toBeVisible();
    await expect(page.getByText('2026-01-01 → 2027-01-01')).toBeVisible();
    await expect(page.getByText('Proposed — awaiting approval')).toBeVisible();
    await expect(page.getByText('activated by approver@epm')).toBeVisible();
  });

  test('governed-configuration and history statements are visible', async ({ page }) => {
    await expect(page.getByText(/governed configuration, never/)).toBeVisible();
    await expect(page.getByText(/rollback is a new version; history is never lost/)).toBeVisible();
  });

  test('visual maintenance actions are exposed', async ({ page }) => {
    await expect(page.getByRole('button', { name: 'Compare' }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Timeline' }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Preview impact' }).first()).toBeVisible();
  });

  test('mapping studio reflows at 200% zoom without horizontal scrolling', async ({ page }) => {
    await page.setViewportSize({ width: 384, height: 640 });
    const horizontalOverflow = await page.evaluate(() => {
      const d = document.documentElement;
      return d.scrollWidth > d.clientWidth + 1;
    });
    expect(horizontalOverflow, 'no horizontal scrolling at doubled zoom').toBe(false);
  });
});
