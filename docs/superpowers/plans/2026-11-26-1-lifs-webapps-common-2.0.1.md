# lifs-webapps-common 2.0.1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the shared library what Goslin's redesign needs: storage slots that do not collide and can be replaced, several page messages at once, and Matomo consent without jQuery.

**Architecture:** Three independent changes to `/Users/nilshoffmann/Projects/github.com/lifs-tools/lifs-webapps-common`. In `FileSystemStorageService`, file slots get a name derived from the slot. The Thymeleaf fragments in `templates/lifs/layout.html` get a `messages` list and a new `tracking` fragment. A plain-JS consent script goes in `static/lifs/js/lifs-tracking.js`. Method signatures stay the same.

**Tech Stack:** Java 25, Spring Boot 4.2.0, Thymeleaf, JUnit 5, plain JavaScript (no libraries).

**Spec:** `/Users/nilshoffmann/Projects/github.com/lifs-tools/goslin-webapp/docs/superpowers/specs/2026-11-26-goslin-lifs-redesign-design.md` (sections 2, 6.3, 6.6, 6.7)

**Plan 1 of 3.** Plan 2 (goslin-webapp) consumes this release. Plan 3 (lifs-apps, compose) consumes it too.

## Global Constraints

- Work on branch `feature/2.0.1-storage-messages-tracking` from `master` (currently `2.0.1-SNAPSHOT`).
- No public method signature of `StorageService`, `DefaultPage` or existing fragments changes. Apps on 2.0.0 must build against 2.0.1 unchanged.
- Consent cookie: name `lifs-cookies-accepted`, value `on`, 30 days, `path=/`, `SameSite=Lax`. Copied from `static/js/lifs-matomo-opt-in.js`.
- Matomo base URL `https://lifs-tools.org/matomo/`; calls `requireConsent`, `trackPageView`, `enableLinkTracking`, `setTrackerUrl(<base>matomo.php)`, `setSiteId(<gaId>)`, then `setConsentGiven` once consent exists.
- Banner text: copy `bannerMessage` and `bannerMessageDnt` verbatim from `lifs-matomo-opt-in.js`. The privacy link goes to `https://lifs-tools.org/imprint-privacy-policy.html` (the link the footer uses).
- No new colours: use the tokens in `static/lifs/css/lifs.css`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Sessions stored by 2.0.0, with random file names, must still load after the upgrade** (results within the 7-day retention). Test `loadFindsLegacyRandomlyNamedFile` in Task 1.
2. **Two replicas writing the same slot at once must leave one complete file, never a partial one.** Test `concurrentStoresLeaveOneCompleteFile` in Task 1.
3. **A page with `messages` empty or null and no `message` must not render an empty callout stack.** Test `rendersNothingWithoutMessages` in Task 2.
4. **A browser with "do not track" must never load matomo.js, even with the consent cookie set.** Covered in Plan 2, Task 9 (e2e `consent.spec.ts`), because this repository has no browser tests.
5. **`gaId` empty string (not null) must render no tracking script.** Test `noTrackingWhenGaIdBlank` in Task 3.

---

### Task 1: Slot-named, replaceable files in `FileSystemStorageService`

**Files:**
- Modify: `src/main/java/org/lifstools/lifs/webapps/common/service/storage/FileSystemStorageService.java` (`store(String, UUID, StorageServiceSlot)` and `load(UUID, StorageServiceSlot)`)
- Test: `src/test/java/org/lifstools/lifs/webapps/common/service/storage/FileSystemStorageServiceTest.java` (new; the repo has no `src/test` yet)

**Interfaces:**
- Produces: for a slot with `isDirectory() == false`, `store(String content, UUID id, slot)` writes `<root>/<id>/<slot.getName()><slot.getExtension()>`. Storing again replaces that file atomically. `load(id, slot)` returns a `UserSessionFile` whose filename is that name. Plan 2 relies on this.

- [ ] **Step 1: Write the failing tests** (JUnit 5, `@TempDir Path root`, `StorageProperties` with `setLocation(root.toString())`, a test-local `enum Slots implements StorageServiceSlot { TSV(".tsv", false), JSON(".json", false), DIR("", true) }`)

```java
@Test void twoFileSlotsDoNotCollide()            // store "a" in TSV, "b" in JSON → load(id,TSV) file content "a", load(id,JSON) content "b"
@Test void storingAgainReplacesTheFile()         // store "a" then "b" in TSV → exactly one file in <root>/<id>, content "b"
@Test void storedFileIsNamedAfterTheSlot()       // load(id,TSV).getFilename() equals "TSV.tsv"
@Test void loadFindsLegacyRandomlyNamedFile()    // write <root>/<id>/<random-uuid>.tsv by hand → load(id,TSV) returns that file
@Test void concurrentStoresLeaveOneCompleteFile() // 8 threads store 64 KB strings of 'a'..'h' into TSV → file is exactly one of them, length 65536
@Test void loadFromMissingSessionThrowsNotFound() // load(UUID.randomUUID(), TSV) → StorageFileNotFoundException (today: StorageException or NoSuchElementException)
@Test void twoSlotsWithSameExtensionDoNotCollide() // slots TSV(".tsv") and TSV2(".tsv"): store "a" and "b" → each loads its own content
@Test void loadFindsMultipartUploadWithUserFilename() // store(MockMultipartFile "data.txt", id, TSV) → load(id, TSV).getFilename() == "data.txt" (as in 2.0.0)
```

- [ ] **Step 2: Run them to see them fail**

Run: `./mvnw -B -q test -Dtest=FileSystemStorageServiceTest`
Expected: `twoFileSlotsDoNotCollide`, `storingAgainReplacesTheFile` and `storedFileIsNamedAfterTheSlot` FAIL.

- [ ] **Step 3: Implement**
  - In `store(String, …)` for non-directory slots: the file name is `slot.getName() + slot.getExtension()`. Write to a temp file in the same folder (`Files.createTempFile(dir, ".", ".tmp")`), then `Files.move(tmp, target, REPLACE_EXISTING, ATOMIC_MOVE)`.
  - Directory slots keep `UUID.randomUUID() + ext` inside the slot folder.
  - In `load(id, slot)` for non-directory slots: if `<id>/<slot.getName()><ext>` exists, return it. Otherwise fall back to the legacy behaviour: the first regular file in the session folder that does not end in `.tmp`, preferring one that ends with `slot.getExtension()`. That covers 2.0.0's random names and uploads stored under the user's file name. The service has no registry of other slots, so it cannot exclude their files; mixing legacy and slot-named files in one session does not happen in practice.
  - `WebDavStorageService` is an unimplemented stub that throws everywhere. Leave it as it is. If there is none, or the session folder does not exist, throw `StorageFileNotFoundException`. Today it throws `NoSuchElementException` from `findFirst().get()`, or `StorageException` for a missing folder.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./mvnw -B -q test -Dtest=FileSystemStorageServiceTest`
Expected: 8 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lifstools/lifs/webapps/common/service/storage/FileSystemStorageService.java src/test
git commit -m "Store file slots under the slot's name and replace them atomically"
```

### Task 2: A list of page messages in `lifs/layout :: messages`

**Files:**
- Create: `src/main/java/org/lifstools/lifs/webapps/common/domain/PageMessage.java`
- Modify: `src/main/resources/templates/lifs/layout.html` (fragment `messages`)
- Test: `src/test/java/org/lifstools/lifs/webapps/common/templates/LayoutFragmentsTest.java`
- Test helper: `src/test/java/org/lifstools/lifs/webapps/common/templates/FragmentRenderer.java`

**Interfaces:**
- Produces: `public record PageMessage(Level level, String title, String text)`, with `public enum Level { INFO, OK, WARN, ERROR }` nested and factories `PageMessage.info(String text)`, `ok(…)`, `warn(…)`, `error(…)` (title null). There are also overloads `warn(String title, String text)` and `error(String title, String text)`.
- Produces: model attribute **`messages`** (`List<PageMessage>`), rendered by `lifs/layout :: messages` after the system message and the single `message`. Each entry renders as `<div class="lifs-callout lifs-callout--{info|ok|warn|error}" role="{alert for ERROR, status otherwise}">`. It has an optional `<b>` title, the text, and a dismiss button.
- Produces (test helper): `FragmentRenderer.render(String template, String fragment, Map<String,Object> model) -> org.jsoup.nodes.Document`. It uses `SpringTemplateEngine` with a `ClassLoaderTemplateResolver` (prefix `templates/`, suffix `.html`) and a `WebContext` built from `MockHttpServletRequest` and `MockServletContext` through `JakartaServletWebApplication`, so `param.*` resolves. Add `org.jsoup:jsoup` (test scope) to the pom.

- [ ] **Step 1: Write the failing tests**

```java
@Test void rendersEveryMessageInOrder()   // messages=[warn("Dev build"), ok("2 corrected"), error("Bad", "line 3")] → 3 .lifs-callout, classes --warn, --ok, --error, in that order; the error has role=alert and <b>Bad</b>
@Test void keepsSingleMessageWorking()    // message="Old style", messageLevel="alert-warning" → one .lifs-callout--warn with text "Old style"
@Test void rendersNothingWithoutMessages() // no page, no message, messages=List.of() → no element with class lifs-stack
```

- [ ] **Step 2: Run them to see them fail**

Run: `./mvnw -B -q test -Dtest=LayoutFragmentsTest`
Expected: FAIL (`PageMessage` does not exist).

- [ ] **Step 3: Implement** `PageMessage`, and extend the fragment's `th:if` with `or (messages != null and !#lists.isEmpty(messages))` plus a `th:each` block. Map the level to the CSS suffix with `${#strings.toLowerCase(m.level)}`.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./mvnw -B -q test -Dtest=LayoutFragmentsTest`
Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/java/org/lifstools/lifs/webapps/common/domain/PageMessage.java src/main/resources/templates/lifs/layout.html src/test
git commit -m "Render a list of page messages in lifs/layout :: messages"
```

### Task 3: Matomo consent without jQuery (`lifs/layout :: tracking`)

**Files:**
- Create: `src/main/resources/static/lifs/js/lifs-tracking.js`
- Modify: `src/main/resources/templates/lifs/layout.html` (new fragment `tracking`, and update the usage comment at the top)
- Modify: `src/main/resources/static/lifs/css/lifs.css` (new section `/* ---------- consent banner ---------- */`)
- Modify: `src/main/resources/static/js/lifs-matomo-opt-in.js` (a header comment only: `@deprecated since 2.0.1, use lifs/layout :: tracking`)
- Test: `src/test/java/org/lifstools/lifs/webapps/common/templates/LayoutFragmentsTest.java`

**Interfaces:**
- Consumes: `FragmentRenderer` from Task 2.
- Produces: fragment `tracking`, rendered only when `page != null and page.gaId != null and page.gaId != ''`:
  - `<script src="@{/lifs/js/lifs-tracking.js}" defer data-lifs-tracking data-site-id="${page.gaId}" data-tracker-url="https://lifs-tools.org/matomo/" data-privacy-url="https://lifs-tools.org/imprint-privacy-policy.html"></script>`
  - `<noscript>` with the Matomo opt-out iframe: `https://lifs-tools.org/matomo/index.php?module=CoreAdminHome&action=optOut&idsite=${page.gaId}`, as in Goslin's old `fragments/nav.html`.
  - Apps include it right before `</body>`: `<th:block th:replace="~{lifs/layout :: tracking}"></th:block>`.
- Produces (DOM contract for the Plan 2 e2e tests): the banner is `<div class="lifs-consent lifs-callout lifs-callout--info" role="region" aria-label="Cookie consent" data-testid="consent-banner">`. Its buttons have `data-testid="consent-accept"` and `"consent-deny"`. The DNT variant has `data-testid="consent-dnt"` and only a dismiss button. Matomo is loaded by appending `<script src="<tracker-url>matomo.js" async>`.

- [ ] **Step 1: Write the failing tests**

```java
@Test void tracksWithSiteIdFromPage()  // page.gaId="10" → script[data-lifs-tracking][data-site-id=10] present, noscript iframe src contains "idsite=10"
@Test void noTrackingWithoutGaId()     // page.gaId=null → no script[data-lifs-tracking], no noscript
@Test void noTrackingWhenGaIdBlank()   // page.gaId="" → no script[data-lifs-tracking]
```

- [ ] **Step 2: Run them to see them fail**

Run: `./mvnw -B -q test -Dtest=LayoutFragmentsTest`
Expected: the 3 new tests FAIL.

- [ ] **Step 3: Implement the fragment and `lifs-tracking.js`**
  - The script is an IIFE that reads its settings from `document.currentScript.dataset`.
  - Flow:
    - DNT set (same detection as `checkDnt` in the old script) → show the DNT notice. Never load Matomo.
    - Cookie `lifs-cookies-accepted=on` → load Matomo and push `setConsentGiven`.
    - Otherwise → show the banner. **Accept** sets the cookie, loads Matomo, pushes `setConsentGiven` and removes the banner (no page reload). **Deny** removes the banner for this page only, as today.
  - The banner is fixed at the bottom of the viewport, full width, with `max-width: var(--lifs-content-width)` content. "Accept" is `lifs-btn lifs-btn--filled lifs-btn--small`, "Deny" is `lifs-btn lifs-btn--outlined lifs-btn--small`.
  - Under `prefers-reduced-motion` nothing animates. The banner has no animation anyway.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./mvnw -B -q test -Dtest=LayoutFragmentsTest`
Expected: 6 tests PASS.

- [ ] **Step 5: Check the script by hand** in a throwaway HTML page that loads `lifs.css` and the script with `data-site-id="1"`. You should see: the banner shows, Accept adds the `matomo.js` script tag and hides the banner, a reload shows no banner, and with the cookie cleared Deny hides it.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/lifs src/main/resources/templates/lifs/layout.html src/main/resources/static/js/lifs-matomo-opt-in.js src/test
git commit -m "Add Matomo consent without jQuery as lifs/layout :: tracking"
```

### Task 4: Verify and prepare the 2.0.1 release

**Files:**
- Modify: `pom.xml` only through the release workflow (version `2.0.1-SNAPSHOT` → `2.0.1`)

- [ ] **Step 1: Run the full build**

Run: `./mvnw -B verify`
Expected: BUILD SUCCESS, 14 tests, 0 failures.

- [ ] **Step 2: Install the snapshot locally so that Plan 2 can build against it**

Run: `./mvnw -B -q install -DskipTests`
Expected: `~/.m2/repository/org/lifs-tools/lifs-webapps-common/2.0.1-SNAPSHOT/` exists.

- [ ] **Step 3: Push the branch and open a pull request.** Ask Nils before running the release workflow (`.github/workflows/release.yml`). A release publishes to m.lifs-tools.org and cannot be taken back. Plan 2 Task 1 switches from `2.0.1-SNAPSHOT` to `2.0.1` once it is released.
