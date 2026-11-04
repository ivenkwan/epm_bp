import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

/**
 * Task 7 browser journeys: keyboard operability, focus visibility, 200%-zoom reflow,
 * and an automated WCAG scan with zero critical/serious findings. Run against the
 * built app (pnpm --dir apps/finance-web run preview) via playwright config.
 */
test.describe('finance-web shell accessibility', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('skip link jumps focus to the main landmark', async ({ page }) => {
    const skip = page.getByRole('link', { name: 'Skip to main content' });
    await skip.focus();
    await page.keyboard.press('Enter');
    await expect(page.locator('#main-content')).toBeFocused();
  });

  test('primary navigation is fully keyboard operable', async ({ page }) => {
    await page.keyboard.press('Tab');
    await expect(page.getByRole('link', { name: 'Skip to main content' })).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: 'Submissions' })).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: 'Reports' })).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('heading', { name: 'Reports' })).toBeVisible();
  });

  test('reflows at 200% zoom without two-dimensional scrolling', async ({ page }) => {
    // Simulated 200% zoom: halve the viewport in CSS pixels. WCAG 1.4.10 governs
    // horizontal reflow: content must stack vertically, never scroll sideways.
    // (Vertical page scrolling is the expected reflow direction.)
    await page.setViewportSize({ width: 640, height: 480 });
    const horizontalOverflow = await page.evaluate(() => {
      const documentElement = document.documentElement;
      return documentElement.scrollWidth > documentElement.clientWidth + 1;
    });
    expect(horizontalOverflow, 'no horizontal page overflow at doubled zoom').toBe(false);
  });

  test('automated WCAG scan reports no critical or serious findings', async ({ page }) => {
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    const severe = results.violations.filter((violation) =>
      ['critical', 'serious'].includes(violation.impact ?? ''),
    );
    expect(
      severe.map((violation) => `${violation.id}: ${violation.help}`),
      'critical/serious WCAG violations',
    ).toEqual([]);
  });
});
