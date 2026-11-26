import { acceptCookies, expect, expectBubble, test } from './helpers';

/** The Goslin logo on the input page: it blinks, and pecks and honks when clicked. */
test.describe('Logo easter egg', () => {
  test.beforeEach(async ({ context }) => {
    await acceptCookies(context);
  });

  test('honk on click, special line on fifth', async ({ page }) => {
    await page.goto('./');
    const logo = page.locator('.goslin-logo');
    await expect(logo.locator('svg')).toHaveAttribute('aria-label', 'Goslin logo');
    await expect(logo.locator('.g-bubble')).toHaveAttribute('aria-hidden', 'true');
    await expect(logo.locator('.g-bubble')).toHaveCSS('opacity', '0');
    expect(await logo.locator('.g-eye').evaluate((eye) => getComputedStyle(eye).animationName)).toBe('g-blink');

    await logo.click();
    await expectBubble(logo, 'Honk!');
    expect(await logo.locator('.g-head').evaluate((head) => getComputedStyle(head).animationName)).toBe('g-peck');

    for (let i = 0; i < 4; i++) {
      await logo.click();
    }
    await expectBubble(logo, 'PC 16:0/18:1(9Z), please.');
  });

  test('reduced motion stops blink and peck', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('./');
    const logo = page.locator('.goslin-logo');
    expect(await logo.locator('.g-eye').evaluate((eye) => getComputedStyle(eye).animationName)).toBe('none');

    await logo.click();
    await expectBubble(logo, 'Honk!');
    expect(await logo.locator('.g-head').evaluate((head) => getComputedStyle(head).animationName)).toBe('none');
  });
});
