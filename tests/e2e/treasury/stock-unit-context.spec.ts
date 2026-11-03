import { expect, test } from '@playwright/test';

/**
 * Task 36 stock/unit context e2e: the optional feature presents as disabled until an
 * owner signs activation (fail-closed default), and no liquidity surface leaks when
 * disabled.
 */
test.describe('stock/unit liquidity context', () => {
  test('disabled state is visible and names the owner decision', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'Reports' }).click();
    await expect(
      page.getByText(/Disabled pending a named business owner.s activation decision/),
    ).toBeVisible();
  });

  test('no liquidity figures render while disabled', async ({ page }) => {
    await page.goto('/');
    const body = await page.locator('main').textContent();
    expect(body).not.toMatch(/quantity|ageing/i);
  });
});
