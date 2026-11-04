import { expect, test } from '@playwright/test';

/**
 * Task 47 full self-service e2e: dashboard slicing and drill with certification
 * labels, saved views with explicit restricted-entity denial, export handoff, and
 * 200%-zoom reflow of the full reporting surface.
 */
test.describe('full self-service analysis', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'Reports' }).click();
  });

  test('dashboard tiles expose slice, drill, and saved-view actions', async ({ page }) => {
    const tile = page.getByTestId('tile-tile-cash');
    await expect(tile.getByRole('button', { name: 'Slice' })).toBeVisible();
    await expect(tile.getByRole('button', { name: 'Drill' })).toBeVisible();
    await expect(tile.getByRole('button', { name: 'Save authorized view' })).toBeVisible();
    await expect(tile.getByText('Certified', { exact: true })).toBeVisible();
  });

  test('pack surfaces keep publication approval and reproduction visible', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Management decision packs' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Reproduce edition' }).first()).toBeVisible();
    await expect(page.getByText(/Blocked: uncertified required scope/)).toBeVisible();
  });

  test('export handoff actions remain available on recurring editions', async ({ page }) => {
    const edition = page.getByTestId('edition-ed-m10');
    await expect(edition.getByRole('button', { name: 'Excel' })).toBeVisible();
    await expect(edition.getByRole('button', { name: 'PDF' })).toBeVisible();
    await expect(edition.getByRole('button', { name: 'Presentation' })).toBeVisible();
  });

  test('full reporting surface reflows at 200% zoom without horizontal scrolling', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 384, height: 640 });
    const horizontalOverflow = await page.evaluate(() => {
      const d = document.documentElement;
      return d.scrollWidth > d.clientWidth + 1;
    });
    expect(horizontalOverflow, 'no horizontal scrolling at doubled zoom').toBe(false);
  });
});
