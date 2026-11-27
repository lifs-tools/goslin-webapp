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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.lifstools.jgoslin.domain.LipidCategory;

/**
 * One lipid category with its count and share of read names in each database, for the documentation page. This is
 * the one place that spells the category labels (the LIPID MAPS spelling) and fixes their order.
 *
 * @param code the category abbreviation, e.g. GP, or NONE for names without a class
 * @param label the label shown, e.g. "Glycerophospholipids (GP)"
 * @param cells count and share per database, in the order of the coverage list passed to {@link #of(List)}
 */
public record CategoryCoverage(String code, String label, List<Cell> cells) {

    /**
     * @param count read names of the database in the category
     * @param share percentage of the database's read names, 0 to 100
     */
    public record Cell(long count, double share) {

        /**
         * The share as text with one decimal and a decimal point in every locale, e.g. "29.4 %"; a non-zero
         * count whose share rounds to zero reads "&lt;0.1 %" so it is not mistaken for none.
         */
        public String sharePercent() {
            if (count > 0 && share < 0.05) {
                return "<0.1 %";
            }
            return String.format(Locale.ROOT, "%.1f %%", share);
        }
    }

    /**
     * Whether any database has an entry in this category, i.e. whether it appears in a bar.
     */
    public boolean present() {
        return cells.stream().anyMatch(cell -> cell.count() > 0);
    }

    private record Def(String code, String label, LipidCategory... categories) {

    }

    private static final List<Def> DEFS = List.of(
            new Def("FA", "Fatty acyls (FA)", LipidCategory.FA),
            new Def("GL", "Glycerolipids (GL)", LipidCategory.GL),
            new Def("GP", "Glycerophospholipids (GP)", LipidCategory.GP),
            new Def("SP", "Sphingolipids (SP)", LipidCategory.SP),
            new Def("ST", "Sterol lipids (ST)", LipidCategory.ST),
            new Def("PK", "Polyketides (PK)", LipidCategory.PK),
            new Def("SL", "Saccharolipids (SL)", LipidCategory.SL),
            new Def("NONE", "Not classified", LipidCategory.UNDEFINED, LipidCategory.NO_CATEGORY));

    /**
     * The categories in display order, each with one cell per database.
     */
    public static List<CategoryCoverage> of(List<DatabaseCoverage> coverage) {
        List<CategoryCoverage> rows = new ArrayList<>();
        for (Def def : DEFS) {
            List<Cell> cells = new ArrayList<>();
            for (DatabaseCoverage db : coverage) {
                long count = 0;
                for (LipidCategory category : def.categories()) {
                    count += db.byCategory().getOrDefault(category, 0L);
                }
                double share = db.namesRead() == 0 ? 0 : count / (double) db.namesRead() * 100;
                cells.add(new Cell(count, share));
            }
            rows.add(new CategoryCoverage(def.code(), def.label(), cells));
        }
        return rows;
    }
}
