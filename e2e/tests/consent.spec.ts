import { acceptCookies, CONSENT_COOKIE, expect, MATOMO, matomoScripts, test } from './helpers';

/**
 * The consent banner of lifs-webapps-common (static/lifs/js/lifs-tracking.js). Matomo is answered by
 * helpers.ts, so these tests only see which requests the page would make.
 */
test.describe('Consent banner', () => {
  test('accept loads matomo once and hides banner', async ({ page, context, matomo }) => {
    await page.goto('./');
    const banner = page.getByTestId('consent-banner');
    await expect(banner).toBeVisible();
    await expect(banner).toHaveAttribute('aria-label', 'Cookie consent');
    await page.waitForLoadState('networkidle');
    expect(matomoScripts(matomo)).toEqual([]);

    await page.getByTestId('consent-accept').click();
    await expect(banner).toHaveCount(0);
    await expect.poll(() => matomoScripts(matomo)).toEqual([`${MATOMO}matomo.js`]);
    // loaded without a reload
    expect(page.url()).toMatch(/\/goslin\/$/);
    const cookie = (await context.cookies()).find((c) => c.name === CONSENT_COOKIE);
    expect(cookie).toMatchObject({ value: 'on', path: '/', sameSite: 'Lax' });
    // about 30 days
    const days = (cookie!.expires * 1000 - Date.now()) / 86_400_000;
    expect(days).toBeGreaterThan(29);
    expect(days).toBeLessThanOrEqual(30);

    await page.reload();
    await page.waitForLoadState('networkidle');
    await expect(page.getByTestId('consent-banner')).toHaveCount(0);
    // once per page load
    expect(matomoScripts(matomo)).toHaveLength(2);
  });

  test('deny hides banner and loads nothing', async ({ page, context, matomo }) => {
    await page.goto('./');
    const banner = page.getByTestId('consent-banner');
    await expect(banner).toBeVisible();

    await page.getByTestId('consent-deny').click();
    await expect(banner).toHaveCount(0);
    await page.waitForLoadState('networkidle');
    expect(matomoScripts(matomo)).toEqual([]);
    expect((await context.cookies()).find((c) => c.name === CONSENT_COOKIE)).toBeUndefined();

    // Deny is not stored: the next page asks again
    await page.reload();
    await expect(page.getByTestId('consent-banner')).toBeVisible();
    await page.waitForLoadState('networkidle');
    expect(matomo).toEqual([]);
  });

  test('do not track never loads matomo', async ({ page, context, matomo }) => {
    await context.addInitScript(() => {
      Object.defineProperty(Navigator.prototype, 'doNotTrack', { configurable: true, get: () => '1' });
    });
    // Do Not Track wins over an earlier consent
    await acceptCookies(context);

    await page.goto('./');
    const notice = page.getByTestId('consent-dnt');
    await expect(notice).toBeVisible();
    await expect(notice).toHaveAttribute('aria-label', 'Do Not Track notice');
    await expect(page.getByTestId('consent-banner')).toHaveCount(0);
    await page.waitForLoadState('networkidle');
    expect(matomo).toEqual([]);

    await page.getByTestId('consent-dnt-dismiss').click();
    await expect(notice).toHaveCount(0);
    await page.waitForLoadState('networkidle');
    expect(matomo).toEqual([]);
  });
});
