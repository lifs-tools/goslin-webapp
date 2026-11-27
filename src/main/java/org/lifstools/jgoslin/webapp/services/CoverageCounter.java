/*
 * Copyright 2026 Leibniz Institut für Analytische Wissenschaften - ISAS e.V..
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lifstools.jgoslin.webapp.services;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import org.lifstools.jgoslin.domain.Headgroup;
import org.lifstools.jgoslin.domain.LipidAdduct;
import org.lifstools.jgoslin.domain.LipidCategory;
import org.lifstools.jgoslin.domain.LipidClassMeta;
import org.lifstools.jgoslin.domain.LipidClasses;
import org.lifstools.jgoslin.webapp.domain.DatabaseCoverage;

/**
 * Counts the entries of one database while it loads. Not thread safe; head group lookups fill static tables
 * of jgoslin, so call it from one thread.
 */
final class CoverageCounter {

    private final DatabaseCoverage.Database database;
    private final Map<LipidCategory, Long> byCategory = new EnumMap<>(LipidCategory.class);
    private final Map<String, Long> byClass = new TreeMap<>();
    private final Set<String> loadedCompounds = new HashSet<>();
    private final Set<String> readCompounds = new HashSet<>();
    private long entriesLoaded;
    private long namesRead;

    CoverageCounter(DatabaseCoverage.Database database) {
        this.database = database;
    }

    /**
     * A LIPID MAPS or SwissLipids row; null, empty, {@code UNDEFINED} and {@code NA} names are not read.
     */
    void countName(String normalizedName) {
        entriesLoaded++;
        if (normalizedName == null || normalizedName.isEmpty() || "UNDEFINED".equals(normalizedName)
                || "NA".equals(normalizedName)) {
            return;
        }
        namesRead++;
        OptionalInt classIndex = classOf(normalizedName);
        if (classIndex.isPresent()) {
            LipidClassMeta meta = LipidClasses.getInstance().get(classIndex.getAsInt());
            increment(meta.lipidCategory, meta.lipidClassName);
        } else {
            increment(LipidCategory.UNDEFINED, null);
        }
    }

    /**
     * A ChEBI name row. ChEBI is counted per compound: the compound is loaded once however many names it has, and
     * read when at least one of its names was parsed; the category and class come from the first parsed name.
     *
     * @param compoundId the ChEBI compound id of the row
     * @param adduct the parsed name, null when Goslin could not read it
     */
    void countChebiName(String compoundId, LipidAdduct adduct) {
        loadedCompounds.add(compoundId);
        if (adduct == null || !readCompounds.add(compoundId)) {
            return;
        }
        namesRead++;
        Headgroup headGroup = adduct.getLipid().getHeadGroup();
        int classIndex = headGroup.getLipidClass();
        LipidCategory category = headGroup.getLipidCategory();
        boolean classified = classIndex != LipidClasses.UNDEFINED_CLASS && category != null
                && category != LipidCategory.UNDEFINED && category != LipidCategory.NO_CATEGORY;
        if (classified) {
            increment(category, LipidClasses.getInstance().get(classIndex).lipidClassName);
        } else {
            increment(LipidCategory.UNDEFINED, null);
        }
    }

    DatabaseCoverage build() {
        return new DatabaseCoverage(database, database == DatabaseCoverage.Database.CHEBI ? loadedCompounds.size() : entriesLoaded, namesRead,
                Collections.unmodifiableMap(new EnumMap<>(byCategory)),
                Collections.unmodifiableMap(new TreeMap<>(byClass)));
    }

    /**
     * The class of the head group at the start of the name, without parsing. Some class names contain a space
     * (e.g. {@code ST 28:2;O}), so the longest known prefix wins: the whole name, the name cut at the first
     * {@code /} or {@code _}, the first two space separated tokens, the first token and, when that is unknown
     * and has a bracket, the part before the bracket.
     *
     * @return the index into {@link LipidClasses}, empty when the head group is unknown
     */
    static OptionalInt classOf(String normalizedName) {
        int classIndex = Headgroup.getClass(normalizedName);
        if (classIndex == LipidClasses.UNDEFINED_CLASS) {
            int cut = firstIndexOf(normalizedName, '/', '_');
            if (cut > 0) {
                classIndex = Headgroup.getClass(normalizedName.substring(0, cut));
            }
        }
        if (classIndex == LipidClasses.UNDEFINED_CLASS) {
            int first = normalizedName.indexOf(' ');
            int second = first < 0 ? -1 : normalizedName.indexOf(' ', first + 1);
            if (second > 0) {
                classIndex = Headgroup.getClass(normalizedName.substring(0, second));
            }
            String token = first < 0 ? normalizedName : normalizedName.substring(0, first);
            if (classIndex == LipidClasses.UNDEFINED_CLASS) {
                classIndex = Headgroup.getClass(token);
            }
            int bracket = token.indexOf('(');
            if (classIndex == LipidClasses.UNDEFINED_CLASS && bracket > 0) {
                classIndex = Headgroup.getClass(token.substring(0, bracket));
            }
        }
        if (classIndex == LipidClasses.UNDEFINED_CLASS) {
            return OptionalInt.empty();
        }
        LipidCategory category = LipidClasses.getInstance().get(classIndex).lipidCategory;
        if (category == LipidCategory.UNDEFINED || category == LipidCategory.NO_CATEGORY) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(classIndex);
    }

    private static int firstIndexOf(String text, char a, char b) {
        int ia = text.indexOf(a);
        int ib = text.indexOf(b);
        return ia < 0 ? ib : ib < 0 ? ia : Math.min(ia, ib);
    }

    private void increment(LipidCategory category, String className) {
        byCategory.merge(category, 1L, Long::sum);
        if (className != null) {
            byClass.merge(className, 1L, Long::sum);
        }
    }
}
