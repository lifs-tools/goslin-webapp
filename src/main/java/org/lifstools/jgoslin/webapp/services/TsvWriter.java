package org.lifstools.jgoslin.webapp.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.lifstools.jgoslin.domain.FattyAcid;
import org.lifstools.jgoslin.domain.FunctionalGroup;
import org.lifstools.jgoslin.domain.LipidClassMeta;
import org.lifstools.jgoslin.domain.LipidClasses;
import org.lifstools.jgoslin.domain.LipidLevel;
import org.lifstools.jgoslin.domain.LipidSpeciesInfo;
import org.lifstools.jgoslin.webapp.domain.NameEntry;
import org.lifstools.jgoslin.webapp.domain.ResultView;
import org.lifstools.jgoslin.webapp.domain.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Writes the downloadable table: one row per active entry in line order,
 * failed entries included.
 */
@Service
public class TsvWriter {

    private static final Logger log = LoggerFactory.getLogger(TsvWriter.class);

    private final LipidNameValidationService lipidNameValidationService;
    private final LipidClasses lipidClasses = LipidClasses.getInstance();

    public TsvWriter(LipidNameValidationService lipidNameValidationService) {
        this.lipidNameValidationService = lipidNameValidationService;
    }

    public String write(ResultView view) {
        record Item(NameEntry entry, ValidationResult t) {
        }
        List<Item> items = new ArrayList<>();
        view.harmonized().forEach(r -> items.add(new Item(r.entry(), r.result())));
        view.failed().forEach(r -> items.add(new Item(r.entry(), r.result())));
        items.sort(Comparator.comparingInt(i -> i.entry().line()));

        StringBuilder sb = new StringBuilder();
        Set<String> keys = new LinkedHashSet<>();
        List<Map<String, String>> entries = new ArrayList<>(items.size());
        for (Item item : items) {
            NameEntry entry = item.entry();
            ValidationResult t = item.t();
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Line", String.valueOf(entry.line()));
        m.put("Normalized Name", t.getNormalizedName()==null?"":t.getNormalizedName());
        m.put("Original Name", entry.original());
        m.put("Corrected Name", entry.corrected() ? entry.current() : "");
        m.put("Grammar", t.getGrammar().name());
        m.put("Validation Messages", t.getMessages().stream().collect(Collectors.joining(" | ")));
        if (t.getLipidAdduct() == null) {
            log.debug("Failed to parse lipid name: {}", entry.current());
        } else {
            if (t.getLipidAdduct() == null || t.getLipidAdduct().getAdduct() == null) {
                m.put("Adduct", "");
            } else {
                m.put("Adduct", t.getLipidAdduct().getAdduct().getLipidString());
                log.debug("Lipid name with adduct: {}", t.getLipidAdduct().getLipidString());
            }
            m.put("Lipid Maps Category", t.getLipidAdduct().getLipid().getHeadGroup().getLipidCategory().getFullName() + " [" + t.getLipidAdduct().getLipid().getHeadGroup().getLipidCategory().name() + "]");
            LipidClassMeta lclass = lipidClasses.get(t.getLipidAdduct().getLipid().getHeadGroup().getLipidClass());
            m.put("Lipid Maps Main Class", lclass.getDescription());
            m.put("Lipid Category Name", lipidNameValidationService.nameForLevel(t.getLipidAdduct(), LipidLevel.CATEGORY));
            m.put("Lipid Class Name", lipidNameValidationService.nameForLevel(t.getLipidAdduct(), LipidLevel.CLASS));
            m.put("Extended Species Name", t.getLipidAdduct().getExtendedClass());
            m.put("Species Name", lipidNameValidationService.nameForLevel(t.getLipidAdduct(), LipidLevel.SPECIES));
            m.put("Molecular Species Name", lipidNameValidationService.nameForLevel(t.getLipidAdduct(), LipidLevel.MOLECULAR_SPECIES));
            m.put("SN Position Name", lipidNameValidationService.nameForLevel(t.getLipidAdduct(), LipidLevel.SN_POSITION));
            m.put("Structure Defined Name", lipidNameValidationService.nameForLevel(t.getLipidAdduct(), LipidLevel.STRUCTURE_DEFINED));
            m.put("Full Structure Name", lipidNameValidationService.nameForLevel(t.getLipidAdduct(), LipidLevel.FULL_STRUCTURE));
            m.put("Complete Structure Name", lipidNameValidationService.nameForLevel(t.getLipidAdduct(), LipidLevel.COMPLETE_STRUCTURE));
            m.put("Lipid Maps References", t.getLipidMapsReferences().stream().flatMap(Collection::stream).map((r) -> {
                return r.getDatabaseUrl() + r.getDatabaseElementId();
            }).collect(Collectors.joining(" | ")));
            m.put("Swiss Lipids References", t.getSwissLipidsReferences().stream().flatMap(Collection::stream).map((r) -> {
                return r.getDatabaseUrl() + r.getDatabaseElementId();
            }).collect(Collectors.joining(" | ")));
            m.put("ChEBI References", t.getChebiReferences().stream().flatMap(Collection::stream).map((r) -> {
                return r.getDatabaseUrl();
            }).collect(Collectors.joining(" | ")));
            m.put("Functional Class Abbr", "[" + lclass.getClassName() + "]");
            m.put("Functional Class Synonyms", "[" + lclass.getSynonyms().stream().collect(Collectors.joining(", ")) + "]");
            LipidSpeciesInfo info = t.getLipidAdduct().getLipid().getInfo();
            m.put("Level", info.getLevel().toString());
            m.put("Total #C", info.getNumCarbon() + "");
            Map<String, ArrayList<FunctionalGroup>> functionalGroups = info.getFunctionalGroups();
            m.put("Total Modifications", functionalGroups.entrySet().stream().filter(grp -> !grp.getKey().equals("[X]")).map((grp) -> {
                return grp.getKey()+"=["+grp.getValue().stream().map((functionalGroup) -> {
                    return functionalGroup.toString(info.getLevel());
                }).collect(Collectors.joining(","))+"]";
            }).collect(Collectors.joining(",", "[", "]")) + "");
            //            m.put("Total #OH", t.getLipidSpeciesInfo().getNHydroxy() + "");
            m.put("Total #DB", info.getDoubleBonds().getNumDoubleBonds() + "");
            m.put("Exact Mass", String.format(Locale.US, "%.4f", t.getMass()));
            m.put("Formula", t.getSumFormula());
            for (FattyAcid fa : t.getLipidAdduct().getLipid().getFaList()) {
                if (fa.getCount() > 0) {
                    m.put(fa.getName() + " SN Position", fa.getPosition() + "");
                    m.put(fa.getName() + " #C", fa.getNumCarbon() + "");
                    //                m.put(fa.name + " #OH", fa.getNHydroxy() + "");
                    m.put(fa.getName() + " #DB", fa.getDoubleBonds().getNumDoubleBonds() + "");
                    m.put(fa.getName() + " Bond Type", fa.getLipidFaBondType() + "");
                    m.put(fa.getName() + " DB Positions", fa.getDoubleBonds().getDoubleBondPositions().entrySet().stream().map((dbPosEntry) -> {
                        return dbPosEntry.getKey() + "" + dbPosEntry.getValue();
                    }).collect(Collectors.toList()) + "");

                    fa.getFunctionalGroups().entrySet().stream().forEachOrdered((grp) -> {
                        if(!grp.getKey().equals("[X]")) {
                            m.put(fa.getName() + " " + grp.getKey(),
                                grp.getValue().stream().map((functionalGroup) -> {
                                    return functionalGroup.toString(info.getLevel());
                                }).collect(Collectors.joining(",", "[", "]")));
                        }
                    });
                }
            }
        }
            keys.addAll(m.keySet());
            entries.add(m);
        }
        sb.append(String.join("\t", keys)).append("\n");
        for (Map<String, String> m : entries) {
            List<String> l = new ArrayList<>(keys.size());
            for (String key : keys) {
                l.add(m.getOrDefault(key, ""));
            }
            sb.append(String.join("\t", l)).append("\n");
        }
        return sb.toString();
    }
}
