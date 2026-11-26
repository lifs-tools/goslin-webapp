package org.lifstools.jgoslin.webapp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lifstools.jgoslin.domain.Headgroup;
import org.lifstools.jgoslin.domain.LipidAdduct;
import org.lifstools.jgoslin.domain.LipidParsingException;
import org.lifstools.jgoslin.parser.LipidParser;
import org.lifstools.jgoslin.webapp.config.ParserProperties;
import org.lifstools.jgoslin.webapp.domain.FailureExplanation.Hint;
import org.lifstools.jgoslin.webapp.domain.ValidationResult;
import org.lifstools.jgoslin.webapp.domain.ValidationResult.Grammar;
import org.lifstools.jgoslin.webapp.services.LipidNameValidationService.Outcome;
import org.lifstools.jgoslin.webapp.services.LipidNameValidationService.ParsedName;
import org.springframework.core.io.ClassPathResource;

/**
 * Names are parsed on a pool, several at a time, with one set of jgoslin parsers: the results must be those of
 * jgoslin's own {@link LipidParser}, one name after the other.
 */
class ParserConcurrencyTest {

    private final LipidNameValidationService service = new LipidNameValidationService(
            mock(ExternalDatabaseMappingLoader.class), new ParserProperties(Duration.ofSeconds(20), Duration.ZERO, 8));

    @AfterEach
    void tearDown() {
        service.close();
    }

    private static List<String> names() throws IOException {
        List<String> names = new ArrayList<>(new ClassPathResource("lipidnames.txt")
                .getContentAsString(StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).toList());
        names.addAll(List.of("PC(18:2-20:4", "Cholesterl", "PC 34:1x", "TG 16:0_18:1_18:2x", "SM d18:1/24:0)", "xyz"));
        return names;
    }

    @Test
    void parsingOnThePoolGivesTheResultsOfLipidParser() throws IOException {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            names.addAll(names());
        }
        List<ParsedName> parsed = service.parse(names);
        LipidParser reference = new LipidParser();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            ValidationResult result = parsed.get(i).result();
            try {
                LipidAdduct expected = reference.parse(name);
                assertEquals(Outcome.PARSED, parsed.get(i).outcome(), name);
                assertEquals(Grammar.forName(reference.getLastSuccessfulGrammar()), result.getGrammar(), name);
                assertEquals(expected.getLipid().getLipidString(), result.getNormalizedName(), name);
                assertEquals(List.of(), result.getMessages(), name);
            } catch (LipidParsingException e) {
                assertEquals(Outcome.FAILED, parsed.get(i).outcome(), name);
                assertEquals(Grammar.NONE, result.getGrammar(), name);
                // the REST message text is unchanged
                assertEquals(List.of("Could not parse " + name + " with any parser. Original message:" + e.getMessage()),
                        result.getMessages(), name);
            }
        }
    }

    @Test
    void failedNameKnowsWhereParsingStopped() {
        assertEquals(7, stop("PC 34:1x"));
        assertEquals(17, stop("TG 16:0_18:1_18:2x"));
        assertEquals(13, stop("SM d18:1/24:0)"));
        assertEquals(2, stop("PC(18:2-20:4"));
    }

    @Test
    void failedNamesAreClassifiedFromWhereParsingStopped() {
        assertEquals(Hint.MISSING_CLOSING_BRACKET, ParseStopLocator.classify("PC(18:2-20:4", stop("PC(18:2-20:4"), "m").hint());
        assertEquals(Hint.HEAD_GROUP, ParseStopLocator.classify("Cholesterl", stop("Cholesterl"), "m").hint());
    }

    private int stop(String name) {
        return service.parse(List.of(name)).get(0).stop();
    }

    @Test
    void sharedHeadGroupTablesAreFilledBeforeParsingInParallel() {
        // jgoslin fills these static maps lazily and without locking; the service fills them when it starts.
        assertFalse(Headgroup.StringCategory.isEmpty());
        assertFalse(Headgroup.StringClass.isEmpty());
        assertFalse(Headgroup.ClassString.isEmpty());
    }
}
