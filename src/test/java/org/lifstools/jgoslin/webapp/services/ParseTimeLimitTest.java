package org.lifstools.jgoslin.webapp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lifstools.jgoslin.webapp.config.ParserProperties;
import org.lifstools.jgoslin.webapp.domain.ValidationRequest;
import org.lifstools.jgoslin.webapp.domain.ValidationResult;
import org.lifstools.jgoslin.webapp.domain.ValidationResult.Grammar;
import org.lifstools.jgoslin.webapp.domain.ValidationResults;
import org.lifstools.jgoslin.webapp.rest.LipidNameValidation;
import org.lifstools.jgoslin.webapp.services.LipidNameValidationService.Attempt;
import org.lifstools.jgoslin.webapp.services.LipidNameValidationService.GrammarStep;
import org.lifstools.jgoslin.webapp.services.LipidNameValidationService.Outcome;
import org.lifstools.jgoslin.webapp.services.LipidNameValidationService.ParsedName;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The parse time limits, with a stand-in for jgoslin that sleeps or blocks: no test waits anywhere near the
 * real 20 seconds.
 */
class ParseTimeLimitTest {

    private static final String SLOW = "slow";

    /** Released after each test, so that blocked stand-in parses end. */
    private final CountDownLatch release = new CountDownLatch(1);
    private final List<LipidNameValidationService> services = new ArrayList<>();

    @AfterEach
    void tearDown() {
        release.countDown();
        services.forEach(LipidNameValidationService::close);
    }

    private LipidNameValidationService service(Duration perName, Duration perRequest, int threads, GrammarStep step) {
        LipidNameValidationService s = new LipidNameValidationService(mock(ExternalDatabaseMappingLoader.class),
                new ParserProperties(perName, perRequest, threads), step);
        services.add(s);
        return s;
    }

    /** "slow" blocks until the test ends (or the parse is cancelled); any other name fails at once. */
    private GrammarStep blockingOnSlow() {
        return (grammar, name) -> {
            if (SLOW.equals(name)) {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new Attempt(null, "");
        };
    }

    private static Attempt sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new Attempt(null, "");
    }

    @Test
    void nameOverTimeLimitIsReportedAsFailed() {
        var s = service(Duration.ofMillis(200), Duration.ZERO, 2, blockingOnSlow());
        long started = System.nanoTime();
        List<ParsedName> parsed = s.parse(List.of("a", SLOW, "b"));
        assertTrue(System.nanoTime() - started < Duration.ofSeconds(3).toNanos(), "returned soon after the limit");

        assertEquals(Outcome.FAILED, parsed.get(0).outcome());
        assertEquals(Outcome.TIME_LIMIT, parsed.get(1).outcome());
        assertEquals(Outcome.FAILED, parsed.get(2).outcome());
        ValidationResult slow = parsed.get(1).result();
        assertNull(slow.getLipidAdduct());
        assertEquals(SLOW, slow.getLipidName());
        assertEquals(Grammar.NONE, slow.getGrammar());
        assertEquals(List.of("Goslin stopped parsing this name after 0.2 seconds. "
                + "Names that take this long are almost never valid lipid names."), slow.getMessages());
    }

    @Test
    void timeLimitMessageSaysSecondsAsConfigured() {
        assertEquals("Goslin stopped parsing this name after 20 seconds. Names that take this long are almost never "
                + "valid lipid names.", LipidNameValidationService.timeLimitMessage(Duration.ofSeconds(20)));
        assertEquals("Goslin stopped parsing this name after 1 second. Names that take this long are almost never "
                + "valid lipid names.", LipidNameValidationService.timeLimitMessage(Duration.ofSeconds(1)));
    }

    @Test
    void deadlineCoversAllGrammarsOfOneName() throws Exception {
        // Each grammar takes 200 ms, well under the 500 ms limit; the six grammars together take 1.2 s.
        AtomicInteger attempts = new AtomicInteger();
        var s = service(Duration.ofMillis(500), Duration.ZERO, 2, (grammar, name) -> {
            attempts.incrementAndGet();
            return sleep(200);
        });
        long started = System.nanoTime();
        ParsedName parsed = s.parse(List.of("x")).get(0);
        long elapsed = System.nanoTime() - started;

        assertEquals(Outcome.TIME_LIMIT, parsed.outcome());
        assertTrue(elapsed < Duration.ofMillis(1100).toNanos(), "one deadline for the name, not one per grammar");
        // The cancelled parse does not go on to the next grammar.
        Thread.sleep(500);
        assertTrue(attempts.get() <= 3, "grammars tried after the deadline: " + attempts.get());
    }

    @Test
    void requestTimeLimitMarksRemainingNamesNotProcessed() {
        // Each name takes 100 ms; the request may take 300 ms.
        var s = service(Duration.ofSeconds(5), Duration.ofMillis(300), 2, (grammar, name) ->
                grammar == Grammar.SHORTHAND ? sleep(100) : new Attempt(null, ""));
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            names.add("n" + i);
        }
        long started = System.nanoTime();
        List<ParsedName> parsed = s.parse(names);
        assertTrue(System.nanoTime() - started < Duration.ofSeconds(2).toNanos());

        assertEquals(20, parsed.size());
        assertEquals(Outcome.FAILED, parsed.get(0).outcome());
        ParsedName last = parsed.get(19);
        assertEquals(Outcome.NOT_PROCESSED, last.outcome());
        assertEquals("n19", last.result().getLipidName());
        assertEquals(List.of("Not processed: the time limit for this request was reached."), last.result().getMessages());
        // names are processed in order: once one is not processed, none after it is
        int firstNotProcessed = parsed.indexOf(parsed.stream().filter(p -> p.outcome() == Outcome.NOT_PROCESSED)
                .findFirst().orElseThrow());
        assertTrue(firstNotProcessed > 0);
        for (int i = firstNotProcessed; i < 20; i++) {
            assertEquals(Outcome.NOT_PROCESSED, parsed.get(i).outcome(), "name " + i);
        }
    }

    @Test
    void zeroDisablesTheLimit() {
        Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
        var s = service(Duration.ZERO, Duration.ZERO, 2, (grammar, name) -> {
            attempts.computeIfAbsent(name, n -> new AtomicInteger()).incrementAndGet();
            return sleep(60);
        });
        List<ParsedName> parsed = s.parse(List.of("a", "b", "c"));
        parsed.forEach(p -> assertEquals(Outcome.FAILED, p.outcome()));
        // all six grammars, about 360 ms per name
        attempts.values().forEach(n -> assertEquals(6, n.get()));
    }

    @Test
    void restReportsTimedOutNameAsFailed() {
        var s = service(Duration.ofMillis(200), Duration.ZERO, 2, blockingOnSlow());
        var rest = new LipidNameValidation(s, mock(UsageCounter.class));
        ValidationRequest request = new ValidationRequest();
        request.setLipidNames(List.of("a", SLOW));
        ResponseEntity<ValidationResults> response = rest.validate(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ValidationResults body = response.getBody();
        assertEquals(2L, body.getTotalReceived());
        assertEquals(2L, body.getTotalParsed());
        assertEquals(2L, body.getFailedToParse());
        ValidationResult slow = body.getResults().get(1);
        assertEquals(SLOW, slow.getLipidName());
        assertEquals(List.of(LipidNameValidationService.timeLimitMessage(Duration.ofMillis(200))), slow.getMessages());
    }

    @Test
    void restWithGrammarsAlsoHasTheLimit() {
        var s = service(Duration.ofMillis(200), Duration.ZERO, 2, blockingOnSlow());
        List<ValidationResult> results = s.validate(List.of(SLOW), List.of(Grammar.GOSLIN, Grammar.LIPIDMAPS));
        assertEquals(List.of(LipidNameValidationService.timeLimitMessage(Duration.ofMillis(200))),
                results.get(0).getMessages());
    }
}
