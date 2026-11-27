# Database matches: caution on the result page, coverage on the documentation page

Date: 2026-11-27. Status: design for review.

## Why

Goslin lists LIPID MAPS, SwissLipids and ChEBI entries for each harmonized name. Users
read these matches as identifications. They are not: Goslin matches on the shorthand name,
including the species-level name (`LipidNameValidationService.java:634-636` looks up the
normalized name, the name at the given level and the species name). LIPID MAPS files many
fully defined structures under a bulk abbreviation: 18,806 of the 28,942 loaded LMSD entries
are keyed at species level, `FA 18:1` matches 43 entries and `PC 34:1` matches 22. What
matches also depends on what each database contains, and the three databases differ in kind
and in coverage.

Goal: users treat matches as candidates, and can see what each database covers and where
its gaps are.

Non-goals: updating the database dumps (issue #129); changing how matching works; ranking
or filtering matches.

Anti-goal: numbers on the page that differ from what the running application actually
matches against.

## Approaches

1. **Statistics computed at startup from the loaded data** (chosen). The loader already
   reads every entry; the class of each LIPID MAPS and SwissLipids entry comes from a head
   group lookup on its normalized name (`Headgroup.getClass` on the token before the first
   space, cut at `(`), ChEBI entries already carry a parsed `LipidAdduct`. Measured: 99.9 %
   of LIPID MAPS and 99.2 % of SwissLipids entries classify, in about a second. The numbers
   always match the loaded data, including changes from a new dump or a new jgoslin.
2. A statistics file made offline and committed next to the dumps. No startup cost, but it
   drifts from the data (the ChEBI count moved from 2,079 to 2,138 with a jgoslin rebuild
   that nobody noticed).
3. Parsing every distinct normalized name at startup (about 720,000). Exact, but far too slow.

## Design

### 1. Result page callout

Below the counts banner on `result.html`, shown whenever the result has at least one
harmonized name. EN and DE message keys. Approved EN wording:

> **Database matches are candidates, not identifications.**
> Goslin lists LIPID MAPS, SwissLipids and ChEBI entries whose shorthand name matches yours,
> including at species level (e.g. PC 34:1). A match means an entry is consistent with your
> name, not that it is your lipid. The less specific the name, the more structures match:
> FA 18:1 matches dozens of LIPID MAPS entries. What you see also depends on what each
> database contains. Classes a database covers poorly return few or no matches. Treat matched
> entries as candidates, check them against your evidence, and do not report them as
> identified lipids. [How complete are the databases?](/documentation#databases)

### 2. Documentation page: "Databases" section

New section `#databases` before the lipid class table, with a table of contents entry.
English only, like the neighbouring documentation sections.

Per database: what kind of database it is, its version, entries loaded, entries with a name
Goslin reads, and entries Goslin could assign to a lipid class.

| Database | Kind (EN text) | Version |
|---|---|---|
| LIPID MAPS (LMSD) | Structures mostly reported for lipids measured from authentic samples. Many are filed under species-level abbreviations such as PC 34:1. Goslin uses LMSD only, not the computationally generated COMPDB. | 3 March 2023 |
| SwissLipids | Generated combinatorially from known building blocks: it lists structures that are biochemically plausible, not structures that were observed. | early 2023 |
| ChEBI | Manually curated chemical entities. It holds far fewer lipids than LIPID MAPS or SwissLipids, and names them by trivial or systematic names rather than by the shorthand nomenclature, especially at its intermediate levels. Goslin reads only a small share of its names. | 18 January 2024 |

Footnote: the LIPID MAPS file holds only the entries that were normalized; the total size of
the March 2023 release was not recorded (issue #129).

Chart: one horizontal 100 % bar per database, split by lipid category (FA, GL, GP, SP, ST,
other / not classified), because the databases differ about 300-fold in size. Exact counts
and shares in a table next to it (the accessible form of the chart). Server-rendered HTML/SVG,
LIFS colour tokens, no new library.

### 3. Lipid class table: coverage columns

Three columns LIPID MAPS, SwissLipids, ChEBI with the number of entries per class. A class a
database does not cover shows "none", visibly marked, so gaps stand out. The existing filter
keeps working.

### 4. Versions

`application.properties` holds each database's display name and version text
(`goslin.databases.lipidmaps.version=3 March 2023` etc.), since the dump files carry no date.

## Decided by the builder (Claude's calls)

- Counts are entries (database records), not distinct names. ChEBI "entries loaded" is all
  name rows in the dump (381,571); "names Goslin reads" are the parsed synonyms (about 2,100).
- Entries whose head group does not classify count as "not classified"; they are not parsed.
- The statistics are built once, in the loader, and exposed read-only; the result page and
  REST API are unchanged apart from the callout.
- Tests: statistics from a small fixture (counts, classification, the "not classified"
  bucket); documentation page renders the section, the chart's table and the class
  columns; result page shows the callout with the link when there are harmonized names.
