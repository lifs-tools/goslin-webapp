import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { acceptCookies, dropFile, expect, harmonize, test } from './helpers';
import type { Page } from '@playwright/test';

/**
 * The screenshots of the manual (documentation page), written to src/main/resources/static/img.
 * Only run by `npm run screenshots` (SCREENSHOTS=1, see playwright.config.ts).
 */
const IMG = join(__dirname, '../../src/main/resources/static/img/');
const EXAMPLE = join(__dirname, '../../src/main/resources/lipidnames.txt');

/** The names of the dropped file in figure 3: two of them cannot be read. */
const DROPPED = ['PC 16:0/18:1(9Z)', 'PC(18:2-20:4', 'TG 52:2', 'Cholesterl', 'SM d35:1 [M+H]1+', 'PE 18:0/22:6'].join('\n') + '\n';

test.use({ viewport: { width: 1280, height: 800 }, deviceScaleFactor: 1 });

test.describe.configure({ mode: 'serial' });

test.beforeEach(async ({ context }) => {
  // no consent banner over the pages
  await acceptCookies(context);
});

/** Removes the development version warning (and its then empty container), which released versions do not show. */
async function hideSnapshotWarning(page: Page): Promise<void> {
  await page.evaluate(() => {
    document.querySelectorAll('.lifs-callout--warn').forEach((callout) => {
      if (!callout.textContent?.includes('development version')) {
        return;
      }
      const parent = callout.parentElement;
      callout.remove();
      if (parent && parent.children.length === 0) {
        parent.remove();
      }
    });
  });
}

async function shoot(page: Page, name: string): Promise<void> {
  // no hover effects: the pointer rests in the right margin
  await page.mouse.move(1275, 795);
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: `${IMG}${name}`, animations: 'disabled', caret: 'hide' });
}

/** Scrolls so that the element starts a little below the top of the window. */
async function scrollTo(page: Page, selector: string, offset = 24): Promise<void> {
  await page.locator(selector).first().evaluate((el, top) => {
    window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - top);
  }, offset);
}

test('input page', async ({ page }) => {
  await page.goto('./');
  await hideSnapshotWarning(page);
  await shoot(page, 'goslin-webapp-form-01.png');

  await page.locator('#load-example').click();
  await expect(page.locator('#lipid-count')).toContainText('56 names');
  await page.locator('#lipids').evaluate((box: HTMLTextAreaElement) => box.setSelectionRange(0, 0));
  await page.locator('#lipids').evaluate((box) => (box.scrollTop = 0));
  // the whole card: the names, Skip invalid names and Harmonize
  await scrollTo(page, '.cols', 24);
  await shoot(page, 'goslin-webapp-form-02.png');
});

test('input page: dropped file with names that cannot be read', async ({ page }) => {
  await page.goto('./');
  await hideSnapshotWarning(page);
  await dropFile(page.locator('.goslin-names'), 'my-lipids.txt', DROPPED);
  await expect(page.locator('#lipid-count')).toContainText('6 names');
  await page.locator('#skipInvalid').uncheck();
  await page.locator('#harmonize').click();
  await expect(page.locator('#failures')).toBeVisible();
  await hideSnapshotWarning(page);
  await scrollTo(page, '.cols', 24);
  await shoot(page, 'goslin-webapp-form-02a.png');
});

test('result page', async ({ page }) => {
  // the example names with three broken ones, at the lines of the mock
  const lines = (await readFile(EXAMPLE, 'utf8')).split(/\r?\n/).filter((line) => line.trim() !== '');
  lines[33] = 'PC(18:2-20:4';
  lines[50] = 'TG 16:0_18:1_18:2x';
  lines[8] = 'Cholesterl';
  await harmonize(page, lines.join('\n'));
  await expect(page.locator('.fix__row')).toHaveCount(3);
  await hideSnapshotWarning(page);
  await shoot(page, 'goslin-webapp-result-03.png');
});

test('result page: details of a name', async ({ page }) => {
  await page.goto('./');
  await page.locator('#load-example').click();
  await page.locator('#harmonize').click();
  await page.waitForURL(/\/result\//);
  await hideSnapshotWarning(page);
  const row = page.locator('tr.result-row[data-line="7"]');
  await row.locator('.xbtn').click();
  await expect(page.locator('#detail-7')).toBeVisible();
  await scrollTo(page, '#results', 16);
  await shoot(page, 'goslin-webapp-result-detail-04.png');
});
