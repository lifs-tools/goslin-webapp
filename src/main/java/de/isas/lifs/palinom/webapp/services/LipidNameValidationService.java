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
package de.isas.lifs.palinom.webapp.services;

import de.isas.lifs.palinom.webapp.domain.ValidationResult;
import de.isas.lifs.palinom.webapp.domain.ValidationResult.Grammar;
import de.isas.lifs.webapps.common.service.AnalyticsTracker;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.lifstools.jgoslin.domain.LipidAdduct;
import org.lifstools.jgoslin.domain.LipidClasses;
import org.lifstools.jgoslin.domain.LipidLevel;
import org.lifstools.jgoslin.parser.FattyAcidParser;
import org.lifstools.jgoslin.parser.GoslinParser;
import org.lifstools.jgoslin.parser.HmdbParser;
import org.lifstools.jgoslin.parser.LipidMapsParser;
import org.lifstools.jgoslin.parser.Parser;
import org.lifstools.jgoslin.parser.ShorthandParser;
import org.lifstools.jgoslin.parser.SwissLipidsParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author nils.hoffmann
 */
@Slf4j
@Service
public class LipidNameValidationService {

    public static final String LIPIDMAPS_CLASS_REGEXP = ".+\\[([A-Z0-9]+)\\]";

    private final AnalyticsTracker tracker;
    private final ExternalDatabaseMappingLoader dbLoader;
    private final List<ValidationResult.Grammar> defaultGrammars;
    private final LipidClasses lipidClasses = LipidClasses.get_instance();

    @Autowired
    public LipidNameValidationService(AnalyticsTracker tracker, ExternalDatabaseMappingLoader dbLoader) {
        this.tracker = tracker;
        this.dbLoader = dbLoader;
        this.defaultGrammars = Collections.unmodifiableList(Arrays.asList(Grammar.SHORTHAND, Grammar.FATTY_ACID, Grammar.GOSLIN, Grammar.LIPIDMAPS, Grammar.SWISSLIPIDS, Grammar.HMDB));
    }

    public List<ValidationResult> validate(List<String> lipidNames) {
        return validate(lipidNames, defaultGrammars);
    }

    public List<ValidationResult> validate(List<String> lipidNames, List<ValidationResult.Grammar> grammars) {
        tracker.count(UUID.randomUUID(), getClass().getSimpleName(), "validate-with-grammars");
        return lipidNames.parallelStream().map((lipidName) -> {
            return parseWith(lipidName.trim(), new ArrayDeque<ValidationResult.Grammar>(grammars));
        }).collect(Collectors.toList());
    }

    private Parser<LipidAdduct> parserFor(ValidationResult.Grammar grammar) {
        switch (grammar) {
            case FATTY_ACID:
                return FattyAcidParser.newInstance();
            case SHORTHAND:
                return ShorthandParser.newInstance();
            case GOSLIN:
                return GoslinParser.newInstance();
            //            case GOSLIN_FRAGMENTS:
            //                return new GoslinFragmentsVisitorParser();
            //break;
            case LIPIDMAPS:
                return LipidMapsParser.newInstance();
            case SWISSLIPIDS:
                return SwissLipidsParser.newInstance();
            case HMDB:
                return HmdbParser.newInstance();
        }
        throw new RuntimeException("No parser implementation available for grammar '" + grammar + "'!");
    }

    private ValidationResult parseWith(String lipidName, Deque<ValidationResult.Grammar> grammars) {
        log.debug("Grammars left: " + grammars.size());
        ValidationResult.Grammar grammar = grammars.pop();
        Parser<LipidAdduct> parser = parserFor(grammar);
        log.info("Using grammar " + grammar + " with parser: " + parser.getClass().getSimpleName());
        LipidAdduct la = parser.parse(lipidName, false);
        if (la != null) {
            ValidationResult result = new ValidationResult();
            result.setLipidName(lipidName);
            result.setLipidAdduct(la);
            result.setGrammar(grammar);
            List<String> messages = Collections.emptyList();
            if (parser.get_error_message() != null && !parser.get_error_message().isEmpty()) {
                messages = Arrays.asList(parser.get_error_message());
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
            if (la != null) {
                result.setLipidMapsCategory(la.lipid.getHeadgroup().lipid_category.name());
                String speciesName = la.lipid.get_lipid_string(LipidLevel.SPECIES);
                Double mass = la.get_mass();
                if (mass == 0.0) {
                    mass = Double.NaN;
                }
                result.setMass(mass);
                result.setSumFormula(la.get_sum_formula());
                result.setLipidMapsClass(getLipidMapsClassAbbreviation(la));
                result.setLipidSpeciesInfo(la.lipid.getInfo());
                try {
                    String normalizedName = la.lipid.get_lipid_string();
                    result.setNormalizedName(normalizedName);
                    result.setLipidMapsReferences(dbLoader.findLipidMapsEntry(normalizedName));
                    result.setSwissLipidsReferences(dbLoader.findSwissLipidsEntry(normalizedName, lipidName));
                } catch (RuntimeException re) {
                    log.debug("Parsing error for {}!", lipidName);
                }
                result.setFattyAcids(la.lipid.getFa());
            }
            return result;
        } else {
            log.debug("Could not parse " + lipidName + " with " + parser.getClass().getName() + " for grammar " + grammar + "! Message: " + parser.get_error_message());
            if (grammars.isEmpty()) {
                ValidationResult result = new ValidationResult();
                result.setLipidName(lipidName);
                result.setGrammar(grammar);
                result.setMessages(Arrays.asList(parser.get_error_message()));
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
        String lipidMapsClass = lipidClasses.get(la.lipid.getHeadgroup().lipid_class).description;
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
