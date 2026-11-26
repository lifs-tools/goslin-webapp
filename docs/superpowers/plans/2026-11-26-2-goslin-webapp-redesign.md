# Goslin Webapp Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move goslin-webapp to Java 25, Spring Boot 4.2 and lifs-webapps-common 2.0.1. Rebuild its pages in the LIFS design: one text box for input, a result page at `/result/{id}` with failed names first, re-run, original line numbers, a filterable table, and the Goslin logo with its easter eggs.

**Architecture:** Server-rendered Thymeleaf pages built on the shared `lifs/layout` fragments, plus one plain-JS file (`static/js/goslin.js`).
- Input is turned into line-numbered `NameEntry` records once.
- A `ResultStore` persists them as JSON through `StorageService` (shared volume in production).
- `ResultService` parses entries into a `ResultView`, cached per id and revision, and writes the TSV.
- Re-run and remove are form posts that redirect back to `/result/{id}`.
- The REST API and `LipidNameValidationService` messages are not changed.

**Tech Stack:** Java 25, Spring Boot 4.2.0 (Spring MVC, Thymeleaf), Jackson 3 (`tools.jackson`), jgoslin 2.2.1, lifs-webapps-common 2.0.1, springdoc-openapi 3.x, JUnit 5 + MockMvc + jsoup, Playwright (e2e).

**Spec:** `/Users/nilshoffmann/Projects/github.com/lifs-tools/goslin-webapp/docs/superpowers/specs/2026-11-26-goslin-lifs-redesign-design.md`. The mocks are next to it, the `*-mock.html` files.

**Plan 2 of 3.** It needs Plan 1 (lifs-webapps-common 2.0.1, at least installed locally as `2.0.1-SNAPSHOT`).

## Global Constraints

- Work in a new git worktree on branch `feature/lifs-design-2.3.0`, made from `master` with superpowers:using-git-worktrees. The main checkout has Nils' uncommitted test changes; leave them alone. Set the project version to `2.3.0-SNAPSHOT`.
- The first commit adds the spec, both mocks and these plans (`docs/superpowers/`), and `.superpowers/` to `.gitignore`.
- **REST stays as it is:** paths `/rest/validate`, `/rest/grammars`, `/rest/lipidlevels`; `ValidationRequest`, `ValidationResult` JSON fields (including `normalizedName`); and message texts from `LipidNameValidationService`. Swagger UI stays at `/swagger-ui/index.html`, OpenAPI at `/v3/api-docs`.
- **Wording:** "Harmonize", "Harmonizer" and "Harmonized" replace "Normalize", "Normalizer" and "Normalized" in user-facing text. They do NOT replace them in REST, JSON, TSV column names, data file names or past news entries.
- **Line numbers:** 1-based line in the submitted text. Blank lines count but are not submitted. `\r\n`, `\n` and `\r` end a line. A leading byte-order mark (`\uFEFF`) is dropped and non-breaking spaces (`\u00A0`) count as spaces. Each line is trimmed and whitespace runs collapse to one space. Duplicates are separate entries. A line number never changes after creation.
- Limit: `jgoslin.maxItemsInRequest` (5000) non-blank lines.
- TSV columns: `Line`, `Normalized Name`, `Original Name`, `Corrected Name`, then the existing columns in their current order (`Grammar`, `Validation Messages`, `Adduct`, …). Failed names are included.
- **Easter egg:**
  - The blink scales the eye to `scaleY(0.12)` once every 6 s, with a random start offset.
  - The peck takes 520 ms and rotates the head about its lower left corner. The bubble shows for 1.4 s with "Honk!". Every 5th click it shows "PC 16:0/18:1(9Z), please." instead.
  - No sound. `prefers-reduced-motion: reduce` turns off the blink and the peck; the bubble still appears.
  - The logo is decorative: no tab stop, the bubble `aria-hidden`, the SVG `role="img" aria-label="Goslin logo"`.
- CSS uses LIFS tokens only. JS is plain, with no libraries; jQuery, Bootstrap, DataTables, open-iconic, bootstrap-toc and the layout dialect are removed.
- Tests never send tracking data: `src/test/resources/application-test.properties` and `e2e/scripts/start-app.sh` set `ga.url=http://127.0.0.1:9/matomo.php`, a closed port, for the server-side `PiwikTracker`. Keep `ga.id=10` so the consent script still renders.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Text pasted from Windows (`\r\n`) with trailing blank lines** must give the line numbers the user's editor shows. Test `windowsLineEndingsAndTrailingBlankLines` in Task 2.
2. **The same name on two lines**: correcting one of them must not touch the other. Test `duplicateNamesAreSeparateEntries` in Task 5.
3. **A re-run edit that is blank or unchanged** is ignored. It must not wipe the name or mark it corrected. Test `blankOrUnchangedEditIsIgnored` in Task 5.
4. **Re-run or remove posted for a result that expired** in the meantime must show the not-found page with HTTP 404, not a 500. Test `rerunOnExpiredResultShowsNotFound` in Task 7.
5. **Names containing `<`, `&` or quotes** (for example `<script>alert(1)</script>`) must appear escaped in the fix card, the table, the `data-search` attribute and the input text area. Test `namesAreEscapedEverywhere` in Task 7.

---

### Task 1: Platform upgrade (Java 25, Spring Boot 4.2, lifs-webapps-common 2.0.1)

**Baseline:** master runs on **Spring Boot 2.7.18** (Spring 5.3, Tomcat 9, Thymeleaf 3.0, `javax.*`). The parent of lifs-webapps-common 1.1.7 overrides the `spring.boot.version` 3.4.8 in Goslin's pom. So this is a 2.7 → 4.2 jump. The lifs-apps commits show the 3 → 4 step; the 2.7 → 3 step adds `javax` → `jakarta` (servlet, validation) and Thymeleaf 3.1, which has no `#request`, `#session` or `#servletContext` expression objects.

**Files:**
- Modify: `pom.xml` (also replace the `javax.validation:validation-api` dependency with `spring-boot-starter-validation`'s Jakarta one), `src/main/resources/application.properties`, `src/main/java/org/lifstools/jgoslin/webapp/domain/ElementTableSerializer.java`, `src/main/java/org/lifstools/jgoslin/webapp/services/ExternalDatabaseMappingLoader.java`, `src/main/java/org/lifstools/jgoslin/webapp/config/TomcatCustomizer.java`, `src/main/java/org/lifstools/jgoslin/webapp/config/WebConfig.java`, `src/main/java/org/lifstools/jgoslin/webapp/controller/LifsErrorController.java`, every `javax.*` import in `src/main` and `src/test` (the tests autowire a `com.fasterxml` `ObjectMapper`; switch them to `tools.jackson.databind.json.JsonMapper`), templates using removed Thymeleaf 3.0 expression objects, `.gitignore`
- Replace: `.github/workflows/maven.yml` with `.github/workflows/ci.yml` and `.github/workflows/release.yml`, modelled on `/Users/nilshoffmann/Projects/github.com/lifs-tools/lifs-apps/.github/workflows/`. Use the image `lifs/lifs-goslin-webapp` on `docker.lifs-tools.org`. Leave the e2e job out until Task 9.
- Create: `docs/superpowers/` (copy the spec, both mocks and the three plans from the main checkout)
- Test: `src/test/java/org/lifstools/jgoslin/webapp/rest/ApiDocsTest.java`, `src/test/java/org/lifstools/jgoslin/webapp/controller/BaselinePagesTest.java`

**Interfaces:**
- Produces: a building app on the new platform. The existing views still render. The old templates may look unstyled until Tasks 6–8.

- [ ] **Step 1: Write the failing test** `ApiDocsTest`, modelled on `LipidNameValidationTest` (`@SpringBootTest`, `@AutoConfigureMockMvc`):

```java
@Test void swaggerUiIsServed()  // GET /swagger-ui/index.html → 200
@Test void apiDocsListRestPaths() // GET /v3/api-docs → 200, body contains "/rest/validate", "/rest/grammars", "/rest/lipidlevels"
// BaselinePagesTest (@SpringBootTest + MockMvc, profiles "test","dev")
@Test void homeRenders()           // GET / → 200, view "index"
@Test void documentationRenders()  // GET /documentation → 200, view "documentation"
```

- [ ] **Step 2: Run it to see it pass on the old platform (the baseline)**

Run: `./mvnw -B -q test -Dtest='ApiDocsTest,BaselinePagesTest'`
Expected: PASS. If `/swagger-ui/index.html` fails here, fix the expectation to what the old platform serves and note it in the commit.

- [ ] **Step 3: Upgrade, following lifs-apps commits `9feaf94` and `4308b43`**
  - **Versions:** `java.version`/`maven.compiler.release` 25; `spring.boot.version` 4.2.0; `lifs.webapps.common.version` `2.0.1-SNAPSHOT` (`2.0.1` once released); `docker.base.image.version` `25-jre-alpine`.
  - **springdoc:** replace `org.springdoc:springdoc-openapi-ui` with `org.springdoc:springdoc-openapi-starter-webmvc-ui`. Use the newest 3.x release on Maven Central that states Spring Boot 4 support.
  - **Remove** `spring-boot-starter-websocket`, the `org.webjars` jquery and bootstrap dependencies, `nz.net.ultraq` layout dialect (if present), and Guava if nothing imports `com.google.common` after the migration. `Files.getFileExtension` in the controller is Guava; replace it with `StringUtils.getFilenameExtension`.
  - **Jackson 3:** `ElementTableSerializer` becomes a `tools.jackson.databind.ValueSerializer<ElementTable>` with `@JacksonComponent`. `ExternalDatabaseMappingLoader` uses `tools.jackson.dataformat.csv.CsvMapper`/`CsvSchema`. `com.fasterxml.jackson.annotation.*` stays.
  - **Properties:** `server.error.*` → `spring.web.error.*`; `server.forward-headers-strategy=NATIVE` → `server.request.header.forwarded.strategy=web-server`; `server.tomcat.internal-proxies`, `protocol-header` and `remote-ip-header` → `server.tomcat.remoteip.*`; drop `spring.mvc.throw-exception-if-no-handler-found` (no longer bound; 404s are handled by default since Spring 6.1). Add `server.tomcat.max-parameter-count=12000` (Tomcat 11 defaults to 1,000 request parameters; re-run posts one per failed name, see Task 7). Check each name against the Spring Boot 4.2 property reference. Exclude `spring-boot-properties-migrator` from lifs-webapps-common as lifs-apps does. If the build uses Jib, add Jib's Spring Boot extension as lifs-apps did.
  - **`.gitignore`:** add `.superpowers/`.

- [ ] **Step 4: Run the whole build**

Run: `./mvnw -B verify`
Expected: BUILD SUCCESS, with `ApiDocsTest`, `BaselinePagesTest`, `LipidNameValidationTest` (REST), `LipidNameValidationControllerTest` and the service tests passing.

- [ ] **Step 5: Commit** the docs first (`git add docs .gitignore && git commit -m "Add the LIFS redesign spec, mocks and plans"`). Then commit the upgrade (`git commit -am "Move to Java 25, Spring Boot 4.2 and lifs-webapps-common 2.0.1"`, plus the new workflow files).

### Task 2: Line-numbered name entries

**Files:**
- Create: `src/main/java/org/lifstools/jgoslin/webapp/domain/NameEntry.java`, `src/main/java/org/lifstools/jgoslin/webapp/domain/NameEntries.java`, `src/main/java/org/lifstools/jgoslin/webapp/domain/TooManyNamesException.java`
- Test: `src/test/java/org/lifstools/jgoslin/webapp/domain/NameEntriesTest.java`

**Interfaces:**
- Produces: `public record NameEntry(int line, String original, String current, boolean corrected, boolean removed)`:
  - `NameEntry.of(int line, String text)`: original = current = text, both flags false,
  - `NameEntry withCorrection(String text)`: current = text, corrected = true,
  - `NameEntry asRemoved()`.
- Produces: `public final class NameEntries { public static List<NameEntry> fromText(String text, int maxNames) }`:
  - It throws `TooManyNamesException(int count, int max)` (a `RuntimeException` with getters) when the non-blank lines exceed `maxNames`.
  - `null` or blank text gives an empty list.

- [ ] **Step 1: Write the failing tests**

```java
@Test void numbersLinesFromOneAndSkipsBlankLines()      // "PC 32:0\n\nTG 52:2" → lines [1, 3]
@Test void windowsLineEndingsAndTrailingBlankLines()    // "a\r\n\r\nb\r\n\r\n" → [(1,"a"), (3,"b")]
@Test void oldMacLineEndings()                          // "a\rb" → lines [1, 2]
@Test void trimsAndCollapsesWhitespace()                // "  PE  16:0 /\t18:1 " → current "PE 16:0 / 18:1"
@Test void keepsDuplicatesAsSeparateEntries()           // "PC 32:0\nPC 32:0" → 2 entries, lines 1 and 2
@Test void rejectsMoreThanMax()                         // 3 non-blank lines, max 2 → TooManyNamesException with count 3, max 2
@Test void blankLinesDoNotCountTowardsMax()             // "a\n\n\nb", max 2 → 2 entries
@Test void correctionKeepsLineAndOriginal()             // NameEntry.of(34,"PC(18:2").withCorrection("PC(18:2)") → line 34, original "PC(18:2", corrected true
@Test void dropsByteOrderMarkAndNonBreakingSpaces()     // "\uFEFFPC\u00A032:0\nTG 52:2" → [(1,"PC 32:0"), (2,"TG 52:2")]
```

- [ ] **Step 2: Run them to see them fail**

Run: `./mvnw -B -q test -Dtest=NameEntriesTest`
Expected: FAIL (the classes do not exist).

- [ ] **Step 3: Implement.** Split with `text.split("\\r\\n|\\r|\\n", -1)`.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./mvnw -B -q test -Dtest=NameEntriesTest`
Expected: 9 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lifstools/jgoslin/webapp/domain/NameEntr* src/main/java/org/lifstools/jgoslin/webapp/domain/TooManyNamesException.java src/test/java/org/lifstools/jgoslin/webapp/domain/NameEntriesTest.java
git commit -m "Turn submitted text into line-numbered name entries"
```

### Task 3: Where parsing stopped, in plain language

**Files:**
- Create: `src/main/java/org/lifstools/jgoslin/webapp/domain/FailureExplanation.java`, `src/main/java/org/lifstools/jgoslin/webapp/services/ParseStopLocator.java`
- Test: `src/test/java/org/lifstools/jgoslin/webapp/services/ParseStopLocatorTest.java`

**Interfaces:**
- Produces: `public record FailureExplanation(int position, Hint hint, String originalMessage)`, with nested `enum Hint { UNSUPPORTED_CHARACTER, MISSING_CLOSING_BRACKET, EXTRA_CLOSING_BRACKET, HEAD_GROUP, AT_POSITION }`:
  - `int markFrom()`: 0 for `HEAD_GROUP`, otherwise `position`,
  - `int markTo(String name)`: `position + 1` for `UNSUPPORTED_CHARACTER` and `EXTRA_CLOSING_BRACKET`, otherwise `name.length()`,
  - `String marked(String name)`: `name.substring(markFrom(), markTo(name))`. For `MISSING_CLOSING_BRACKET` it is `""` (position = length), and the template shows a one-character marked space.
- Produces: `@Service public class ParseStopLocator`:
  - `ParseStopLocator()` builds the six parsers in `LipidParser`'s order: `ShorthandParser`, `FattyAcidParser`, `GoslinParser`, `LipidMapsParser`, `SwissLipidsParser`, `HmdbParser`. They share one `new KnownFunctionalGroups()`.
  - `int furthestStop(String name)`: for each parser, `parser.parse(name, handler = parser.newEventHandler(), false)`. Take `handler.getErrorMessage()`. If it is a non-empty prefix of `name`, its length is a candidate. Return the maximum candidate, or 0. The parsers report the longest *complete* part they recognised, so an unclosed bracket stops right after the head group (measured with jgoslin 2.2.1: `PC(18:2-20:4` → 2, `TG 16:0_18:1_18:2x` → 17, `PC 34:1x` → 7, `SM d18:1/24:0)` → 13, `Cholesterl` → 4, `Xyz 16:0` → 0, a name with an en dash → 0).
  - `FailureExplanation explain(String name, String originalMessage)` calls `classify(name, furthestStop(name), originalMessage)`.
  - `static FailureExplanation classify(String name, int position, String originalMessage)`. The rules come from spec 6.4 and apply in this order:

```java
// 1. first char c with c < 0x20 || c > 0x7E at index i         → UNSUPPORTED_CHARACTER, position i
// 2. count('(') > count(')')                                   → MISSING_CLOSING_BRACKET, position name.length()
//    a ')' at index i with no open '(' before it               → EXTRA_CLOSING_BRACKET, position i (the first such)
// 3. headGroupEnd = index of the first ' ', '(' or digit; name.length() if none
//    position < headGroupEnd                                   → HEAD_GROUP
// 4. otherwise                                                 → AT_POSITION, position
// (the same counting applies to '[' ']')
```
- Message texts (keys `fix.hint.*`), with `{0}` the 1-based character position and `{1}` the character:
  - `UNSUPPORTED_CHARACTER`: "The character ‘{1}’ (position {0}) is not used in lipid names."
  - `MISSING_CLOSING_BRACKET`: "A closing bracket is missing."
  - `EXTRA_CLOSING_BRACKET`: "There is a closing bracket too many."
  - `HEAD_GROUP`: "Goslin does not recognise the head group. Check the supported lipid classes."
  - `AT_POSITION`: "Not a valid name from character {0} on."

- [ ] **Step 1: Write the failing tests**

```java
@Test void unsupportedCharacterWins()        // classify("PC 16:0–18:1", 0, "m") → UNSUPPORTED_CHARACTER, position 7, marked "–"
@Test void missingClosingBracket()           // classify("PC(18:2-20:4", 2, "m") → MISSING_CLOSING_BRACKET, position 12, marked ""
@Test void extraClosingBracket()             // classify("SM d18:1/24:0)", 13, "m") → EXTRA_CLOSING_BRACKET, position 13, marked ")"
@Test void wholeWordIsHeadGroup()            // classify("Cholesterl", 4, "m") → HEAD_GROUP, marked "Cholesterl"
@Test void stopInsideHeadGroup()             // classify("Xyz 16:0", 0, "m") → HEAD_GROUP
@Test void stopAfterHeadGroupIsAtPosition()  // classify("TG 16:0_18:1_18:2x", 17, "m") → AT_POSITION, marked "x"
@Test void keepsOriginalMessage()            // classify(.., "Could not parse …").originalMessage() == "Could not parse …"
// real parsers, one name per grammar family, with the measured stops
@Test void realStopShorthand()               // furthestStop("PC 34:1x") == 7
@Test void realStopGoslinMolecular()         // furthestStop("TG 16:0_18:1_18:2x") == 17
@Test void realStopLipidMapsStyle()          // furthestStop("SM d18:1/24:0)") == 13
@Test void realStopUnclosedBracket()         // furthestStop("PC(18:2-20:4") == 2, explain(..).hint() == MISSING_CLOSING_BRACKET
@Test void realStopUnknownHeadGroup()        // explain("Cholesterl", "m").hint() == HEAD_GROUP
```

- [ ] **Step 2: Run them to see them fail**

Run: `./mvnw -B -q test -Dtest=ParseStopLocatorTest`
Expected: FAIL (the classes do not exist).

- [ ] **Step 3: Implement** `FailureExplanation` and `ParseStopLocator` as specified. `LipidNameValidationService` is NOT changed.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./mvnw -B -q test -Dtest=ParseStopLocatorTest`
Expected: 12 tests PASS. If a `realStop*` value differs, print `furthestStop` for that name. The values above were measured with jgoslin 2.2.1; a difference means a different jgoslin version is on the classpath.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lifstools/jgoslin/webapp/domain/FailureExplanation.java src/main/java/org/lifstools/jgoslin/webapp/services/ParseStopLocator.java src/test/java/org/lifstools/jgoslin/webapp/services/ParseStopLocatorTest.java
git commit -m "Find where parsing stopped and explain it in plain language"
```

### Task 4: Stored results on the shared volume

**Files:**
- Create: `src/main/java/org/lifstools/jgoslin/webapp/domain/StoredResult.java`, `src/main/java/org/lifstools/jgoslin/webapp/services/ResultStore.java`
- Modify: `src/main/java/org/lifstools/jgoslin/webapp/domain/GoslinStorageServiceSlots.java` (add `INPUT_ENTRIES`)
- Test: `src/test/java/org/lifstools/jgoslin/webapp/services/ResultStoreTest.java`

**Interfaces:**
- Consumes: `NameEntry` (Task 2); lifs-webapps-common 2.0.1 `StorageService` slot semantics (Plan 1, Task 1).
- Produces: `public record StoredResult(UUID id, int revision, List<NameEntry> entries)`:
  - `StoredResult withEdits(Map<Integer, String> edits)`: for each line, apply `withCorrection` when the trimmed, whitespace-collapsed edit is non-blank and differs from `current`. Revision + 1 if anything changed, otherwise `this`.
  - `StoredResult withRemoved(int line)`: revision + 1.
  - `List<NameEntry> active()`: entries that are not removed, in line order.
- Produces: `GoslinStorageServiceSlots.INPUT_ENTRIES`: name `"inputEntries"`, extension `".json"`, not a directory.
- Produces: `@Service public class ResultStore`, constructed with `(StorageService storage, SessionIdGenerator ids)`:
  - `StoredResult create(List<NameEntry> entries)`: revision 1,
  - `Optional<StoredResult> load(UUID id)`: empty if the folder or the file is missing,
  - `void save(StoredResult result)`.
  - JSON through a Jackson 3 `JsonMapper`.

- [ ] **Step 1: Write the failing tests** (a real `FileSystemStorageService` on `@TempDir`, and `DefaultSessionIdGenerator`)

```java
@Test void createThenLoadRoundTrips()          // entries (34,"PC(18:2"),(35,"PC 32:0") → load returns equal StoredResult, revision 1
@Test void unknownIdIsEmpty()                  // load(UUID.randomUUID()) → Optional.empty()
@Test void secondStoreOnSameFolderSeesResult() // store A creates; a new ResultStore over a new FileSystemStorageService on the same folder loads it (the replica case)
@Test void saveReplacesPreviousRevision()      // save(r.withEdits(Map.of(34,"PC(18:2)"))) → load().revision() == 2, entry 34 current "PC(18:2)", corrected
@Test void withRemovedHidesFromActive()        // r.withRemoved(34).active() lines == [35]
```

- [ ] **Step 2: Run them to see them fail**

Run: `./mvnw -B -q test -Dtest=ResultStoreTest`
Expected: FAIL (the classes do not exist).

- [ ] **Step 3: Implement** as specified.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./mvnw -B -q test -Dtest=ResultStoreTest`
Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lifstools/jgoslin/webapp/domain/StoredResult.java src/main/java/org/lifstools/jgoslin/webapp/domain/GoslinStorageServiceSlots.java src/main/java/org/lifstools/jgoslin/webapp/services/ResultStore.java src/test/java/org/lifstools/jgoslin/webapp/services/ResultStoreTest.java
git commit -m "Store line-numbered results so that every replica can serve them"
```

### Task 5: Result service and TSV

**Files:**
- Create: `src/main/java/org/lifstools/jgoslin/webapp/domain/ResultView.java`, `src/main/java/org/lifstools/jgoslin/webapp/domain/RerunOutcome.java`, `src/main/java/org/lifstools/jgoslin/webapp/services/ResultService.java`, `src/main/java/org/lifstools/jgoslin/webapp/services/TsvWriter.java`
- Modify: `src/main/java/org/lifstools/jgoslin/webapp/controller/LipidNameValidationController.java`. Move `toTable` out of it into `TsvWriter`; the controller delegates for now.
- Test: `src/test/java/org/lifstools/jgoslin/webapp/services/ResultServiceTest.java`, `src/test/java/org/lifstools/jgoslin/webapp/services/TsvWriterTest.java`

**Interfaces:**
- Consumes: `NameEntry`, `StoredResult`, `ResultStore`, `FailureExplanation`, `ParseStopLocator`, `LipidNameValidationService.validate(List<String>)` (unchanged; a failure has `getLipidAdduct() == null`).
- Produces: `public record ResultView(UUID id, int revision, int submitted, List<Row> harmonized, List<FailedRow> failed)`:
  - nested `record Row(NameEntry entry, ValidationResult result)`,
  - nested `record FailedRow(NameEntry entry, ValidationResult result, FailureExplanation explanation)`,
  - both lists in line order; `submitted` counts active entries.
- Produces: `public record RerunOutcome(int harmonized, int stillFailing)`. `harmonized` counts the edited lines that now parse; `stillFailing` is the size of the failed list after the re-run.
- Produces: `@Service public class ResultService`:
  - `ResultView harmonize(List<NameEntry> entries)`: create, parse, write the TSV,
  - `Optional<ResultView> view(UUID id)`,
  - `Optional<RerunOutcome> rerun(UUID id, Map<Integer, String> edits)`: only lines that are currently failed are applied, other keys are ignored,
  - `Optional<ResultView> remove(UUID id, int line)`,
  - `List<ResultView.FailedRow> preview(List<NameEntry> entries)`: parse without storing, for "skip invalid" unchecked.
  - Every change re-parses, saves and rewrites `OUTPUT_TSV_FILE`.
  - Parse results are cached in an LRU keyed by `id + ":" + revision` (access-ordered `LinkedHashMap`, synchronized). It is bounded by the total number of entries across cached views: at most 20,000 (`goslin.result-cache.max-entries`). It evicts eldest views until the new one fits; a single view larger than the bound is not cached. Parsing is cheap (about 0.7 ms per name), so a miss costs little.
- Produces: `@Service public class TsvWriter { String write(ResultView view) }`:
  - one row per active entry, in line order, failed ones included,
  - columns as in Global Constraints. `Original Name` = `entry.original()`. `Corrected Name` = `entry.current()` if corrected, otherwise empty.

- [ ] **Step 1: Write the failing tests**

```java
// ResultServiceTest (real LipidNameValidationService with a mocked ExternalDatabaseMappingLoader returning Optional.empty(), as in LipidNameValidationServiceTest; ResultStore on @TempDir)
@Test void splitsHarmonizedAndFailedInLineOrder()  // lines 7 "Cer(d18:1/18:0)", 34 "PC(18:2-20:4", 35 "PC 32:0" → harmonized lines [7,35], failed [34]
@Test void rerunMovesCorrectedNameIntoTable()      // rerun(id, {34:"PC(18:2-20:4)"}) → outcome (1,0); view: harmonized lines [7,34,35], entry 34 corrected, original "PC(18:2-20:4"
@Test void rerunIgnoresLinesThatDidNotFail()       // rerun(id, {35:"XYZ"}) → entry 35 unchanged, revision unchanged
@Test void blankOrUnchangedEditIsIgnored()         // rerun(id, {34:"  "}) and rerun(id, {34:"PC(18:2-20:4"}) → revision unchanged, entry 34 not corrected
@Test void duplicateNamesAreSeparateEntries()      // lines 1 and 2 both "PC(18:2-20:4"; rerun {1:"PC(18:2-20:4)"} → line 1 harmonized, line 2 still failed
@Test void removeDropsLineFromViewAndTsv()         // remove(id, 34) → failed empty, TSV has no row with Line 34
@Test void unknownIdGivesEmpty()                   // view/rerun/remove on a random UUID → Optional.empty()
@Test void cacheStaysWithinEntryBound()            // max-entries 3; harmonize two results of 2 names each → the first is evicted (view() parses again: verify via a spy on LipidNameValidationService)
// TsvWriterTest
@Test void headerStartsWithLineAndKeepsOldColumns() // first line starts with "Line\tNormalized Name\tOriginal Name\tCorrected Name\tGrammar\tValidation Messages"
@Test void correctedRowKeepsOriginalName()          // row for 34 after correction: Original Name "PC(18:2-20:4", Corrected Name "PC(18:2-20:4)"
@Test void failedRowsAreIncludedWithMessage()       // a failing line has an empty Normalized Name and a non-empty Validation Messages
```

- [ ] **Step 2: Run them to see them fail**

Run: `./mvnw -B -q test -Dtest='ResultServiceTest,TsvWriterTest'`
Expected: FAIL (the classes do not exist).

- [ ] **Step 3: Implement** as specified. `TsvWriter` keeps the current column logic of `toTable` (the `LinkedHashSet` header union) and prepends `Line` and inserts `Corrected Name`.

- [ ] **Step 4: Run the tests to see them pass, then the whole suite**

Run: `./mvnw -B -q test -Dtest='ResultServiceTest,TsvWriterTest' && ./mvnw -B -q test`
Expected: 11 new tests PASS. The full suite is green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lifstools/jgoslin/webapp src/test/java/org/lifstools/jgoslin/webapp/services
git commit -m "Parse stored results into a view, re-run corrections and write the TSV"
```

### Task 6: Input page in the LIFS design, with the Goslin logo

**Files:**
- Create: `src/main/java/org/lifstools/jgoslin/webapp/domain/HarmonizeForm.java`, `src/main/resources/templates/fragments/goslin-logo.html`, `src/main/resources/static/css/goslin.css`, `src/main/resources/static/js/goslin.js`, `src/main/resources/static/img/goslin-logo.svg`
- Rewrite: `src/main/resources/templates/index.html`, `src/main/resources/templates/fragments/nav.html`
- Modify: `src/main/java/org/lifstools/jgoslin/webapp/controller/LipidNameValidationController.java` (`/`, `/validate`, `/validatefile`), `src/main/resources/i18n/tool/messages.properties`, `src/main/resources/i18n/tool/messages_de.properties`
- Delete: `src/main/resources/templates/fragments/lipid-names-form.html` only. `overview`, `html-head`, `footer`, `script-footer`, `static/js/app.js` and `static/css/site.css` stay until Task 8, because `documentation.html` still uses them. In `fragments/nav.html`, add the new `nav-items` and `app-bar` fragments next to the old `nav` fragment; Task 8 removes `nav`.
- Test: `src/test/java/org/lifstools/jgoslin/webapp/controller/InputPageTest.java`. Replace `LipidNameValidationControllerTest.validateNames` with the redirect test below.

**Interfaces:**
- Consumes: `NameEntries.fromText`, `TooManyNamesException`, `ResultService.harmonize` and `preview`, `PageMessage` (Plan 1), the fragments `lifs/layout :: head/app-bar/messages/tracking/footer`.
- Produces: `public class HarmonizeForm { String lipidNames; boolean skipInvalid = true; }`, a JavaBean bound as model attribute `harmonizeForm`.
- **Routes:**
  - `GET /` renders `index`. With `?example` the text area holds `lipidnames.txt`.
  - `POST /validate` (`HarmonizeForm`):
    - success → `303` to `/result/{id}`,
    - too many names → `index` with an error `PageMessage`,
    - `skipInvalid=false` and failures → `index` with model `failures` (`List<ResultView.FailedRow>`) and the text kept.
  - `GET /validate` → `302` to `/`.
  - `POST /validatefile` (multipart, no-JS fallback) → same flow as `POST /validate`, from the file's text with `skipInvalid=true`.
- **Model attribute `messages`** (`List<PageMessage>`): the SNAPSHOT warning is `PageMessage.warn` with today's text, "This is a development version, please be aware that it may not be stable and expect bugs and errors!".
- **Produces (DOM contract for Tasks 7 and 9):**
  - `textarea#lipids[name=lipidNames]`
  - `#lipid-count` (`aria-live=polite`)
  - `a#load-example[href$="?example"]`
  - `input#skipInvalid[name=skipInvalid]` (checked)
  - `button#harmonize[type=submit]` with the text "Harmonize"
  - `input#file[type=file]` inside `form#upload-fallback` (posts to `/validatefile`)
  - `.goslin-logo` wrapping the inline SVG, which has `.g-head` and `.g-eye`, plus `.g-bubble`
- **Copy:** the hero, notation card, "For scripts", "How to cite" and "About Goslin" texts come from the mock `docs/superpowers/specs/2026-11-26-goslin-lifs-redesign-mock.html`, screen 1. They go into message keys (`index.*`), with German versions.
- **Upload limit:** the file size limit shown comes from `spring.servlet.multipart.max-file-size`, through `Page.maxFileSize` as today.

- [ ] **Step 1: Write the failing tests**

```java
@Test void homeShowsEmptyTextAreaAndCheckedSkip()   // GET / → h1 "Harmonize lipid names", textarea#lipids empty, #skipInvalid checked, button#harmonize text "Harmonize"
@Test void exampleFillsTextArea()                  // GET /?example → textarea#lipids text starts with "12-HETE" and has 56 non-blank lines
@Test void noNormalizeWordingOnHome()              // GET / → body text contains no "Normaliz" (case-insensitive)
@Test void validateRedirectsToResult()             // POST /validate lipidNames="PC 32:0\nTG 52:2" → 303, Location matches /result/[0-9a-f-]{36}
@Test void tooManyNamesKeepsText()                 // jgoslin.maxItemsInRequest via @TestPropertySource=2; 3 names → 200 view index, .lifs-callout--error, textarea keeps the text
@Test void skipUncheckedListsFailuresByLine()      // POST skipInvalid=false, "PC 32:0\n\nPC(18:2" → 200 index, error callout lists "line 3"
@Test void fileFallbackRedirectsToResult()         // multipart POST /validatefile with "PC 32:0\n" → 303 /result/…
@Test void logoIsDecorativeInlineSvg()             // GET / → .goslin-logo svg[role=img][aria-label="Goslin logo"], no tabindex, .g-bubble[aria-hidden=true]
@Test void trackingScriptHasSiteId()               // GET / → script[data-lifs-tracking][data-site-id] present (ga.id set in test properties)
@Test void documentationStillRenders()             // GET /documentation → 200 (old layout until Task 8)
```

- [ ] **Step 2: Run them to see them fail**

Run: `./mvnw -B -q test -Dtest=InputPageTest`
Expected: FAIL.

- [ ] **Step 3: Implement the controller changes and the templates**
  - **Logo:** the SVG in `goslin-logo.html` is the cleaned `goslin/goslin.svg` with the same geometry, exactly as built in the easter-egg mock's `logo()` function (`docs/superpowers/specs/2026-11-26-goslin-easter-egg-mock.html`). The same SVG, without the classes, goes into `static/img/goslin-logo.svg`.
  - **`goslin.js`** (an IIFE; each part runs only if its elements exist):
    - the input page: example loader (fetches `?example` text from a `data-example-url`, or uses an embedded `<template id="example-names">`), Clear, the count, file read into the text area, drag and drop onto `.goslin-names`,
    - the logo: a random blink offset, and honk on click with the 5th-click line, following Global Constraints.
  - **`goslin.css`:** the classes from the mocks (`.cols`, `.goslin-names`, `.goslin-logo` with `.g-eye`/`.g-head` animations, `.g-bubble`, `.check`, `.notation`, `.about`), on LIFS tokens.
  - **`fragments/nav.html`** has `nav-items` (Harmonize, Documentation, REST API, each with `active`) and an `app-bar` wrapper with no actions, as in `/Users/nilshoffmann/Projects/github.com/lifs-tools/lifs-apps/src/main/resources/templates/fragments/nav.html`.

- [ ] **Step 4: Run the tests to see them pass, then the whole suite**

Run: `./mvnw -B -q test -Dtest=InputPageTest && ./mvnw -B -q test`
Expected: 10 tests PASS. The full suite is green.

- [ ] **Step 5: Check by hand**: `./mvnw -B spring-boot:run -Dspring-boot.run.profiles=dev`. Open `http://localhost:8083/goslin/` and compare with mock screen 1 at 1280 px and at 390 px width. Load example, drop a `.txt` file, click the logo five times.

- [ ] **Step 6: Commit**

```bash
git add -A src/main
git add src/test/java/org/lifstools/jgoslin/webapp/controller
git commit -m "Rebuild the input page in the LIFS design with one text box and the Goslin logo"
```

### Task 7: Result page, re-run and remove

**Files:**
- Create: `src/main/resources/templates/result.html`, `src/main/java/org/lifstools/jgoslin/webapp/domain/RerunForm.java`
- Delete: `src/main/resources/templates/validationResult.html`
- Modify: `LipidNameValidationController.java` (new routes; `/download` now names the file `goslin-result-<first 8 chars of id>.tsv`), `static/js/goslin.js` (table part), `static/css/goslin.css`, `messages*.properties` (`result.*`)
- Test: `src/test/java/org/lifstools/jgoslin/webapp/controller/ResultPageTest.java`, `src/test/java/org/lifstools/jgoslin/webapp/controller/ResultPageReplicaIT.java`, `src/test/java/org/lifstools/jgoslin/webapp/controller/ResultPageParamsIT.java`. Add `maven-failsafe-plugin` for `*IT` if it is missing, as in lifs-apps.

**Interfaces:**
- Consumes: `ResultService.view/rerun/remove`, `ResultView`, `FailureExplanation.marked`, `RerunOutcome`.
- Produces: `public class RerunForm { Map<Integer, String> edit = new HashMap<>(); }`. Inputs are named `edit[34]`.
- **Routes:**
  - `GET /result/{id}` (optional `rerun=<harmonized>,<stillFailing>`) renders `result`. Unknown or expired id: HTTP 404, view `result` with model `notFound=true`, showing the callout "Result not found or expired. Results are kept for 7 days." and a link to `/`.
  - `POST /result/{id}/rerun` (`RerunForm`) → `303` to `/result/{id}?rerun=h,f`.
  - `POST /result/{id}/remove` (`line`) → `303` to `/result/{id}`.
  - Both POSTs on an unknown id → 404 not-found page.
  - The "Remove" buttons are `<button formaction="…/remove" name="line" value="34">` inside the re-run form. `/remove` also applies the posted `edit[…]` values (as a re-run does) before removing the line, so edits typed into other rows are not lost.
  - **Enter in a correction field must re-run, not remove.** The first submit button in the form is `<button type="submit" id="rerun-default" class="visually-hidden" tabindex="-1" aria-hidden="true">`. It is clipped, not `display:none`, so implicit submission uses it. The visible `#rerun` follows the rows.
  - `{id}` that is not a UUID → the same 404 not-found page.
- **The `rerun` parameter** renders `PageMessage.ok("N corrected names were harmonized and added to the table. M names still need attention.")`, with singular and plural forms, via a `ChoiceFormat` message key. Invalid values are ignored.
- **Produces (DOM contract for Task 9):**
  - `a#download-tsv` (filled, twice: `#download-tsv` and `#download-tsv-bottom`)
  - `section#fix` with `.fix__row[data-line]`, each holding `input[name="edit[N]"]`, `.trace mark`, the plain-language hint, `details.fix__original` (Goslin's original message), and `button[name=line]`
  - `button#rerun`
  - `table#results` with `tbody tr.result-row[data-line][data-level][data-category][data-search]`, followed by `tr.detail` holding `<details>`
  - `.chip-corrected`
  - `#filter-text`, `#filter-level`, `#filter-category`
  - `#pager` (`Previous`/`Next`, "Rows a–b of n · sorted by line")
- **Table without JS:** all rows are rendered, with `details` open on click. `goslin.js` adds filtering on the `data-*` attributes, sorting by clicking `th[data-sort]`, paging at 25, and ▸/▾ toggles with `aria-expanded`. Rows are rendered server-side; the JS only shows and hides `tr`s.
- **Details row:** carries over every field of the old `lipid-adduct-card` fragment in `validationResult.html`: grammar, adduct, LIPID MAPS category and class links, database entries, level, C atoms, double bonds, functional group counts, mass, formula and the FA table.

- [ ] **Step 1: Write the failing tests**

```java
@Test void failedNamesComeFirstWithLineNumbers()    // harmonize "Cer(d18:1/18:0)\n\nPC(18:2-20:4" → section#fix precedes table#results; .fix__row[data-line=3]; input[name="edit[3]"] value "PC(18:2-20:4"
@Test void tableRowsCarryLineNumbers()              // tr.result-row[data-line=1] exists; first td text "1"
@Test void rerunRedirectsWithOutcome()              // POST /result/{id}/rerun edit[3]=PC(18:2-20:4) → 303 Location ends "/result/{id}?rerun=1,0"
@Test void outcomeMessageIsShown()                  // GET /result/{id}?rerun=1,0 → .lifs-callout--ok contains "1 corrected name was harmonized"
@Test void correctedRowShowsChipAndOriginal()       // after rerun: tr.result-row[data-line=3] has .chip-corrected and <s>PC(18:2-20:4</s>
@Test void removeDropsRow()                         // POST /result/{id}/remove line=3 → 303; GET → no [data-line=3] anywhere
@Test void unknownResultIs404()                     // GET /result/{random} → 404, text contains "Result not found or expired"
@Test void rerunOnExpiredResultShowsNotFound()      // POST /result/{random}/rerun → 404 not-found page
@Test void downloadButtonsPointToTsv()              // a#download-tsv and a#download-tsv-bottom href end with "/download/{id}/OUTPUT_TSV_FILE"
@Test void downloadHasLineColumn()                  // GET that href → 200, first line starts "Line\tNormalized Name"
@Test void downloadUnknownIs404()                   // GET /download/{random}/OUTPUT_TSV_FILE → 404 (lifs-webapps-common 2.0.1 throws StorageFileNotFoundException; map it to 404)
@Test void namesAreEscapedEverywhere()              // harmonize "<script>alert(1)</script>\n<b>PC 32:0" → no "<script>alert(1)" and no "<b>PC" in the body; rerun edit[2]="PC 32:0" → row 2 harmonized, its struck-through original and data-search hold "&lt;b&gt;PC 32:0"
@Test void nonUuidIdIs404()                         // GET /result/not-a-uuid and POST /result/not-a-uuid/rerun → 404 not-found page
@Test void removeKeepsEditsInOtherRows()            // failed lines 2 and 3; POST /remove line=3 with edit[2]="PC 32:0" → line 2 harmonized, line 3 gone
// ResultPageReplicaIT (two SpringApplicationBuilder(Application.class) contexts, server.port=0, the same storage.location @TempDir)
@Test void resultCreatedOnOneReplicaRendersOnTheOther() // POST /validate to context A → GET Location on context B → 200, same rows
// ResultPageParamsIT (@SpringBootTest(webEnvironment=RANDOM_PORT), real Tomcat)
@Test void rerunWithElevenHundredFailedNames()      // 1,100 failing names; POST rerun with 1,100 edit[...] params → 303 (fails with Tomcat's default limit of 1,000)
@Test void noNormalizeWordingOnResult()             // GET /result/{id} → no "Normaliz" in body text (the TSV column name is not in the page)
```

- [ ] **Step 2: Run them to see them fail**

Run: `./mvnw -B -q test -Dtest=ResultPageTest`
Expected: FAIL.

- [ ] **Step 3: Implement** the routes, `result.html` (following mock screen 2 with notes C and D) and the table JS.

- [ ] **Step 4: Run the tests to see them pass, then the whole suite**

Run: `./mvnw -B -q test -Dtest=ResultPageTest && ./mvnw -B verify`
Expected: 15 tests in `ResultPageTest` and 2 ITs PASS. BUILD SUCCESS.

- [ ] **Step 5: Check by hand** with the example names plus three typos: fix two, re-run, remove one, filter by level, page through, and download. Compare with mock screen 2.

- [ ] **Step 6: Commit**

```bash
git add -A src/main src/test/java/org/lifstools/jgoslin/webapp/controller/ResultPageTest.java
git commit -m "Add the result page: failed names first, re-run, remove, line numbers and a filterable table"
```

### Task 8: Documentation and error pages, manual, wording, news

**Files:**
- Rewrite: `src/main/resources/templates/documentation.html`, `src/main/resources/templates/fragments/manual.html` (the "Interactive Usage" section only; the REST part stays), `src/main/resources/templates/fragments/news.html`
- Create: `src/main/resources/templates/error.html` (LIFS design, like `/Users/nilshoffmann/Projects/github.com/lifs-tools/lifs-apps/src/main/resources/templates/error.html`)
- Modify: `src/main/java/org/lifstools/jgoslin/webapp/controller/LifsErrorController.java`: always render view `error`, as lifs-apps' `LifsErrorController` does. Today it picks `errors/404` and `errors/500` from lifs-webapps-common, which are still Bootstrap.
- Delete: `src/main/resources/templates/fragments/{overview,html-head,footer,script-footer}.html`, the old `nav` fragment, `src/main/resources/static/js/app.js`, `src/main/resources/static/css/site.css`
- Modify: `src/main/resources/i18n/tool/messages*.properties` (`main.title=Goslin webapplication - A Parser, Validator and Harmonizer for Shorthand Lipid Nomenclature`), `src/main/resources/news.properties` (a new entry for 2.3.0 that names the TSV `Line` and `Corrected Name` columns), `README.adoc` (screenshots, Java 25, run instructions)
- Delete: `src/main/resources/static/css/bootstrap-toc*.css`, `src/main/resources/static/js/bootstrap-toc*.js`
- Test: `src/test/java/org/lifstools/jgoslin/webapp/controller/DocumentationPageTest.java`

**Interfaces:**
- Consumes: the layout fragments; the `goslin.js` text filter, generalised to `[data-filter-target]`.
- Produces: `GET /documentation`:
  - headings with ids, and an in-page table of contents `nav#toc` listing them,
  - the lipid classes table `table#lipid-classes` with input `#filter-classes`,
  - news,
  - the manual images still named `static/img/goslin-webapp-form-01.png`, `-form-02.png`, `-form-02a.png`, `-result-03.png` and `-result-detail-04.png`, replaced in Task 9.
- The "Interactive Usage" text describes: pasting or typing into the box, loading examples, uploading or dropping a file, "Skip invalid names", the result page (counts, "Correct these names", re-run, remove, filters, details, TSV with `Line` and `Corrected Name`), and that results are kept 7 days.

- [ ] **Step 1: Write the failing tests**

```java
@Test void documentationUsesLifsLayout()   // GET /documentation → header .lifs-bar, footer.lifs-footer, nav#toc with ≥ 4 links
@Test void lipidClassTableHasFilter()      // table#lipid-classes tbody tr count > 100, input#filter-classes present
@Test void manualDescribesNewFlow()        // text contains "Correct these names" and "Load example names"; no "Lipid File Input", no "CTRL+V"
@Test void errorPageUsesLifsLayout()       // GET /error with request attributes jakarta.servlet.error.status_code=404 and request_uri=/x → view "error", .lifs-bar present (MockMvc does not dispatch to /error by itself)
@Test void noNormalizeWordingInManual()    // #interactive-usage text contains no "Normaliz"
```

- [ ] **Step 2: Run them to see them fail**

Run: `./mvnw -B -q test -Dtest=DocumentationPageTest`
Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run the tests to see them pass, then the whole suite**

Run: `./mvnw -B -q test -Dtest=DocumentationPageTest && ./mvnw -B verify`
Expected: 5 tests PASS, BUILD SUCCESS. `grep -rni "normaliz" src/main/resources/templates` finds only REST, JSON or past-news mentions.

- [ ] **Step 5: Commit**

```bash
git add -A src/main README.adoc src/test/java/org/lifstools/jgoslin/webapp/controller/DocumentationPageTest.java
git commit -m "Restyle documentation and error pages; rewrite the manual for the new pages"
```

### Task 9: End-to-end tests, screenshots and CI

**Files:**
- Create: `e2e/` modelled on `/Users/nilshoffmann/Projects/github.com/lifs-tools/lifs-apps/e2e/`, without Keycloak:
  - `e2e/package.json` (scripts `test`, `screenshots`; `@playwright/test` at the version lifs-apps uses),
  - `e2e/playwright.config.ts` (baseURL `http://localhost:8083/goslin/`, webServer `scripts/start-app.sh`),
  - `e2e/scripts/start-app.sh` (runs `target/lifs-goslin-webapp-*.jar --server.port=8083 --ga.url=http://127.0.0.1:9/matomo.php --storage.location=<a temp folder>`),
  - `e2e/tests/helpers.ts`, `e2e/tests/harmonize.spec.ts`, `e2e/tests/consent.spec.ts`, `e2e/tests/logo.spec.ts`, `e2e/tests/screenshots.spec.ts`,
  - `e2e/.gitignore`.
- Modify: `.github/workflows/ci.yml` (an e2e job after the unit tests, as in lifs-apps)
- Replace: `src/main/resources/static/img/goslin-webapp-*.png` (output of `npm run screenshots`)

**Interfaces:**
- Consumes: the DOM contracts of Tasks 6 and 7 and Plan 1 Task 3.
- In every test, `helpers.ts` routes `https://lifs-tools.org/matomo/**` to an empty 200 response and records the requests. Tests never reach the real Matomo.

- [ ] **Step 1: Write the tests**

```ts
// harmonize.spec.ts
test('paste, harmonize, fix, re-run, download')   // fill #lipids with "PC 32:0\n\nPC(18:2-20:4\nTG 52:2"; click #harmonize; expect URL /result/; expect .fix__row[data-line="3"];
                                                 // fill input[name="edit[3]"] "PC(18:2-20:4)"; click #rerun; expect ok callout; expect tr.result-row[data-line="3"] .chip-corrected;
                                                 // click #download-tsv → download; its first line starts "Line\tNormalized Name"
test('load example and file drop')               // click #load-example → #lipid-count "56 names"; drop a 2-line file on .goslin-names → #lipid-count "2 names"
test('filter and paging')                        // example names → #pager shows "Rows 1–25"; type "TG" in #filter-text → every visible tr.result-row has "tg" in data-search
test('harmonizes 5000 names')                    // 5000 lines of "PC 32:0" → result page within 60 s; #pager "of 5,000"
test('enter in a correction field re-runs')      // two failed names; type the fix into the first edit field and press Enter → ok callout, both failed rows' lines still present (nothing removed)
// consent.spec.ts
test('accept loads matomo once and hides banner') // [data-testid=consent-accept] → a matomo.js request recorded; reload → no banner
test('deny hides banner and loads nothing')       // [data-testid=consent-deny] → no matomo.js request
test('do not track never loads matomo')           // addInitScript defines navigator.doNotTrack "1" and the cookie lifs-cookies-accepted=on → [data-testid=consent-dnt] visible, no matomo.js request
// logo.spec.ts
test('honk on click, special line on fifth')      // click .goslin-logo → .g-bubble visible with "Honk!"; 4 more clicks → "PC 16:0/18:1(9Z), please."
test('reduced motion stops blink and peck')       // emulateMedia({reducedMotion:'reduce'}) → getComputedStyle(.g-eye).animationName === 'none'; click → bubble still visible
// screenshots.spec.ts (only with `npm run screenshots`: testMatch from SCREENSHOTS=1)
// writes ../src/main/resources/static/img/goslin-webapp-form-01.png (empty input), -form-02.png (example loaded), -form-02a.png (file dropped),
//        -result-03.png (result with fix card), -result-detail-04.png (one detail row open), at 1280×800
```

- [ ] **Step 2: Build and run**

Run: `./mvnw -B -q package -DskipTests && cd e2e && npm ci && npx playwright install chromium && npm test`
Expected: 10 tests PASS (screenshots excluded).

- [ ] **Step 3: Make the screenshots and look at each of them**

Run: `cd e2e && npm run screenshots`
Expected: five PNGs updated. Open each one and compare with the mocks.

- [ ] **Step 4: Add the e2e job to `ci.yml`** following lifs-apps' job, minus the Keycloak service. Push the branch and check that CI is green.

- [ ] **Step 5: Commit**

```bash
git add e2e .github/workflows/ci.yml src/main/resources/static/img
git commit -m "Add Playwright tests for harmonize, consent and the logo; new manual screenshots"
```

### Task 10: Whole-branch check and pull request

- [ ] **Step 1:** Switch `lifs.webapps.common.version` to `2.0.1` once Plan 1 is released. Run `./mvnw -B verify` and `cd e2e && npm test`; all must pass.
- [ ] **Step 2:** Go through the spec's sections 3–7 and tick off each item against the running app.
- [ ] **Step 3:** Open a pull request against `master`. The description names the TSV changes (`Line`, `Corrected Name`) and the new `/result/{id}` addresses, and links issue #121 ("Fixes #121").
