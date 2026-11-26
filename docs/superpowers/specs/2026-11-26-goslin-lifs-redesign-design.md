# Goslin webapp: LIFS design, platform upgrade and usability (issue #121)

Date: 2026-11-26 · Status: draft for approval

Mocks (approved look):
- `docs/superpowers/specs/2026-11-26-goslin-lifs-redesign-mock.html`: input page and result page.
- `docs/superpowers/specs/2026-11-26-goslin-easter-egg-mock.html`: logo effects. Options A and C
  were chosen; B was not.

Both load the LIFS stylesheet from `/files/lifs.css`. To view them outside the brainstorming server,
change that link to `lifs.css`. Then put `lifs-webapps-common/src/main/resources/static/lifs/css/lifs.css`
(with its fonts) and `goslin/goslin.svg` (as `goslin-logo.svg`) next to them.

## 1. Intent

Users find the Goslin start page confusing (issue #121). At the same time, the LIFS web applications
are moving to one shared look, the de.NBI LIFS design system. That system shipped in
lifs-webapps-common 2.0.0 and has already been adopted by lifs-apps. Goslin should:

1. look like the other LIFS tools (app bar, hero, cards, footer from `lifs/layout`),
2. run on the platform those tools now share: Java 25, Spring Boot 4.2, lifs-webapps-common 2.0.x,
3. become easier to use. Someone pastes a list of lipid names, gets a harmonized table back, and can
   fix the names that failed without starting over.

## 2. Scope and order

This work touches four repositories. Work in this order: each step depends on the one before it.

1. **lifs-webapps-common 2.0.1** (release):
   - storage that can hold several files per result and replace a file (6.3),
   - several messages at once in `lifs/layout :: messages` (6.6),
   - Matomo consent in plain JavaScript (6.7).
2. **goslin-webapp**: the platform upgrade (6.8), then the redesign (6.1–6.7).
3. **lifs-apps**: add Matomo tracking back, using the new consent from lifs-webapps-common 2.0.1 (6.7).
4. **docker-recipes-external** (`external/applications/docker-compose-goslin.yml`):
   - mount the shared volume on every Goslin replica as `storage.location` (6.3),
   - move the environment properties to their Spring Boot 4.2 names, as lifs-apps `4308b43` did.

Each repository gets its own branch and pull request. In goslin-webapp, the upgrade and the
redesign go in separate commits so they can be reviewed apart.

## 3. Goals

- **Input page (issue #121)**
  1. One large text area, one name per line. It replaces the numbered input rows.
  2. Remove the +, −, "remove all" and "reset to default" buttons.
  3. The text area starts empty. A **"Load example names"** button fills in the contents of
     `lipidnames.txt`, the 56 names that are preloaded today.
  4. **"Skip invalid names"** is checked by default.
  5. **"Harmonize"** replaces "Normalize" in the UI.
- File upload goes into the same text area. "Upload a file…" or dropping a file onto the box reads
  the file into it. The separate "Lipid File Input" tab is removed.
- **Result page**
  - A summary banner with the counts: N harmonized, M not read.
  - **A "Correct these names" card comes first** when names failed. Each failed name shows:
    - its original line number,
    - an editable copy of the name,
    - a message in plain language,
    - the part of the name after the position where parsing stopped, marked.

    "Re-run corrected names" sends only the failed names again. Names that now parse join the
    table, marked "corrected". "Remove" drops a name from the result.
  - **A filterable, sortable, paged table** with one row per harmonized name.
  - **"Download table (TSV)" is the main action**: a filled button next to the page title, repeated
    under the table.
- **Original line numbers are kept** for every name, from input to table to TSV.
- **The Goslin logo** (the original `goslin/goslin.svg`) appears in the start-page hero at 160 px, in
  place of the LIFS logo. It has two easter eggs:
  - it blinks now and then,
  - clicking it makes the gosling peck and say "Honk!".
- The documentation page and the error page get the LIFS design. The manual's "Interactive Usage"
  section is rewritten for the new pages, with new screenshots.
- Matomo tracking stays, with a consent banner in plain JavaScript. lifs-apps gets tracking back.
- Results survive redeploys and work on every replica (shared volume).

## 4. Non-goals

- No change to jgoslin. It stays at its current version (2.2.1), and grammars and parsing rules are
  not touched.
- No change to REST API paths, request and response shapes, or message texts. An optional `line`
  field in REST responses is a possible follow-up, not part of this work.
- No Angular or other single-page-app frontend, and no frontend build step.
- No "did you mean" suggestions for failed names.
- No new log-in requirement. The app stays anonymous.
- No eye-follows-the-pointer effect (option B of the easter-egg mock).

## 5. Anti-goals (would count as failure even if it works)

- Line numbers that do not match the user's own file or text, for example because blank lines were
  skipped while counting.
- A re-run that loses or re-orders the names that already worked, or that changes their line numbers.
- A result that only exists as the response to a POST, so that a reload re-submits or loses it.
- A result link that works on one replica and not the other, or that breaks after a redeploy within
  the retention period.
- Breaking existing REST clients (pygoslin, rgoslin and scripts) or the Swagger UI address.
- A UI that diverges from lifs-apps' use of the shared `lifs/layout` fragments, for example a copied,
  locally modified layout. Changes that every app needs go into lifs-webapps-common.
- An easter egg that gets in the way: it moves without being asked beyond the occasional blink,
  makes a sound, or ignores reduced motion.

## 6. Design

### 6.0 Approaches considered

| | Approach | Trade-off |
|---|---|---|
| **A (chosen)** | Server-rendered Thymeleaf pages with the shared LIFS fragments, plus one small plain-JavaScript file. Results are stored under `/result/{id}`. Re-run is a normal form post that redirects back to the same address. | Same stack and pattern as lifs-apps, no frontend build, core flow works without JavaScript. Up to 5,000 rows are filtered, sorted and paged in the browser, so the script must stay lean. |
| B | A, plus htmx for partial page updates on re-run and filtering | Slightly smoother, but adds a library no other LIFS Thymeleaf app uses. Server-side filtering costs a round trip per keystroke. |
| C | Angular SPA on the REST API, like jmzTab-m-webapp | Node build and a much larger rewrite for two pages. Splits Goslin from the lifs-apps path. Dropped. |

Nils chose **A**.

### 6.1 Line numbers

- A name's **line number** is its 1-based line in the submitted text: the text area contents, or a
  file read into the text area. Blank lines count but are not submitted. `\r\n`, `\n` and `\r` all end
  a line.
- A byte-order mark is dropped and non-breaking spaces count as spaces. Each non-blank line is
  trimmed and runs of whitespace are collapsed to one space, as the file path
  does today. Duplicates are kept as separate entries with their own line numbers.
- Line numbers are assigned once, on the server, from the raw text. They never change afterwards,
  through re-runs or removals.
- The table is sorted by line by default. Corrected names go back to their place in line order.

### 6.2 Input page (`/`)

- The layout follows the mock:
  - a hero with the eyebrow "LIFS Tools · Lipid nomenclature" and the title "Harmonize lipid names".
    The hero picture is the Goslin logo at 160 px (6.5). The app bar keeps the shared LIFS mark,
  - two columns: the form card on the left; on the right the cards "Accepted notations", "For
    scripts" and "How to cite",
  - a condensed "About Goslin" section below.
- The form card has:
  - a toolbar with "Load example names", "Upload a file…" and "Clear",
  - the text area (monospace, about 16 rows, resizable),
  - a live count, "N names · up to 5,000",
  - the "Skip invalid names" checkbox (checked) with its help line,
  - the filled button "Harmonize".
- **"Load example names" works without JavaScript.** It is a link to `/?example`, which renders the
  page with the text area prefilled. JavaScript makes it instant.
- **File reading is client-side**, through the File API, into the text area. The size limit shown is
  `spring.servlet.multipart.max-file-size`: 512 KB by default, 16 MB in production. The mock's
  "10 MB" is a placeholder.
  - Without JavaScript, "Upload a file…" falls back to the current `/validatefile` post, with the
    same line-number rules.
- **Limits:** more than `jgoslin.maxItemsInRequest` (5,000) non-blank lines brings the page back
  with the text intact and an error callout.
- **"Skip invalid names" unchecked**, with some names failing: the input page comes back with the
  text intact and an error callout listing `line N: name: message` for each failure. Nothing is
  stored.
- The development-version warning on SNAPSHOT builds stays (6.6).

### 6.3 Results: storage, addresses, re-run

- `POST /validate` parses the names, stores the result under a new id and redirects (303) to
  `GET /result/{id}`. This is post/redirect/get.
- **Storage is the existing `StorageService` from lifs-webapps-common, on the shared volume.**
  - `storage.location` points to the shared volume in production, mounted on every replica.
  - Any replica can then serve any result, and results survive redeploys until the existing cleanup
    removes them (`minCleanupAge=7` days).
- **Stored per result id:**
  - the input entries, as the source of truth: line, original text, current text, a corrected flag
    and a removed flag,
  - the TSV.
  - This needs lifs-webapps-common 2.0.1 (see below).
- **The fix in lifs-webapps-common 2.0.1:**
  - Today `FileSystemStorageService` stores every file-type slot in the session folder under a random
    name. `load(id, slot)` returns the first file it finds, whatever the slot, and nothing can
    replace a file.
  - 2.0.1 stores each slot under a name derived from the slot, so `load(id, slot)` returns that
    slot's file. Storing to a slot again replaces its file. (`WebDavStorageService` is an unimplemented
    stub that throws everywhere; it stays as it is.)
  - Files that 2.0.0 stored under random names, including uploads under the user's file name, are
    still found.
  - Existing callers keep working. The method signatures stay the same.
- **Rendering `/result/{id}`:**
  - It parses the stored entries' current texts.
  - A bounded in-memory cache per replica, keyed by id and a revision number stored with the
    entries, avoids parsing again on every view.
  - A cache miss, such as the other replica or a restart, just parses again. Parsing is
    deterministic.
- **An expired or unknown id** shows the callout "Result not found or expired. Results are kept for 7
  days.", with a link back to the input page.
- **`POST /result/{id}/rerun`** takes the edited names keyed by line number:
  - It updates those entries' current text and sets their corrected flag.
  - It increments the revision, re-parses, rewrites the TSV and redirects to `/result/{id}`.
  - The redirect carries the outcome as query parameters (`?rerun=2,1`), not as flash attributes,
    because flash attributes live in the HTTP session of a single replica. The page renders the
    callout "N corrected names were harmonized and added to the table. M names still need
    attention." from them.
- **`POST /result/{id}/remove`** with `line=N` sets the removed flag. Removed entries leave the
  table, the fix card and the TSV.
- **Concurrency:** two re-runs on the same result at once are unlikely. The rule is "the last write
  wins", made safe by writing to a temporary file and moving it into place. Locking is not needed.
- Ids are random UUIDs from the existing `SessionIdGenerator`. Anyone holding the link can view or
  change the result, as today with download links.
- `/download/{id}/OUTPUT_TSV_FILE` keeps working.

### 6.4 Result page (`/result/{id}`)

- **Header:**
  - eyebrow "Result · N names submitted", title "Harmonized lipid names",
  - the filled "Download table (TSV)" button with a download icon,
  - a text button "New input".
- **Banner:** `lifs-callout--ok` when all names parsed, `--warn` otherwise. It shows the counts as
  `lifs-lvl` chips.
- **"Correct these names" card**, shown only when names failed. The card has an error accent on its
  left border. Each row shows:
  - `line N`,
  - an input with the name's current text,
  - the stop marker and a message in plain language (see below),
  - "Remove".

  Below the rows: the button "Re-run corrected names" and the note "The N harmonized names are kept."
- **Where parsing stopped (webapp only; jgoslin and REST unchanged):**
  - For failed names only, the webapp runs each grammar's parser itself (Goslin, LIPID MAPS,
    SwissLipids, HMDB, FattyAcids and the others `LipidParser` tries). It records how far each one
    got from its error handler. The furthest stop is the marker position.
  - This replaces reading a position from the message text. That text comes from the last grammar
    tried (HMDB), so it often points to the wrong place for shorthand or LIPID MAPS names.
  - The parsers report the longest complete part they recognised. An unclosed bracket therefore
    stops right after the head group (`PC(18:2-20:4` stops after `PC`). A character outside the
    grammars, such as an en dash or a non-breaking space, stops at 0. The message is chosen by these
    rules, in this order (amended after the plan review on 2026-11-26):
    1. **Unsupported character:** the first character outside printable ASCII is marked: "The
       character ‘–’ (position N) is not used in lipid names."
    2. **Brackets do not match:** "A closing bracket is missing." with a marked space at the end, or
       "There is a closing bracket too many." with that bracket marked.
    3. **Head group not recognised:** the stop is before the end of the head group, which is
       everything before the first space, `(` or digit. The whole name is marked: "Goslin does not
       recognise the head group. Check the supported lipid classes" (with a link).
    4. **Otherwise:** the text from the stop onwards is marked: "Not a valid name from character
       N+1 on."
  - Goslin's original message is in a "Details" disclosure under each row and in the TSV.
- **Table**
  - **Columns:** Line, Submitted, Harmonized name, Level, Category, Formula, Exact mass, Databases.
    Databases shows counts per database: `LM n`, `SL n`, `ChEBI n`, or "none".
  - **Corrected names:** "Submitted" shows the corrected text, with the original text on a second
    line, struck through. The row is marked with a "corrected" chip.
  - **Filters:** free text (matches submitted name, harmonized name, class and formula), Level and
    LIPID MAPS category.
  - **Sorting and paging:** column headers sort, line order by default. 25 rows per page, with
    Previous and Next.
  - **Details:** a ▸ / ▾ button with `aria-expanded` opens a detail row with what each result card
    shows today: grammar, adduct, LIPID MAPS category and class links, database entries as links,
    C atoms and double bonds, functional group counts, and the chain table.
- **Without JavaScript**, all rows render unfiltered and unpaged, with the details shown inline
  (`<details>`). The page stays usable, only longer.

### 6.5 Goslin logo and easter eggs

- `goslin/goslin.svg` is cleaned up (Inkscape metadata removed, the same geometry) and goes into
  `templates/fragments/goslin-logo.html` as an inline SVG fragment. Inline is needed for the click
  effect.
  - The head triangle and the eye are in their own groups, as in the easter-egg mock.
  - It is also saved as `static/img/goslin-logo.svg` for the documentation page and future use.
- **A · Blink:** a CSS animation on the eye, `scaleY` to about 0.12 for a moment every 6 s. Each
  page load gets a random start offset.
- **C · Honk:** clicking the logo:
  - plays a 0.5 s peck (the head rotates about its lower left corner),
  - shows a speech bubble with "Honk!" for about 1.4 s,
  - on every fifth click shows "PC 16:0/18:1(9Z), please." instead.
  - No sound.
- **The logo stays decorative.** It is not a button, has no tab stop, and the bubble is
  `aria-hidden`. The picture has `role="img"` and `aria-label="Goslin logo"`.
- **`prefers-reduced-motion: reduce`** turns off the blink and the peck. The bubble still appears.
- The code goes in `static/js/goslin.js` and `static/css/goslin.css`, about 20 lines each.

### 6.6 Wording, layout and templates

- **Wording:**
  - Every user-facing "Normalize", "Normalizer" or "Normalized" in templates and message bundles
    becomes "Harmonize", "Harmonizer" or "Harmonized". That includes `main.title`, the overview
    text, the manual and future news entries. Past news stays as written.
  - Unchanged: REST paths, JSON field names (`normalizedName`), data file names
    (`*-normalized.tsv`), and existing TSV column names.
- **TSV:**
  - It gets a new first column, `Line`, and a new column `Corrected Name` after `Original Name`.
    `Corrected Name` is empty unless the name was corrected.
  - `Original Name` always holds the text the user submitted. `Normalized Name` and all other
    existing columns keep their names and order.
  - Names that still fail are included, with their message.
  - These changes go in the release notes.
- New UI strings go into `i18n/tool/messages.properties` and `messages_de.properties`.
- **Pages** use `lifs/layout :: head`, `app-bar('Goslin', ~{fragments/nav :: nav-items}, ~{})`,
  `messages`, `tracking` (6.7) and `footer` from lifs-webapps-common 2.0.1, like
  `lifs-apps/src/main/resources/templates/index.html`.
- **Navigation items:** Harmonize (`/`), Documentation, REST API (Swagger UI). The layout adds
  "LIFS Tools ↗" and GitHub itself.
- **Messages** (lifs-webapps-common 2.0.1):
  - `lifs/layout :: messages` also renders a model attribute `messages`, a list of message, level
    and title. The single `message` and `messageLevel` keep working.
  - Goslin uses the list. A page can then show the SNAPSHOT warning, the re-run outcome and an error
    at the same time.
- **CSS and JavaScript:** Goslin-specific CSS goes in `static/css/goslin.css`, built on the LIFS
  tokens only, with no new colours. JavaScript goes in `static/js/goslin.js`: plain JavaScript, no
  libraries.
- **Documentation page:**
  - It uses LIFS headings, `lifs-table` and a plain in-page table of contents, replacing
    bootstrap-toc.
  - The lipid classes table loses DataTables and gets the same small text filter as the result table.
  - The manual's "Interactive Usage" section is rewritten for the new input and result pages. Its
    five screenshots (`static/img/goslin-webapp-*.png`) are replaced by new ones from the Playwright
    run (7).
  - The REST part of the manual stays.
- **Accessibility**, to the level of lifs-apps:
  - labels on every field, `aria-live` for the count and the callouts,
  - status is never shown by colour alone (the `lifs-lvl` dot plus text),
  - everything works with the keyboard.

### 6.7 Matomo tracking

- **Today:**
  - Goslin loads `lifs-matomo-opt-in.js` from lifs-webapps-common. That script needs jQuery and
    Bootstrap. It shows a cookie banner with Accept and Deny, stores consent in the cookie
    `lifs-cookies-accepted` for 30 days, and only tracks after consent (`requireConsent`).
  - A `<noscript>` notice embeds Matomo's opt-out page.
  - lifs-apps dropped all of this in `9feaf94`.
- **lifs-webapps-common 2.0.1:**
  - It adds a `lifs/layout :: tracking` fragment and `static/lifs/js/lifs-tracking.js`, in plain
    JavaScript.
  - Same behaviour, banner text, cookie name and duration as today. Consent given in one LIFS app
    on the same domain therefore carries over.
  - The banner is a `lifs-callout--info` at the bottom of the viewport, with "Accept" (filled) and
    "Deny" (outlined) buttons and a link to the privacy policy.
  - The fragment renders only when `page.gaId` is set. It reads the site id from `page` (`ga.id`).
    The tracker base `https://lifs-tools.org/matomo/` is fixed in the fragment (`data-tracker-url`);
    `ga.url` stays the server-side `matomo.php` endpoint of `PiwikTracker`. It keeps the
    `<noscript>` opt-out.
  - The old jQuery script is kept but marked deprecated, so apps that have not migrated keep
    working.
- **Goslin** includes the fragment on all pages. Its site id stays `ga.id=10`.
- **lifs-apps** includes the fragment again on its pages. It uses its existing `gaId` setting, the
  one its statistics page already uses.

### 6.8 Platform upgrade (goslin-webapp)

- **Baseline:** master actually runs on Spring Boot 2.7.18 (Spring 5.3, Tomcat 9, Thymeleaf 3.0,
  `javax.*`). The parent pom of lifs-webapps-common 1.1.7 overrides the `spring.boot.version` 3.4.8
  set in Goslin's pom. So the upgrade spans 2.7 → 4.2, not 3.4 → 4.2.
- Java 25; Spring Boot 4.2.0; lifs-webapps-common 2.0.1. Follow the migration commits:
  lifs-webapps-common `8f7a261` and lifs-apps `9feaf94` / `4308b43`.
  - `jakarta.*`, Jackson 3 (`tools.jackson`) for the JSON and CSV code (`ElementTableSerializer`,
    TSV writer), Spring Boot 4.2 property names.
- springdoc: replace `springdoc-openapi-ui` 1.6.15 with the springdoc release line that supports
  Spring Boot 4. Keep `/swagger-ui/index.html` and `/v3/api-docs`.
- **Remove:** the websocket starter (unused), the webjars for Bootstrap and jQuery, DataTables,
  open-iconic, bootstrap-toc, the layout dialect, and Guava if it is unused after the migration.
- Docker base `eclipse-temurin:25-jre-alpine`. CI on JDK 25, Temurin, modelled on lifs-apps'
  `ci.yml`.
- Add `.superpowers/` to `.gitignore`.

## 7. Testing

- **Unit tests:**
  - line numbering (blank lines, `\r\n`, whitespace collapse, duplicates, the 5,000 limit),
  - merging after re-run and removal, including revision and line order,
  - TSV columns (`Line`, `Corrected Name`, unchanged existing columns),
  - the furthest-stop position and the four message rules, with names from each grammar,
  - REST message texts unchanged.
- **lifs-webapps-common:**
  - storage slots no longer collide, and storing again replaces the file (file system and WebDAV),
  - the messages list renders.
- **Controller tests (MockMvc):**
  - `/` and `/?example`,
  - `/validate` redirecting to `/result/{id}`,
  - "skip invalid" unchecked coming back with the line list,
  - rerun and remove with the outcome callout,
  - an expired id,
  - a result rendered by a second application context that shares the storage folder (the replica
    case),
  - REST endpoints unchanged (the existing `LipidNameValidationTest`).
- **End-to-end (Playwright, as in `lifs-apps/e2e`):**
  - paste, harmonize, failed names on top, fix, re-run, download the TSV,
  - consent banner Accept and Deny,
  - the honk on click,
  - the run captures the new manual screenshots.

## 8. Left to the builder

- Class and method names, the form-object shape for the text area and the rerun form (the REST
  `ValidationRequest` must stay compatible), the cache implementation and size, and the file format
  of the stored entries.
- The exact list of grammars to try for the stop position, taken from what `LipidParser` uses.
- German wording for new strings.
- Whether filtering, sorting and paging render rows from an embedded JSON block or from the rendered
  `<tr>` elements.
- Whether the e2e tests run in CI on every pull request, as in lifs-apps, or only locally at first.
