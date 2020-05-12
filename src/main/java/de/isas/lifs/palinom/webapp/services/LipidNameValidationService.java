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
import de.isas.lifs.webapps.common.service.AnalyticsTracker;
import de.isas.lipidomics.domain.LipidAdduct;
import de.isas.lipidomics.domain.LipidClass;
import de.isas.lipidomics.domain.LipidLevel;
import de.isas.lipidomics.domain.LipidSpeciesInfo;
import de.isas.lipidomics.palinom.lipidmaps.LipidMapsVisitorParser;
import de.isas.lipidomics.palinom.goslin.GoslinVisitorParser;
import de.isas.lipidomics.palinom.SyntaxErrorListener;
import de.isas.lipidomics.palinom.VisitorParser;
import de.isas.lipidomics.palinom.exceptions.ParsingException;
import de.isas.lipidomics.palinom.goslinfragments.GoslinFragmentsVisitorParser;
import de.isas.lipidomics.palinom.hmdb.HmdbVisitorParser;
import de.isas.lipidomics.palinom.swisslipids.SwissLipidsVisitorParser;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
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

    @Autowired
    public LipidNameValidationService(AnalyticsTracker tracker, ExternalDatabaseMappingLoader dbLoader) {
        this.tracker = tracker;
        this.dbLoader = dbLoader;
        this.defaultGrammars = Collections.unmodifiableList(Arrays.asList(ValidationResult.Grammar.values()));
    }

    public List<ValidationResult> validate(List<String> lipidNames) {
        tracker.count(UUID.randomUUID(), getClass().getSimpleName(), "validate");
        return lipidNames.stream().map((lipidName) -> {
            return parseWith(lipidName, new ArrayDeque<ValidationResult.Grammar>(defaultGrammars));
        }).collect(Collectors.toList());
    }

    public List<ValidationResult> validate(List<String> lipidNames, List<ValidationResult.Grammar> grammars) {
        tracker.count(UUID.randomUUID(), getClass().getSimpleName(), "validate");
        return lipidNames.stream().map((lipidName) -> {
            return parseWith(lipidName, new ArrayDeque<ValidationResult.Grammar>(grammars));
        }).collect(Collectors.toList());
    }

    private VisitorParser<LipidAdduct> parserFor(ValidationResult.Grammar grammar) {
        switch (grammar) {
            case GOSLIN:
                return new GoslinVisitorParser();
            case GOSLIN_FRAGMENTS:
                return new GoslinFragmentsVisitorParser();
            case LIPIDMAPS:
                return new LipidMapsVisitorParser();
            case SWISSLIPIDS:
                return new SwissLipidsVisitorParser();
            case HMDB:
                return new HmdbVisitorParser();
            default:
                throw new RuntimeException("No parser implementation available for grammar '" + grammar + "'!");
        }
    }

    private ValidationResult parseWith(String lipidName, Deque<ValidationResult.Grammar> grammars) {
        log.debug("Grammars left: " + grammars.size());
        ValidationResult.Grammar grammar = grammars.pop();
        VisitorParser<LipidAdduct> parser = parserFor(grammar);
        log.debug("Using grammar " + grammar + " with parser: " + parser.getClass().getSimpleName());
        SyntaxErrorListener listener = new SyntaxErrorListener();
        try {
            LipidAdduct la = parser.parse(lipidName, listener);
            ValidationResult result = new ValidationResult();
            result.setLipidName(lipidName);
            result.setLipidAdduct(la);
            result.setGrammar(grammar);
            result.setMessages(toStringMessages(listener));
            result.setLipidMapsCategory(la.getLipid().getLipidCategory().name());
            String speciesName = la.getLipid().getLipidString(LipidLevel.SPECIES);
            Double mass = la.getMass();
            if(mass==0.0) {
                mass = Double.NaN;
            }
            result.setMass(mass);
            result.setSumFormula(la.getSumFormula());
            result.setLipidMapsClass(getLipidMapsClassAbbreviation(la));
            result.setLipidSpeciesInfo(la.getLipid().getInfo().orElse(LipidSpeciesInfo.NONE));
            try {
                String normalizedName = la.getLipid().getNormalizedLipidString();
                result.setGoslinName(normalizedName);
                result.setLipidMapsReferences(dbLoader.findLipidMapsEntry(normalizedName));
                result.setSwissLipidsReferences(dbLoader.findSwissLipidsEntry(normalizedName, lipidName));
            } catch (RuntimeException re) {
                log.debug("Parsing error for {}!", lipidName);
            }
            result.setFattyAcids(la.getLipid().getFa());
            return result;
        } catch (ParsingException ex) {
            log.debug("Caught exception while parsing " + lipidName + " with " + parser.getClass().getName() + " for grammar " + grammar + ": ", ex);
            if (grammars.isEmpty()) {
                ValidationResult result = new ValidationResult();
                result.setLipidName(lipidName);
                result.setGrammar(grammar);
                result.setMessages(toStringMessages(listener));
                return result;
            } else {
                return parseWith(lipidName, grammars);
            }
        }
    }

    private List<String> toStringMessages(SyntaxErrorListener listener) {
        return listener.getSyntaxErrors().stream().map((syntaxError) -> {
            return syntaxError.getMessage();
        }).collect(Collectors.toList());
    }

    private String getLipidMapsClassAbbreviation(LipidAdduct la) {
        String lipidMapsClass = la.getLipid().getLipidClass().orElse(LipidClass.UNDEFINED).getLipidMapsClassName();
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
