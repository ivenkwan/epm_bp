import { expect, test } from '@playwright/test';

/**
 * Task 39 management-pack e2e: packs list with governance labels (owner,
 * certification, edition version, approval state), zero/missing semantics stated,
 * uncertified required scope visibly blocked (not silently published), and a
 * reproduce action for the immutable edition.
 */
test.describe('management decision packs', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'Reports' }).click();
  });

  test('pack rows show layout, owner, edition, and approval labels', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Management decision packs' })).toBeVisible();
    const flashRow = page.getByTestId('pack-row-pack-flash-w13');
    await expect(flashRow).toBeVisible();
    await expect(flashRow.getByRole('cell', { name: 'FLASH', exact: true })).toBeVisible();
    await expect(flashRow.getByRole('cell', { name: 'fpa@example.invalid' })).toBeVisible();
    await expect(flashRow.getByText('Certified', { exact: true })).toBeVisible();
    await expect(flashRow.getByRole('cell', { name: 'v3', exact: true })).toBeVisible();
    await expect(flashRow.getByRole('cell', { name: 'APPROVED', exact: true })).toBeVisible();
  });

  test('uncertified required scope is visibly blocked, never silently approved', async ({
    page,
  }) => {
    const boardRow = page.getByTestId('pack-row-pack-board-q1');
    await expect(boardRow).toBeVisible();
    await expect(boardRow.getByText('Blocked: uncertified required scope')).toBeVisible();
    await expect(boardRow.getByRole('cell', { name: 'APPROVED', exact: true })).toHaveCount(0);
  });

  test('zero activity and missing data semantics are stated on the surface', async ({ page }) => {
    await expect(
      page.getByText(/Zero activity shows as zero; missing data stays unavailable/),
    ).toBeVisible();
  });

  test('each pack exposes a reproduce-edition action', async ({ page }) => {
    await expect(page.getByRole('button', { name: 'Reproduce edition' })).toHaveCount(2);
  });
});
