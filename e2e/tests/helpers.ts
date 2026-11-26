import { test as base, expect, BrowserContext, Locator, Page } from '@playwright/test';

/** The Matomo base the consent script uses (lifs-webapps-common, lifs/layout :: tracking). */
export const MATOMO = 'https://lifs-tools.org/matomo/';

/** The consent cookie of lifs-tracking.js. */
export const CONSENT_COOKIE = 'lifs-cookies-accepted';

type Fixtures = {
  /** The URLs of the Matomo requests the page made; each was answered with an empty 200 here. */
  matomo: string[];
};

/**
 * Every test: requests to Matomo get an empty 200 response and are recorded, and every other request that
 * leaves localhost is aborted, so that the tests never send tracking data anywhere.
 */
export const test = base.extend<Fixtures>({
  matomo: [
    async ({ context }, use) => {
      const requests: string[] = [];
      await context.route(
        (url) => url.hostname !== 'localhost' && url.hostname !== '127.0.0.1',
        (route) => route.abort('blockedbyclient'),
      );
      // registered last, so it is matched first
      await context.route(`${MATOMO}**`, (route) => {
        requests.push(route.request().url());
        const javascript = new URL(route.request().url()).pathname.endsWith('.js');
        return route.fulfill({ status: 200, contentType: javascript ? 'text/javascript' : 'text/plain', body: '' });
      });
      await use(requests);
    },
    { auto: true },
  ],
});

export { expect };

/** The matomo.js loads among the recorded Matomo requests. */
export function matomoScripts(requests: string[]): string[] {
  return requests.filter((url) => new URL(url).pathname.endsWith('/matomo.js'));
}

/** Gives consent before the first page load, so that the banner does not cover the page. */
export async function acceptCookies(context: BrowserContext): Promise<void> {
  await context.addCookies([{ name: CONSENT_COOKIE, value: 'on', domain: 'localhost', path: '/', sameSite: 'Lax' }]);
}

/**
 * Puts the text into the names box as a paste does: one input event. (fill() types the text with
 * insertText, which takes Chromium over 20 s for 5,000 lines; a real paste of them takes milliseconds.)
 */
export async function pasteNames(page: Page, names: string): Promise<void> {
  await page.locator('#lipids').evaluate((box: HTMLTextAreaElement, text) => {
    box.focus();
    box.value = text;
    box.dispatchEvent(new Event('input', { bubbles: true }));
  }, names);
}

/** Types the names on the input page, harmonizes them and waits for the result page (and its load event). */
export async function harmonize(page: Page, names: string): Promise<void> {
  await page.goto('./');
  await page.locator('#lipids').fill(names);
  await page.locator('#harmonize').click();
  await page.waitForURL(/\/result\/[^/?]+/);
}

/** Drops a text file onto the names box, as a file dragged from the desktop. */
export async function dropFile(target: Locator, name: string, content: string): Promise<void> {
  const dataTransfer = await target.page().evaluateHandle(
    ([fileName, text]) => {
      const transfer = new DataTransfer();
      transfer.items.add(new File([text], fileName, { type: 'text/plain' }));
      return transfer;
    },
    [name, content] as const,
  );
  await target.dispatchEvent('dragenter', { dataTransfer });
  await target.dispatchEvent('dragover', { dataTransfer });
  await target.dispatchEvent('drop', { dataTransfer });
}

/** The logo's speech bubble is shown (it fades in: opacity 0 counts as visible for Playwright). */
export async function expectBubble(logo: Locator, text: string): Promise<void> {
  const bubble = logo.locator('.g-bubble');
  await expect(bubble).toHaveText(text);
  await expect(bubble).toBeVisible();
  await expect(bubble).toHaveCSS('opacity', '1');
}
