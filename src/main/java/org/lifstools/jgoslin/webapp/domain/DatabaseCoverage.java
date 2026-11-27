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
package org.lifstools.jgoslin.webapp.domain;

import java.util.Map;
import org.lifstools.jgoslin.domain.LipidCategory;

/**
 * How many entries of an external database were loaded and how many of their names were classified.
 *
 * @param database the database
 * @param entriesLoaded number of entries loaded: rows for LIPID MAPS and SwissLipids, compounds for ChEBI
 * @param namesRead entries with a lipid name that could be read (not undefined, not "NA", not unparsable)
 * @param byCategory read entries per lipid category (for ChEBI: compounds); only counts above zero, plus {@link LipidCategory#UNDEFINED}
 * for read names without a known class
 * @param byClass read names per lipid class, keyed by the class name
 */
public record DatabaseCoverage(Database database, long entriesLoaded, long namesRead,
        Map<LipidCategory, Long> byCategory, Map<String, Long> byClass) {

    public enum Database {
        LIPID_MAPS("LIPID MAPS"), SWISS_LIPIDS("SwissLipids"), CHEBI("ChEBI");

        private final String displayName;

        Database(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * Read names that were assigned to a lipid class.
     */
    public long classified() {
        return namesRead - byCategory.getOrDefault(LipidCategory.UNDEFINED, 0L);
    }

    public long countForClass(String lipidClassName) {
        return byClass.getOrDefault(lipidClassName, 0L);
    }

    /**
     * Percentage of read names in the category; 0 when nothing was read.
     */
    public double share(LipidCategory category) {
        if (namesRead == 0) {
            return 0;
        }
        return byCategory.getOrDefault(category, 0L) / (double) namesRead * 100;
    }
}
