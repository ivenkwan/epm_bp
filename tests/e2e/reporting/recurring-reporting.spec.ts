import { expect, test } from '@playwright/test';

/**
 * Task 45 recurring reporting e2e: certified dashboards with freshness labels,
 * governed export formats with retention classification, recurring editions with
 * named publication provenance, and the explicit absence of automatic publication.
 */
test.describe('recurring reporting workspace', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'Reports' }).click();
  });

  test('certified dashboard tiles show freshness and lowest governed grain', async ({
    page,
  }) => {
    const tile = page.getByTestId('tile-tile-cash');
    await expect(tile).toBeVisible();
    await expect(tile.getByText('kpi-cash')).toBeVisible();
    await expect(tile.getByText('Certified', { exact: true })).toBeVisible();
    await expect(tile.getByText(/Freshness: 2026-11-03T18:00:00Z/)).toBeVisible();
    await expect(tile.getByText(/Lowest governed grain: Entity/)).toBeVisible();
    await expect(tile.getByRole('button', { name: 'Drill' })).toBeVisible();
  });

  test('recurring editions expose all approved export formats with retention', async ({
    page,
  }) => {
    const weekly = page.getByTestId('edition-ed-w45');
    await expect(weekly).toBeVisible();
    for (const format of ['Excel', 'PDF', 'Presentation']) {
      await expect(weekly.getByRole('button', { name: format })).toBeVisible();
    }
    await expect(weekly.getByText('RETENTION_7Y')).toBeVisible();
  });

  test('publication is a named-human act — review state never claims publication', async ({
    page,
  }) => {
    const review = page.getByTestId('edition-ed-w45');
    await expect(review.getByText('In review — awaiting named publication')).toBeVisible();
    await expect(review.getByRole('cell', { name: '—', exact: true })).toBeVisible();

    const published = page.getByTestId('edition-ed-m10');
    await expect(published.getByText('PUBLISHED')).toBeVisible();
    await expect(published.getByRole('cell', { name: 'publisher@example.invalid' })).toBeVisible();
  });

  test('watermark and recipient re-authorization are stated on the surface', async ({
    page,
  }) => {
    await expect(
      page.getByText(/watermarked, retention-classified, and re-authorize every recipient/),
    ).toBeVisible();
  });
});
