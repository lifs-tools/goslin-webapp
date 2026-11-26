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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.lifstools.jgoslin.domain.FattyAcid;
import org.lifstools.jgoslin.domain.LipidAdduct;
import org.lifstools.jgoslin.domain.LipidClassMeta;
import org.lifstools.jgoslin.domain.LipidClasses;
import org.lifstools.jgoslin.domain.LipidLevel;
import org.lifstools.jgoslin.domain.LipidSpeciesInfo;

/**
 * One harmonized name as the result table shows it: the main row and its details row.
 * Values that are awkward to derive in the template are computed here.
 *
 * @param entry the line-numbered name
 * @param result Goslin's result; its lipid adduct is not null
 * @param level the structural level, a {@link LipidLevel} name (also a message key)
 * @param levelOrder the level's ordinal, for sorting
 * @param category the LIPID MAPS category code, e.g. {@code GP}
 * @param categoryName the category's full name with its code, e.g. {@code Glycerophospholipids [GP]}
 * @param className the LIPID MAPS main or sub class description
 * @param mass the exact mass, or null if Goslin could not compute one
 * @param numCarbon C atoms of all chains, or null if there are none
 * @param doubleBonds double bonds of all chains, or null if there are no chains
 * @param faBondType the lipid's overall FA bond type (a message key), or null when the chain table shows it
 * @param chains the chain table, empty if not shown
 * @param search the text the free-text filter matches: submitted and original name, harmonized name,
 * class and formula
 */
public record ResultTableRow(NameEntry entry, ValidationResult result, String level, int levelOrder,
        String category, String categoryName, String className, Double mass, Integer numCarbon,
        Integer doubleBonds, String faBondType, List<Chain> chains, String search) {

    /**
     * One row of the chain table.
     *
     * @param position the sn position, or null if not defined
     * @param bondType the bond to the head group, a {@code LipidFaBondType} name (also a message key)
     * @param doubleBondPositions e.g. {@code 9Z, 12Z}, or null if not defined
     * @param functionalGroups Goslin's functional group string, or null if there are none
     */
    public record Chain(String name, String position, int numCarbon, int doubleBonds, String bondType,
            String doubleBondPositions, String functionalGroups) {
    }

    public static ResultTableRow of(ResultView.Row row) {
        NameEntry entry = row.entry();
        ValidationResult result = row.result();
        LipidAdduct adduct = Objects.requireNonNull(result.getLipidAdduct(), "only harmonized rows");
        LipidLevel lipidLevel = adduct.getLipidLevel();
        var headGroup = adduct.getLipid().getHeadGroup();
        var lipidCategory = headGroup.getLipidCategory();
        LipidClassMeta meta = LipidClasses.getInstance().get(headGroup.getLipidClass());
        String className = meta == null ? "" : meta.getDescription();
        Double mass = result.getMass() == null || result.getMass().isNaN() || result.getMass() <= 0
                ? null : result.getMass();

        LipidSpeciesInfo info = adduct.getLipid().getInfo();
        Integer numCarbon = null;
        Integer doubleBonds = null;
        if (info != null && info.getNumCarbon() > 0) {
            numCarbon = info.getNumCarbon();
            doubleBonds = info.getNDoubleBonds() >= 0 ? info.getNDoubleBonds() : null;
        }
        List<FattyAcid> faList = adduct.getLipid().getFaList();
        boolean hasChains = faList != null && !faList.isEmpty();
        String faBondType = !hasChains && info != null && info.getLipidFaBondType() != null
                ? info.getLipidFaBondType().name() : null;
        List<Chain> chains = new ArrayList<>();
        if (hasChains && lipidLevel != LipidLevel.SPECIES) {
            for (FattyAcid fa : faList) {
                chains.add(chain(adduct, fa));
            }
        }
        String search = Stream.of(entry.current(), entry.corrected() ? entry.original() : null,
                result.getNormalizedName(), result.getLipidClassName(), className, result.getSumFormula())
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining(" "));
        return new ResultTableRow(entry, result, lipidLevel.name(), lipidLevel.ordinal(), lipidCategory.name(),
                lipidCategory.getFullName() + " [" + lipidCategory.name() + "]", className, mass, numCarbon,
                doubleBonds, faBondType, List.copyOf(chains), search);
    }

    private static Chain chain(LipidAdduct adduct, FattyAcid fa) {
        String position = fa.getPosition() > 0 ? "sn" + fa.getPosition() : null;
        String dbPositions = null;
        Map<Integer, String> positions = fa.getDoubleBonds() == null ? Map.of() : fa.getDoubleBonds().getDoubleBondPositions();
        if (fa.getNDoubleBonds() > 0 && !positions.isEmpty()) {
            dbPositions = positions.entrySet().stream()
                    .map(e -> e.getKey() + Objects.toString(e.getValue(), ""))
                    .collect(Collectors.joining(", "));
        }
        String groups = fa.getFunctionalGroups().isEmpty() ? null : ValidationResult.toFunctionalGroupString(adduct, fa);
        String bondType = fa.getLipidFaBondType() == null ? null : fa.getLipidFaBondType().name();
        return new Chain(fa.getName(), position, fa.getNumCarbon(), fa.getNDoubleBonds(), bondType, dbPositions, groups);
    }

    /** Whether the category links to LIPID MAPS. */
    public boolean hasLipidMapsCategory() {
        return result.getLipidMapsCategory() != null && !"UNDEFINED".equals(result.getLipidMapsCategory());
    }

    public Collection<ExternalDatabaseReference> lipidMapsEntries() {
        return entries(result.getLipidMapsReferences());
    }

    public Collection<ExternalDatabaseReference> swissLipidsEntries() {
        return entries(result.getSwissLipidsReferences());
    }

    public Collection<ExternalDatabaseReference> chebiEntries() {
        return entries(result.getChebiReferences());
    }

    /** Whether any database has an entry for this name. */
    public boolean hasDatabaseEntries() {
        return !lipidMapsEntries().isEmpty() || !swissLipidsEntries().isEmpty() || !chebiEntries().isEmpty();
    }

    /** The adduct as Goslin writes it, {@code [M]} if there is none. */
    public String adduct() {
        var adduct = result.getLipidAdduct().getAdduct();
        String text = adduct == null ? "" : adduct.getLipidString();
        return text == null || text.isEmpty() ? "[M]" : text;
    }

    private static Collection<ExternalDatabaseReference> entries(Optional<Collection<ExternalDatabaseReference>> refs) {
        return refs == null ? List.of() : refs.orElse(List.of());
    }
}
