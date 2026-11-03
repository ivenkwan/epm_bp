import { expect, test } from '@playwright/test';

/**
 * R0 control journeys over the deployed shell: health surface reachable, navigation
 * landmarks intact, and excluded actions absent from the client bundle surface (the
 * server contract is asserted in the API suite; this journey asserts the user-facing
 * surface exposes no excluded entry points).
 */
test.describe('R0 control journeys', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('application shell exposes governed navigation only', async ({ page }) => {
    await expect(page.getByRole('banner')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Submissions' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Reports' })).toBeVisible();
    const buttons = await page.getByRole('button').allTextContents();
    for (const excluded of ['sql', 'cypher', 'drl', 'bpmn', 'payment', 'posting']) {
      expect(
        buttons.join(' ').toLowerCase(),
        `no excluded action surface: ${excluded}`,
      ).not.toContain(excluded);
    }
  });

  test('submission lookup shows the empty prompt before any request', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Find a submission' })).toBeVisible();
    await expect(
      page.getByText('Enter a submission identifier to see its governed state.'),
    ).toBeVisible();
  });

  test('status slots are visible on the reports surface', async ({ page }) => {
    await page.getByRole('button', { name: 'Reports' }).click();
    await expect(page.getByText('Certification:')).toBeVisible();
    await expect(page.getByText('Freshness:')).toBeVisible();
  });
});
