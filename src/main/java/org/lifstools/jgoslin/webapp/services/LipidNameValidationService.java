/*
 * Copyright 2019 Leibniz Institut für Analytische Wissenschaften - ISAS e.V..
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

import de.isas.lifs.webapps.common.service.AnalyticsTracker;
import org.lifstools.jgoslin.webapp.domain.ValidationResult;
import org.lifstools.jgoslin.webapp.domain.ValidationResult.Grammar;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.lifstools.jgoslin.domain.KnownFunctionalGroups;
import org.lifstools.jgoslin.domain.LipidAdduct;
import org.lifstools.jgoslin.domain.LipidClasses;
import org.lifstools.jgoslin.domain.LipidLevel;
import org.lifstools.jgoslin.domain.LipidParsingException;
import org.lifstools.jgoslin.parser.BaseParserEventHandler;
import org.lifstools.jgoslin.parser.FattyAcidParser;
import org.lifstools.jgoslin.parser.GoslinParser;
import org.lifstools.jgoslin.parser.HmdbParser;
import org.lifstools.jgoslin.parser.LipidMapsParser;
import org.lifstools.jgoslin.parser.LipidParser;
import org.lifstools.jgoslin.parser.Parser;
import org.lifstools.jgoslin.parser.ShorthandParser;
import org.lifstools.jgoslin.parser.SwissLipidsParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author nils.hoffmann
 */
@Service
public class LipidNameValidationService {

    private static final Logger log = LoggerFactory.getLogger(LipidNameValidationService.class);

    public static final String LIPIDMAPS_CLASS_REGEXP = ".+\\[([A-Z0-9]+)\\]";

    private final AnalyticsTracker tracker;
    private final ExternalDatabaseMappingLoader dbLoader;
    private final List<ValidationResult.Grammar> defaultGrammars;
    private final KnownFunctionalGroups knownFunctionalGroups = new KnownFunctionalGroups();
    private final LipidClasses lipidClasses = LipidClasses.getInstance();
    private final Map<Grammar, Parser> grammarToParser = new HashMap<>();
//    private final LipidParser parser = new LipidParser();

    @Autowired
    public LipidNameValidationService(AnalyticsTracker tracker, ExternalDatabaseMappingLoader dbLoader) {
        this.tracker = tracker;
        this.dbLoader = dbLoader;
        this.defaultGrammars = Collections.unmodifiableList(Arrays.asList(
                Grammar.SHORTHAND,
                Grammar.FATTY_ACID,
                Grammar.GOSLIN,
                Grammar.LIPIDMAPS,
                Grammar.SWISSLIPIDS,
                Grammar.HMDB
        )
        );
        grammarToParser.put(Grammar.SHORTHAND, new ShorthandParser(knownFunctionalGroups));
        grammarToParser.put(Grammar.FATTY_ACID, new FattyAcidParser(knownFunctionalGroups));
        grammarToParser.put(Grammar.GOSLIN, new GoslinParser(knownFunctionalGroups));
        grammarToParser.put(Grammar.LIPIDMAPS, new LipidMapsParser(knownFunctionalGroups));
        grammarToParser.put(Grammar.SWISSLIPIDS, new SwissLipidsParser(knownFunctionalGroups));
        grammarToParser.put(Grammar.HMDB, new HmdbParser(knownFunctionalGroups));
    }

    public List<ValidationResult> validate(List<String> lipidNames) {
        return validate(lipidNames, Collections.emptyList());
    }

    public List<ValidationResult> validate(List<String> lipidNames, List<ValidationResult.Grammar> grammars) {
        UUID requestId = UUID.randomUUID();
        tracker.count(requestId, getClass().getSimpleName(), "validate-with-grammars");
        if (grammars.isEmpty()) {
            LipidParser lipidParser = new LipidParser();
            List<ValidationResult> results = lipidNames.stream().map((lipidName) -> {
                return parseWithLipidParser(lipidParser, lipidName.replaceAll("\\s+", " ").trim());
            }).collect(Collectors.toList());
            return results;
        } else {
            List<ValidationResult> results = lipidNames.parallelStream().map((lipidName) -> {
                return parseWith(lipidName.replaceAll("\\s+", " ").trim(), new ArrayDeque<ValidationResult.Grammar>(grammars));
            }).collect(Collectors.toList());
            return results;
        }
    }

    private Parser<LipidAdduct> parserFor(ValidationResult.Grammar grammar) {
        if (grammarToParser.containsKey(grammar)) {
            return grammarToParser.get(grammar);
        }
        throw new RuntimeException("No parser implementation available for grammar '" + grammar + "'!");
    }

    private ValidationResult parseWithLipidParser(LipidParser lipidParser, String lipidName) {
        try {
            LipidAdduct la = lipidParser.parse(lipidName);
            return createValidationResult(la, lipidName, Grammar.forName(lipidParser.getLastSuccessfulGrammar()), "");
        } catch (LipidParsingException lpe) {
            log.debug("Could not parse " + lipidName + " with " + LipidParser.class.getName() + "! Message: " + lpe.getMessage());
            ValidationResult result = new ValidationResult();
            result.setLipidName(lipidName);
            result.setGrammar(Grammar.NONE);
            result.setMessages(Arrays.asList("Could not parse " + lipidName + " with any parser. Original message:" + lpe.getMessage()));
            return result;
        }

    }

    private ValidationResult parseWith(String lipidName, Deque<ValidationResult.Grammar> grammars) {
        log.debug("Grammars left: " + grammars.size());
        ValidationResult.Grammar grammar = grammars.pollFirst();
        Parser<LipidAdduct> parser = parserFor(grammar);
        log.debug("Using grammar " + grammar + " with parser: " + parser.getClass().getSimpleName());
        BaseParserEventHandler<LipidAdduct> handler = parser.newEventHandler();
        LipidAdduct la = parser.parse(lipidName, handler, false);
        if (la != null && handler.getErrorMessage().isEmpty()) {
            String errorMessage = "";
            if (handler.getErrorMessage() != null && !handler.getErrorMessage().isEmpty()) {
                errorMessage = handler.getErrorMessage();
            }
            return createValidationResult(la, lipidName, grammar, errorMessage);
//            result.setFattyAcids(la.getLipid().getFaList());
        } else {
            log.debug("Could not parse " + lipidName + " with " + parser.getClass().getName() + " for grammar " + grammar + "! Message: " + handler.getErrorMessage());
            if (grammars.isEmpty()) {
                ValidationResult result = new ValidationResult();
                result.setLipidName(lipidName);
                result.setGrammar(grammar);
                if (handler.getErrorMessage() == null || handler.getErrorMessage().isEmpty()) {
                    result.setMessages(Arrays.asList("Could not parse " + lipidName + " with any parser. Please check that the headgroup is supported!"));
                } else {
                    result.setMessages(Arrays.asList("Not a valid name after position " + handler.getErrorMessage().length() + ": " + handler.getErrorMessage()));
                }
                return result;
            } else {
                return parseWith(lipidName.trim(), grammars);
            }
        }
    }

    private ValidationResult createValidationResult(LipidAdduct la, String lipidName, Grammar grammar, String errorMessage) {
        ValidationResult result = new ValidationResult();
        result.setLipidName(lipidName);
        result.setLipidAdduct(la);
        result.setGrammar(grammar);
        List<String> messages = Collections.emptyList();
        if (errorMessage != null && !errorMessage.isEmpty()) {
            messages = Arrays.asList(errorMessage);
        }
        result.setMessages(messages);
        result.setLipidMapsCategory(la.getLipid().getHeadGroup().getLipidCategory().name());
//        String speciesName = la.getLipid().getLipidString(LipidLevel.SPECIES);
        try {
            Double mass = la.getMass();
            if (mass == 0.0) {
                mass = Double.NaN;
            }
            result.setMass(mass);
        } catch (RuntimeException re) {
            log.warn("Failed to get mass: ", re);
            result.setMass(Double.NaN);
        }
        try {
            String sumFormula = la.getSumFormula();
            result.setSumFormula(sumFormula);
        } catch (RuntimeException re) {
            log.warn("Failed to get sum formula: ", re);
            result.setSumFormula(null);
        }
        result.setLipidMapsClass(getLipidMapsClassAbbreviation(la));
        //            result.setLipidSpeciesInfo(la.getLipid().getInfo());
        Map<String, Integer> functionalGroupCounts = new TreeMap<>();
        for (String key : la.getLipid().getInfo().getFunctionalGroups().keySet()) {
            functionalGroupCounts.put(key, ValidationResult.getTotalFunctionalGroupCount(la, key));
        }
        result.setFunctionalGroupCounts(functionalGroupCounts);
        try {
            String normalizedName = la.getLipid().getLipidString();
            String lipidSpeciesName = null;
            if (la.getLipidLevel() != LipidLevel.CATEGORY && la.getLipidLevel() != LipidLevel.CLASS && la.getLipidLevel() != LipidLevel.UNDEFINED_LEVEL && la.getLipidLevel() != LipidLevel.NO_LEVEL) {
                lipidSpeciesName = la.getLipidString(LipidLevel.SPECIES);
            }
            result.setLipidCategoryName(nameForLevel(la, LipidLevel.CATEGORY));
            result.setLipidClassName(nameForLevel(la, LipidLevel.CLASS));
            result.setLipidSpeciesName(nameForLevel(la, LipidLevel.SPECIES));
            result.setLipidExtendedSpeciesName(la.getExtendedClass());
            result.setLipidMolecularSpeciesName(nameForLevel(la, LipidLevel.MOLECULAR_SPECIES));
            result.setLipidSnPositionName(nameForLevel(la, LipidLevel.SN_POSITION));
            result.setLipidStructureDefinedName(nameForLevel(la, LipidLevel.STRUCTURE_DEFINED));
            result.setLipidFullStructureName(nameForLevel(la, LipidLevel.FULL_STRUCTURE));
            result.setLipidCompleteStructureName(nameForLevel(la, LipidLevel.COMPLETE_STRUCTURE));
            result.setNormalizedName(normalizedName);
            result.setLipidMapsReferences(dbLoader.findLipidMapsEntry(normalizedName, lipidName, lipidSpeciesName));
            result.setSwissLipidsReferences(dbLoader.findSwissLipidsEntry(normalizedName, lipidName, lipidSpeciesName));
            result.setChebiReferences(dbLoader.findChebiEntry(normalizedName, lipidName, lipidSpeciesName));
        } catch (RuntimeException re) {
            log.debug("Error while trying to resolve database hits for {}!", lipidName);
        }
        return result;
    }

    private String getLipidMapsClassAbbreviation(LipidAdduct la) {
        String lipidMapsClass = lipidClasses.get(la.getLipid().getHeadGroup().getLipidClass()).description;
        Pattern lmcRegexp = Pattern.compile(LIPIDMAPS_CLASS_REGEXP);
        Matcher lmcMatcher = lmcRegexp.matcher(lipidMapsClass);
        if (lmcMatcher.matches() && lmcMatcher.groupCount() == 1) {
            lipidMapsClass = lmcMatcher.group(1);
        } else {
            lipidMapsClass = null;
        }
        return lipidMapsClass;
    }

    public String nameForLevel(LipidAdduct la, LipidLevel level) {
        if (level.level <= la.getLipidLevel().level) {
            return la.getLipidString(level);
        }
        return "";
    }
}
