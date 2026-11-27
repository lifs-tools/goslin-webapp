# Database matches: caution and coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Warn on the result page that database matches are candidates, and show on the documentation page what each database contains and covers.

**Architecture:** `ExternalDatabaseMappingLoader` counts entries per database, lipid category and lipid class while it loads the dumps, and exposes the counts as `DatabaseCoverage` records. The documentation controller passes them, with version texts from `goslin.databases.*` properties, to a new `#databases` section and to three new columns in the lipid class table. The result page gets a callout with fixed EN/DE copy.

**Tech Stack:** Java 25, Spring Boot 4.2, Thymeleaf, jgoslin 2.2.1, JUnit 5 + MockMvc + jsoup.

**Spec:** `/Users/nilshoffmann/Projects/github.com/lifs-tools/goslin-webapp-wt/database-coverage/docs/superpowers/specs/2026-11-27-database-coverage-design.md`

All paths below are relative to the worktree `/Users/nilshoffmann/Projects/github.com/lifs-tools/goslin-webapp-wt/database-coverage` (branch `feature/database-coverage`). Java sources live under `src/main/java/org/lifstools/jgoslin/webapp/`, tests under `src/test/java/org/lifstools/jgoslin/webapp/`.

## Global Constraints

- No new libraries (Maven or JS). The chart is server-rendered HTML/CSS (or inline SVG).
- Colours only from LIFS tokens (`--lifs-*` in lifs-webapps-common `lifs.css`), defined once as chart tokens in `static/css/goslin.css`.
- Page text says "Harmonize", never "Normalize", for what Goslin does to names.
- Tests never send tracking data (the test profile already blocks Matomo; keep it so).
- The documentation page is English only (like its neighbouring sections); the result page callout is EN + DE message keys.
- Counts are entries (database records), not distinct names.
- A name counts as "read by Goslin" when its normalized name is not empty and not `NA` or `UNDEFINED` (LIPID MAPS, SwissLipids), or when jgoslin parsed it (ChEBI, as today).
- Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Do not push.
- Port 8083 is Nils' running instance: never stop it or bind to it. e2e uses `GOSLIN_E2E_PORT=8094`.

## Review Focus

1. A name whose head group carries a bracket (`PE-N(FA 16:0) 34:1`, token `PE-N(FA`) must classify as its class, not as "not classified" — test `classifiesHeadGroupWithBracket` in Task 1.
2. Entries with normalized name `UNDEFINED` or `NA` must count as loaded but not read, and never as a class named "UNDEFINED" — test `unreadNamesAreNotClassified` in Task 1.
3. A class with zero entries in a database must show "none", not "0" or an empty cell, and the class filter must still find rows — test `classTableShowsNoneForGaps` in Task 2.
4. The chart must stay readable for a category with a tiny share (SwissLipids FA ≈ 0.02 %): the bar may be invisible, but the table next to it must still show the count — test `coverageTableListsEveryCategory` in Task 2.
5. The callout must not appear on a result with no harmonized names (all failed or all removed) — test `noCalloutWithoutHarmonizedNames` in Task 3.

---

### Task 1: Coverage statistics in the loader

**Files:**
- Create: `domain/DatabaseCoverage.java`
- Create: `services/CoverageCounter.java`
- Create: `config/DatabaseProperties.java`
- Modify: `services/ExternalDatabaseMappingLoader.java` (count while loading; add `getCoverage()`)
- Modify: `src/main/resources/application.properties` (version texts, documented)
- Modify: `Application.java` (add `DatabaseProperties.class` to `@EnableConfigurationProperties`)
- Test: `services/CoverageCounterTest.java` (plain unit test), `services/DatabaseCoverageIT`-style Spring test named `services/DatabaseCoverageTest.java`, `config/DatabasePropertiesTest.java`

**Interfaces:**
- Produces:
  - `enum DatabaseCoverage.Database { LIPID_MAPS, SWISS_LIPIDS, CHEBI }` with `String displayName()` → `"LIPID MAPS"`, `"SwissLipids"`, `"ChEBI"`.
  - `record DatabaseCoverage(Database database, long entriesLoaded, long namesRead, Map<LipidCategory, Long> byCategory, Map<String, Long> byClass)` in `domain`. `byCategory` is an `EnumMap` holding only categories with a count > 0, plus `LipidCategory.UNDEFINED` for "not classified" read names. `byClass` is keyed by `LipidClassMeta.lipidClassName`.
    - `long classified()` = `namesRead - byCategory.getOrDefault(UNDEFINED, 0)`.
    - `long countForClass(String lipidClassName)` → 0 when absent.
    - `double share(LipidCategory c)` = `byCategory.get(c) / namesRead * 100`, 0 when `namesRead == 0`.
  - `final class CoverageCounter` in `services`:
    - `CoverageCounter(DatabaseCoverage.Database database)`
    - `void countName(String normalizedName)` — for LIPID MAPS and SwissLipids rows.
    - `void countUnparsed()` — a ChEBI row that was not read.
    - `void countParsed(LipidAdduct adduct)` — a ChEBI row that was read; class and category from `adduct.getLipid().getHeadGroup()`.
    - `DatabaseCoverage build()`
    - `static OptionalInt classOf(String normalizedName)` — head group lookup: token before the first space; if `Headgroup.getClass(token)` is `LipidClasses.UNDEFINED_CLASS` and the token contains `(`, retry with the part before `(`; empty when still undefined or when the class's category is `UNDEFINED` or `NO_CATEGORY`. Never parses.
  - `ExternalDatabaseMappingLoader.getCoverage()` → `List<DatabaseCoverage>` in the order LIPID MAPS, SwissLipids, ChEBI; unmodifiable; built once in the constructor.
  - `@ConfigurationProperties("goslin.databases") record DatabaseProperties(@DefaultValue("3 March 2023") String lipidMapsVersion, @DefaultValue("early 2023") String swissLipidsVersion, @DefaultValue("18 January 2024") String chebiVersion)` with `String versionOf(DatabaseCoverage.Database d)`.

- [ ] **Step 1: Write the failing tests**

`CoverageCounterTest` (no Spring):
```java
@Test void classifiesByHeadGroupToken()      // classOf("PC 34:1") present, LipidClasses.getInstance().get(it).lipidClassName equals the PC class name; category GP
@Test void classifiesHeadGroupWithBracket()  // classOf("PE-N(FA 16:0) 34:1") present (same class as classOf("PE-N 34:1") if that token exists, else assert category GP)
@Test void unknownHeadGroupIsNotClassified() // classOf("NeuAc-Gal-Glc-Cer 36:1;O2").isEmpty()
@Test void unreadNamesAreNotClassified()     // counter.countName("UNDEFINED"), countName("NA"), countName(""), countName("PC 34:1") → entriesLoaded 4, namesRead 1, byClass has no key "UNDEFINED", byCategory == {GP=1}
@Test void unclassifiedReadNamesGoToUndefined() // countName("NeuAc-Gal-Glc-Cer 36:1;O2"), countName("FA 18:1") → namesRead 2, classified() 1, byCategory {FA=1, UNDEFINED=1}
@Test void sharesSumToHundred()              // after a mix of names, sum of share(c) over byCategory keys == 100.0 within 1e-9; share of a category absent == 0
@Test void chebiCountsParsedAndUnparsed()    // GoslinParser().parse("PC 16:0/18:1") → countParsed; countUnparsed() twice → entriesLoaded 3, namesRead 1, byCategory {GP=1}
```

`DatabaseCoverageTest` (`@SpringBootTest`, profiles `test`, `dev`; autowire the loader):
```java
@Test void coverageMatchesTheLoadedDumps()
// LIPID_MAPS: entriesLoaded 28_942, namesRead 26_832
// SWISS_LIPIDS: entriesLoaded 777_956, namesRead 766_511
// CHEBI: entriesLoaded 381_571, namesRead between 2_000 and 3_000 (depends on the jgoslin build)
// each: classified() / (double) namesRead > 0.99 for LIPID_MAPS and SWISS_LIPIDS
```

`DatabasePropertiesTest`: binding `goslin.databases.lipid-maps-version=1 May 2026` yields that text; defaults are `3 March 2023`, `early 2023`, `18 January 2024` (follow `ParserPropertiesTest`'s pattern).

- [ ] **Step 2: Run them to see them fail**

Run: `./mvnw -B -q test -Dtest='CoverageCounterTest,DatabaseCoverageTest,DatabasePropertiesTest'`
Expected: compilation fails (types missing).

- [ ] **Step 3: Implement** the types above, and count in `ExternalDatabaseMappingLoader`'s existing loops: every LIPID MAPS / SwissLipids row → `countName(edr.getNormalizedName())`; every ChEBI row → `countParsed` when `lipidAdduct != null`, else `countUnparsed()` (rows that are skipped by type or source also count as loaded, not read). `entriesLoaded` is the number of rows. Keep the existing log lines. Add to `application.properties`, with a comment that the dumps carry no date:
```
goslin.databases.lipid-maps-version=3 March 2023
goslin.databases.swiss-lipids-version=early 2023
goslin.databases.chebi-version=18 January 2024
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./mvnw -B -q test -Dtest='CoverageCounterTest,DatabaseCoverageTest,DatabasePropertiesTest'`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add src/main/java src/test/java src/main/resources/application.properties
git commit -m "Count database entries per lipid category and class while loading"
```

### Task 2: Documentation page — Databases section and coverage columns

**Files:**
- Modify: `controller/LipidNameValidationController.java` (inject `ExternalDatabaseMappingLoader` and `DatabaseProperties` through the constructor like the other final fields; in the `/documentation` handler, around line 279, add model attributes `databases` = `loader.getCoverage()` and `databaseVersions` = a `Map<DatabaseCoverage.Database, String>` from `DatabaseProperties`)
- Modify: `templates/documentation.html` (toc entry "Databases" before "Lipid classes"; include the new fragment before `lipidclasses`)
- Modify: `templates/fragments/news.html` (new fragment `databases`; three columns in the `lipidclasses` table)
- Modify: `static/css/goslin.css` (chart tokens and styles)
- Test: `controller/DocumentationPageTest.java`

**Interfaces:**
- Consumes: `ExternalDatabaseMappingLoader.getCoverage()`, `DatabaseCoverage` and its methods, `DatabaseProperties.versionOf(...)` from Task 1.

**Copy (fixed by the spec):**
- Section id `databases`, heading "Databases Goslin matches against".
- Per-database description, as the spec's table "Kind (EN text)":
  - LIPID MAPS (LMSD): "Structures mostly reported for lipids measured from authentic samples. Many are filed under species-level abbreviations such as PC 34:1. Goslin uses LMSD only, not the computationally generated COMPDB."
  - SwissLipids: "Generated combinatorially from known building blocks: it lists structures that are biochemically plausible, not structures that were observed."
  - ChEBI: "Manually curated chemical entities. It holds far fewer lipids than LIPID MAPS or SwissLipids, and names them by trivial or systematic names rather than by the shorthand nomenclature, especially at its intermediate levels. Goslin reads only a small share of its names."
- Footnote: "The LIPID MAPS file holds only the entries that were normalized; the total size of the March 2023 release was not recorded." Link "issue #129" to `https://github.com/lifs-tools/goslin-webapp/issues/129`.
- Overview table columns: "Database", "What it contains", "Version", "Entries loaded", "Names Goslin reads", "Assigned to a lipid class". Numbers with thousands separators (`#numbers.formatInteger(n, 1, 'COMMA')`).
- Chart caption: "Share of the names Goslin reads, by lipid category". One row per database: a 100 % horizontal bar split by category in the order FA, GL, GP, SP, ST, PK, SL, then "Not classified" (`UNDEFINED`); categories with 0 are left out of that bar. Category labels: the jgoslin `LipidCategory` description with its abbreviation, e.g. "Glycerophospholipids (GP)"; "Not classified" for `UNDEFINED`. Each segment has a `title` with "Category: count (share %)". Below the bars, a table "Entries per lipid category" with one row per category in the same order and one column per database (count and share), so no number depends on colour.
- Lipid class table: three new columns "LIPID MAPS", "SwissLipids", "ChEBI" after "Synonyms", each the class's `countForClass(lipidClass.lipidClassName)`; 0 renders as `<span class="db-none">none</span>`.

- [ ] **Step 1: Write the failing tests** (in `DocumentationPageTest`)
```java
@Test void databasesSectionListsThreeDatabases()
// #databases exists; toc has a[href=#databases]; overview table tbody has 3 rows, first cells "LIPID MAPS", "SwissLipids", "ChEBI";
// LIPID MAPS row contains "28,942" and "26,832" and "3 March 2023"; SwissLipids row contains "777,956" and "early 2023"; ChEBI row contains "18 January 2024";
// section text contains "computationally generated COMPDB", "combinatorially", "Manually curated" and a link to issues/129
@Test void coverageChartHasOneBarPerDatabase()
// 3 elements .coverage-bar; each has segments [data-category]; SwissLipids bar has a segment data-category=GP whose title contains "%"
@Test void coverageTableListsEveryCategory()
// the "Entries per lipid category" table has a row for FA, GL, GP, SP, ST and "Not classified"; the SwissLipids FA cell is not empty and not "0"
@Test void classTableShowsCoverageColumns()
// table#lipid-classes thead has th "LIPID MAPS", "SwissLipids", "ChEBI"; the row whose LIPID MAPS class name cell is the PC class has a numeric SwissLipids cell > 1000
@Test void classTableShowsNoneForGaps()
// at least one .db-none in table#lipid-classes tbody; input#filter-classes still targets #lipid-classes
```

- [ ] **Step 2: Run them to see them fail**

Run: `./mvnw -B -q test -Dtest=DocumentationPageTest`
Expected: the five new tests FAIL; existing ones PASS.

- [ ] **Step 3: Implement** as listed under Files and Copy. Before styling the chart, load the `dataviz` skill and pick the category colours from LIFS tokens with it (eight segments; "Not classified" in a neutral grey); define them once in `goslin.css` as `--goslin-cat-fa`, `--goslin-cat-gl`, … `--goslin-cat-none`. Bars and tables must fit at 360 px width without horizontal page scroll (tables may scroll inside `.lifs-table-wrap`).

- [ ] **Step 4: Run the tests and look at the page**

Run: `./mvnw -B -q test -Dtest=DocumentationPageTest`
Expected: PASS. Then start the app on port 8094 (`--server.port=8094`), open `/documentation#databases` at desktop and 360 px width with the e2e Playwright setup or a throwaway script, take screenshots into the scratchpad, and look at them.

- [ ] **Step 5: Commit**
```bash
git add src/main/java src/main/resources/templates src/main/resources/static/css/goslin.css src/test/java
git commit -m "Documentation: databases Goslin matches against, with coverage by category and class"
```

### Task 3: Result page callout

**Files:**
- Modify: `templates/result.html` (after the counts banner, inside the `th:unless="${notFound}"` block, only when `nHarmonized > 0`)
- Modify: `i18n/tool/messages.properties`, `i18n/tool/messages_de.properties`
- Test: `controller/ResultPageTest.java`

**Copy (fixed; EN approved by Nils):**
```
result.databases.title=Database matches are candidates, not identifications.
result.databases.body=Goslin lists LIPID MAPS, SwissLipids and ChEBI entries whose shorthand name matches yours, including at species level (e.g. PC 34:1). A match means an entry is consistent with your name, not that it is your lipid. The less specific the name, the more structures match: FA 18:1 matches dozens of LIPID MAPS entries. What you see also depends on what each database contains. Classes a database covers poorly return few or no matches. Treat matched entries as candidates, check them against your evidence, and do not report them as identified lipids. <a href="{0}">How complete are the databases?</a>
```
```
result.databases.title=Datenbanktreffer sind Kandidaten, keine Identifizierungen.
result.databases.body=Goslin listet Einträge aus LIPID MAPS, SwissLipids und ChEBI, deren Kurzschreibweise zu Ihrem Namen passt, auch auf Speziesebene (z. B. PC 34:1). Ein Treffer bedeutet, dass ein Eintrag mit Ihrem Namen vereinbar ist, nicht, dass es Ihr Lipid ist. Je weniger spezifisch der Name, desto mehr Strukturen passen: FA 18:1 trifft Dutzende Einträge in LIPID MAPS. Was Sie sehen, hängt auch davon ab, was jede Datenbank enthält. Klassen, die eine Datenbank schlecht abdeckt, liefern wenige oder keine Treffer. Behandeln Sie Treffer als Kandidaten, prüfen Sie sie anhand Ihrer Daten und berichten Sie sie nicht als identifizierte Lipide. <a href="{0}">Wie vollständig sind die Datenbanken?</a>
```
Rendered as `<div id="database-caution" class="lifs-callout lifs-callout--info" role="note">` with `<b th:text=…title>` and `<span class="lifs-prose" th:utext="#{result.databases.body(@{/documentation#databases})}">` (the same `th:utext` + link-parameter pattern as `result.fix.headGroup`).
- [ ] **Step 1: Write the failing tests** (in `ResultPageTest`)
```java
@Test void calloutWarnsThatMatchesAreCandidates()
// result(harmonize("PC 34:1")) → #database-caution text contains "Database matches are candidates, not identifications." and "do not report them as identified lipids";
// its a[href=/documentation#databases] exists; it comes after the counts banner in document order
@Test void noCalloutWithoutHarmonizedNames()
// result(harmonize("PC(18:2-20:4")) (only a failing name) → #database-caution absent
@Test void calloutIsGerman()
// page("/result/" + id + "?lang=de") (or the locale mechanism the other German tests use) → #database-caution contains "Datenbanktreffer sind Kandidaten"
```

- [ ] **Step 2: Run them to see them fail**

Run: `./mvnw -B -q test -Dtest='ResultPageTest,MessageKeysTest'`
Expected: the three new tests FAIL.

- [ ] **Step 3: Implement** as listed under Files and Copy.

- [ ] **Step 4: Run the whole suite and e2e**

Run: `./mvnw -B verify` → BUILD SUCCESS. Then `cd e2e && GOSLIN_E2E_PORT=8094 npm test` → all pass. If an e2e screenshot of the result page exists and changed visibly, run `npm run screenshots`, look at the PNGs and commit them separately.

- [ ] **Step 5: Commit**
```bash
git add src/main/resources/templates/result.html src/main/resources/i18n src/test/java
git commit -m "Result page: database matches are candidates, not identifications"
```
