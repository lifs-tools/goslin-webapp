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

import org.lifstools.jgoslin.webapp.domain.ValidationResult;
import org.lifstools.jgoslin.webapp.domain.ValidationResult.Grammar;
import de.isas.lifs.webapps.common.service.AnalyticsTracker;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.lifstools.jgoslin.domain.KnownFunctionalGroups;
import org.lifstools.jgoslin.domain.LipidAdduct;
import org.lifstools.jgoslin.domain.LipidClasses;
import org.lifstools.jgoslin.domain.LipidLevel;
import org.lifstools.jgoslin.domain.StringFunctions;
import static org.lifstools.jgoslin.domain.StringFunctions.DEFAULT_QUOTE;
import org.lifstools.jgoslin.parser.BaseParserEventHandler;
import org.lifstools.jgoslin.parser.FattyAcidParser;
import org.lifstools.jgoslin.parser.GoslinParser;
import org.lifstools.jgoslin.parser.HmdbParser;
import org.lifstools.jgoslin.parser.LipidMapsParser;
import org.lifstools.jgoslin.parser.Parser;
import org.lifstools.jgoslin.parser.ShorthandParser;
import org.lifstools.jgoslin.parser.SumFormulaParser;
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
    private final SumFormulaParser sfp = SumFormulaParser.newInstance();
    private final KnownFunctionalGroups knownFunctionalGroups = new KnownFunctionalGroups(StringFunctions.getResourceAsStringList("functional-groups.csv"), sfp);
    private final LipidClasses lipidClasses = LipidClasses.getInstance();

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
    }

    public List<ValidationResult> validate(List<String> lipidNames) {
        return validate(lipidNames, defaultGrammars);
    }

    public List<ValidationResult> validate(List<String> lipidNames, List<ValidationResult.Grammar> grammars) {
        UUID requestId = UUID.randomUUID();
        tracker.count(requestId, getClass().getSimpleName(), "validate-with-grammars");
        List<ValidationResult> results = lipidNames.parallelStream().map((lipidName) -> {
            return parseWith(lipidName.trim().replaceAll("\\s+", " "), new ArrayDeque<ValidationResult.Grammar>(grammars));
        }).collect(Collectors.toList());
        return results;
    }

    private Parser<LipidAdduct> parserFor(ValidationResult.Grammar grammar) {
        switch (grammar) {
            case SHORTHAND:
                return ShorthandParser.newInstance(knownFunctionalGroups, "Shorthand2020.g4", DEFAULT_QUOTE);
            case FATTY_ACID:
                return FattyAcidParser.newInstance(knownFunctionalGroups, "FattyAcids.g4", DEFAULT_QUOTE);
            case GOSLIN:
                return GoslinParser.newInstance(knownFunctionalGroups, "Goslin.g4", DEFAULT_QUOTE);
            //            case GOSLIN_FRAGMENTS:
            //                return new GoslinFragmentsVisitorParser();
            //break;
            case LIPIDMAPS:
                return LipidMapsParser.newInstance(knownFunctionalGroups, "LipidMaps.g4", DEFAULT_QUOTE);
            case SWISSLIPIDS:
                return SwissLipidsParser.newInstance(knownFunctionalGroups, "SwissLipids.g4", DEFAULT_QUOTE);
            case HMDB:
                return HmdbParser.newInstance(knownFunctionalGroups, "HMDB.g4", DEFAULT_QUOTE);
        }
        throw new RuntimeException("No parser implementation available for grammar '" + grammar + "'!");
    }

    private ValidationResult parseWith(String lipidName, Deque<ValidationResult.Grammar> grammars) {
        log.debug("Grammars left: " + grammars.size());
        ValidationResult.Grammar grammar = grammars.pollFirst();
        Parser<LipidAdduct> parser = parserFor(grammar);
        log.debug("Using grammar " + grammar + " with parser: " + parser.getClass().getSimpleName());
        BaseParserEventHandler<LipidAdduct> handler = parser.newEventHandler();
        LipidAdduct la = parser.parse(lipidName, handler, false);
        if (la != null && handler.getErrorMessage().isEmpty()) {
            ValidationResult result = new ValidationResult();
            result.setLipidName(lipidName);
            result.setLipidAdduct(la);
            result.setGrammar(grammar);
            List<String> messages = Collections.emptyList();
            if (handler.getErrorMessage() != null && !handler.getErrorMessage().isEmpty()) {
                messages = Arrays.asList(handler.getErrorMessage());
            }
            //            if (la != null) {
            //                long fasWithModifications = la.lipid.getFa().entrySet().stream().filter((t) -> {
            //                    return !t.getValue().getModifications().isEmpty();
            //                }).count();
            ////                if (fasWithModifications > 0) {
            ////                    messages.add(lipidName + " contains modifications. At the moment, Goslin only supports proper handling of hydroxylations concerning the number of hydroxylations, sum formula and mass. This will be resolved as soon as the next update to the lipid shorthand nomenclature is published, which will include all LIPID MAPS and SwissLipids modifications.");
            ////                }
            //            }
            result.setMessages(messages);

            result.setLipidMapsCategory(la.getLipid().getHeadGroup().getLipidCategory().name());
            String speciesName = la.getLipid().getLipidString(LipidLevel.SPECIES);
            Double mass = la.getMass();
            if (mass == 0.0) {
                mass = Double.NaN;
            }
            result.setMass(mass);
            result.setSumFormula(la.getSumFormula());
            result.setLipidMapsClass(getLipidMapsClassAbbreviation(la));
            result.setLipidSpeciesInfo(la.getLipid().getInfo());
            try {
                String normalizedName = la.getLipid().getLipidString();
                result.setNormalizedName(normalizedName);
                result.setLipidMapsReferences(dbLoader.findLipidMapsEntry(normalizedName));
                result.setSwissLipidsReferences(dbLoader.findSwissLipidsEntry(normalizedName, lipidName));
            } catch (RuntimeException re) {
                log.debug("Error while trying to resolve database hits for {}!", lipidName);
            }
            result.setFattyAcids(la.getLipid().getFaList());
            return result;
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

//    private List<String> toStringMessages(SyntaxErrorListener listener) {
//        return listener.getSyntaxErrors().stream().map((syntaxError) -> {
//            return syntaxError.getMessage();
//        }).collect(Collectors.toList());
//    }
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
}
