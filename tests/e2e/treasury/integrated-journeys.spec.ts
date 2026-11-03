import { expect, test } from '@playwright/test';

/**
 * Task 32 integrated treasury journeys over the delivered shell: submission lookup with
 * governed states, certification/freshness status slots, denied/empty behaviors, and
 * the accessibility contract (keyboard, 200% reflow) on essential journeys.
 */
test.describe('integrated treasury journeys', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('submission journey surfaces governed state from the API boundary', async ({
    page,
  }) => {
    await expect(page.getByRole('heading', { name: 'Find a submission' })).toBeVisible();
    const input = page.getByLabel('Submission identifier');
    await input.fill('sub-nonexistent');
    await page.getByRole('button', { name: 'Look up' }).click();
    // Empty state is a governed outcome, not an error.
    await expect(
      page.getByText('No submission exists with that identifier.'),
    ).toBeVisible();
  });

  test('invalid identifier shows a safe error with correlation', async ({ page }) => {
    // Runtime-validated boundary: an impossible id shape still behaves safely.
    await page.getByLabel('Submission identifier').fill('sub-⚠-bad');
    await page.getByRole('button', { name: 'Look up' }).click();
    // Either safe outcome is acceptable: governed empty or correlated error.
    await expect(
      page
        .getByRole('alert')
        .or(page.getByText('No submission exists with that identifier.')),
    ).toBeVisible();
  });

  test('status slots expose certification and freshness semantics', async ({ page }) => {
    await page.getByRole('button', { name: 'Reports' }).click();
    await expect(page.getByText('Certification:')).toBeVisible();
    await expect(page.getByText('Freshness:')).toBeVisible();
  });

  test('essential journeys remain keyboard-operable and reflow at 200% zoom', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 640, height: 480 });
    await page.keyboard.press('Tab');
    await expect(
      page.getByRole('link', { name: 'Skip to main content' }),
    ).toBeFocused();
    const overflowed = await page.evaluate(() => {
      const el = document.documentElement;
      return el.scrollWidth > el.clientWidth + 1;
    });
    expect(overflowed, 'no horizontal overflow at doubled zoom').toBe(false);
  });
});
