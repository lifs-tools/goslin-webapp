import { readFile } from 'node:fs/promises';
import { acceptCookies, dropFile, expect, harmonize, pasteNames, test } from './helpers';

test.describe('Harmonize', () => {
  test.beforeEach(async ({ context }) => {
    // the consent banner is tested in consent.spec.ts
    await acceptCookies(context);
  });

  test('paste, harmonize, fix, re-run, download', async ({ page }) => {
    await harmonize(page, 'PC 32:0\n\nPC(18:2-20:4\nTG 52:2');

    // line 2 is blank, so the broken name is line 3
    const fix = page.locator('section#fix');
    const row = fix.locator('.fix__row[data-line="3"]');
    await expect(row).toBeVisible();
    await expect(fix.locator('.fix__row')).toHaveCount(1);
    await expect(row.locator('.trace mark')).toHaveCount(1);
    await expect(page.locator('tr.result-row[data-line="1"]')).toBeVisible();
    await expect(page.locator('tr.result-row[data-line="4"]')).toBeVisible();

    await row.locator('input[name="edit[3]"]').fill('PC(18:2-20:4)');
    await page.locator('button#rerun').click();

    const outcome = page.locator('.lifs-callout--ok', { hasText: '1 corrected name was harmonized' });
    await expect(outcome).toBeVisible();
    await expect(outcome).toBeFocused();
    await expect(page.locator('section#fix')).toHaveCount(0);
    await expect(page.locator('tr.result-row[data-line="3"] .chip-corrected')).toBeVisible();

    const downloadPromise = page.waitForEvent('download');
    await page.locator('a#download-tsv').click();
    const download = await downloadPromise;
    const tsv = await readFile(await download.path(), 'utf8');
    const [header, ...lines] = tsv.split(/\r?\n/).filter((line) => line !== '');
    expect(header.startsWith('Line\tNormalized Name')).toBe(true);
    expect(lines.map((line) => line.split('\t')[0])).toEqual(['1', '3', '4']);
  });

  test('load example and file drop', async ({ page }) => {
    await page.goto('./');
    const count = page.locator('#lipid-count');
    await expect(count).toContainText('0 names');

    await page.locator('a#load-example').click();
    await expect(count).toContainText('56 names');
    await expect(page.locator('#lipids')).toHaveValue(/^12-HETE\n/);

    await dropFile(page.locator('.goslin-names'), 'names.txt', 'PC 32:0\nTG 52:2\n');
    await expect(count).toContainText('2 names');
    await expect(page.locator('#lipids')).toHaveValue('PC 32:0\nTG 52:2\n');
  });

  test('filter and paging', async ({ page }) => {
    await page.goto('./');
    await page.locator('a#load-example').click();
    await page.locator('#harmonize').click();
    await expect(page).toHaveURL(/\/result\//);

    const pager = page.locator('#pager');
    await expect(pager).toContainText('Rows 1–25');
    await expect(page.locator('tr.result-row:visible')).toHaveCount(25);

    await page.locator('#filter-text').fill('TG');
    await expect(pager).toContainText('filtered from');
    const visible = page.locator('tr.result-row:visible');
    await expect(visible).not.toHaveCount(0);
    const searches = await visible.evaluateAll((rows) => rows.map((row) => row.getAttribute('data-search') ?? ''));
    expect(searches.length).toBeLessThanOrEqual(25);
    for (const search of searches) {
      expect(search.toLowerCase()).toContain('tg');
    }
  });

  test('harmonizes 5000 names', async ({ page }) => {
    const names = Array.from({ length: 5000 }, () => 'PC 32:0').join('\n');
    await page.goto('./');
    await pasteNames(page, names);
    await expect(page.locator('#lipid-count')).toContainText('5,000 names');

    const started = Date.now();
    await page.locator('#harmonize').click();
    // Wait for the load event before looking for elements: polling for an element that is not parsed yet
    // stalls Chromium's parser on this 5,000-row page (Playwright only; a browser shows it in ~3 s).
    await page.waitForURL(/\/result\//, { waitUntil: 'load', timeout: 60_000 });
    await expect(page.locator('#pager')).toContainText('of 5,000');
    const seconds = (Date.now() - started) / 1000;
    test.info().annotations.push({ type: 'timing', description: `5,000 names harmonized and shown in ${seconds.toFixed(1)} s` });
    console.log(`5,000 names harmonized and shown in ${seconds.toFixed(1)} s`);
    expect(seconds).toBeLessThan(60);

    await expect(page.locator('tr.result-row')).toHaveCount(5000);
    await expect(page.locator('tr.result-row:visible')).toHaveCount(25);
  });

  test('enter in a correction field re-runs', async ({ page }) => {
    await harmonize(page, 'PC 32:0\nPC(18:2-20:4\nCholesterl');
    await expect(page.locator('.fix__row')).toHaveCount(2);

    const first = page.locator('input[name="edit[2]"]');
    await first.fill('PC(18:2-20:4)');
    await first.press('Enter');

    await expect(page.locator('.lifs-callout--ok', { hasText: '1 corrected name was harmonized' })).toBeVisible();
    // the fixed name is in the table, the other one is still waiting for a correction: nothing was removed
    await expect(page.locator('tr.result-row[data-line="2"] .chip-corrected')).toBeVisible();
    await expect(page.locator('.fix__row[data-line="3"]')).toBeVisible();
    await expect(page.locator('.fix__row')).toHaveCount(1);
  });
});
