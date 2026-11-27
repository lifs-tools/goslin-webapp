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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.annotation.PreDestroy;
import org.lifstools.jgoslin.domain.Headgroup;
import org.lifstools.jgoslin.domain.KnownFunctionalGroups;
import org.lifstools.jgoslin.domain.LipidAdduct;
import org.lifstools.jgoslin.domain.LipidClasses;
import org.lifstools.jgoslin.domain.LipidLevel;
import org.lifstools.jgoslin.parser.BaseParserEventHandler;
import org.lifstools.jgoslin.parser.FattyAcidParser;
import org.lifstools.jgoslin.parser.GoslinParser;
import org.lifstools.jgoslin.parser.HmdbParser;
import org.lifstools.jgoslin.parser.LipidMapsParser;
import org.lifstools.jgoslin.parser.Parser;
import org.lifstools.jgoslin.parser.ShorthandParser;
import org.lifstools.jgoslin.parser.SwissLipidsParser;
import org.lifstools.jgoslin.webapp.config.ParserProperties;
import org.lifstools.jgoslin.webapp.domain.ValidationResult;
import org.lifstools.jgoslin.webapp.domain.ValidationResult.Grammar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Parses lipid names with jgoslin, under a time limit per name and per request.
 * <p>
 * Names are parsed on a bounded pool of daemon threads ({@code goslin.parser.threads}). The caller waits for each
 * name until its deadline: {@code goslin.parser.time-limit} from the moment it starts waiting for a slot (waiting
 * counts), across all grammars tried for it, and never past the request's deadline,
 * {@code goslin.parser.request-time-limit} from the start of the request. A name that is not done by its own
 * deadline fails with {@link #timeLimitMessage(Duration)}, or with {@link #busyMessage(Duration)} if it never got
 * a slot; the names left when the request's deadline passes fail with {@link #NOT_PROCESSED_MESSAGE}. Zero turns
 * a limit off.
 * <p>
 * jgoslin's parser cannot be interrupted while it parses with one grammar, so a parse that ran out of time is
 * cancelled, but keeps running until that grammar is done; it does not go on to the next grammar. Such a parse
 * still burns CPU until then, so it keeps its slots: a parse takes a slot of the pool (one per thread, handed out
 * in order) and one of its request ({@code goslin.parser.max-parallel-per-request}) before it is handed to the
 * pool, and gives both back only when it ends. One request can therefore never hold more than its share of the
 * pool, however many of its names run out of time, and the pool's queue never grows: a name that gets no slot
 * before its deadline fails without being parsed.
 * <p>
 * Thread safety: the six jgoslin parsers are shared. A parser's grammar tables are only read while parsing, and the
 * parse tables are local to each call; everything a parse writes goes into its own event handler, which is created
 * for every name and grammar ({@code Parser.newEventHandler()}), so a parse that ran out of time shares nothing
 * mutable with later parses. jgoslin's {@code LipidParser} is not used: it remembers the last successful parser in
 * a field. The only shared mutable state is jgoslin's head group tables ({@code Headgroup.StringCategory},
 * {@code StringClass}, {@code ClassString}), static maps that are filled lazily without locking; the constructor
 * fills them by parsing one name before any parse runs on the pool.
 *
 * @author nils.hoffmann
 */
@Service
public class LipidNameValidationService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LipidNameValidationService.class);

    public static final String LIPIDMAPS_CLASS_REGEXP = ".+\\[([A-Z0-9]+)\\]";

    /** The message of a name that was not parsed because its request ran out of time. */
    public static final String NOT_PROCESSED_MESSAGE = "Not processed: the time limit for this request was reached.";

    /** How a name was parsed. */
    public enum Outcome {
        /** A grammar read the name. */
        PARSED,
        /** No grammar read the name. */
        FAILED,
        /** The name was not parsed within {@code goslin.parser.time-limit}. */
        TIME_LIMIT,
        /** The name got no parse slot within {@code goslin.parser.time-limit}; it was not parsed at all. */
        BUSY,
        /** The name was not parsed: its request reached {@code goslin.parser.request-time-limit}. */
        NOT_PROCESSED
    }

    /**
     * A parsed name.
     *
     * @param result the result, as the REST API returns it
     * @param outcome how it was parsed
     * @param stop for a failed name, the length of the longest beginning of the name that a grammar read (where
     * parsing stopped); otherwise 0
     */
    public record ParsedName(ValidationResult result, Outcome outcome, int stop) {
    }

    /** One grammar's attempt at a name: the lipid, or null, and the parser's error message. */
    record Attempt(LipidAdduct adduct, String errorMessage) {
    }

    /** Parses a name with one grammar; jgoslin's parsers, or a stand-in in tests. */
    @FunctionalInterface
    interface GrammarStep {

        Attempt attempt(Grammar grammar, String name);
    }

    private final ExternalDatabaseMappingLoader dbLoader;
    private final List<ValidationResult.Grammar> defaultGrammars;
    private final KnownFunctionalGroups knownFunctionalGroups = new KnownFunctionalGroups();
    private final LipidClasses lipidClasses = LipidClasses.getInstance();
    private final Map<Grammar, Parser<LipidAdduct>> grammarToParser = new EnumMap<>(Grammar.class);
    private final GrammarStep step;
    private final Duration timeLimit;
    private final Duration requestTimeLimit;
    private final int threads;
    private final int maxParallelPerRequest;
    private final ThreadPoolExecutor pool;
    /** One per pool thread: taken before a name is handed to the pool, given back when its parse ends. */
    private final Semaphore poolSlots;

    @Autowired
    public LipidNameValidationService(ExternalDatabaseMappingLoader dbLoader, ParserProperties parserProperties) {
        this(dbLoader, parserProperties, null);
    }

    /**
     * @param step parses one name with one grammar; null for jgoslin's parsers
     */
    LipidNameValidationService(ExternalDatabaseMappingLoader dbLoader, ParserProperties parserProperties,
            GrammarStep step) {
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
        this.step = step != null ? step : this::jgoslinAttempt;
        this.timeLimit = parserProperties.timeLimit();
        this.requestTimeLimit = parserProperties.requestTimeLimit();
        this.threads = parserProperties.threads();
        this.maxParallelPerRequest = parserProperties.maxParallelPerRequest();
        this.poolSlots = new Semaphore(threads, true);
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "goslin-parse-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.pool = new ThreadPoolExecutor(threads, threads, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), factory);
        this.pool.allowCoreThreadTimeOut(true);
        fillSharedTables();
    }

    /** Fills jgoslin's lazily filled static head group tables in this thread, before parses run in parallel. */
    private static void fillSharedTables() {
        synchronized (Headgroup.class) {
            Headgroup.getCategory("PC");
            Headgroup.getClass("PC");
            Headgroup.getClassString(0);
        }
    }

    @PreDestroy
    @Override
    public void close() {
        pool.shutdownNow();
    }

    /**
     * Validates the names with all grammars, the first that reads a name wins; the REST API's results.
     *
     * @param lipidNames the names
     * @return one result per name, in order
     */
    public List<ValidationResult> validate(List<String> lipidNames) {
        return validate(lipidNames, Collections.emptyList());
    }

    /**
     * Validates the names with the given grammars, in order, the first that reads a name wins; all grammars if
     * none are given. The REST API's results.
     *
     * @param lipidNames the names
     * @param grammars the grammars
     * @return one result per name, in order
     */
    public List<ValidationResult> validate(List<String> lipidNames, List<ValidationResult.Grammar> grammars) {
        return parse(lipidNames, grammars).stream().map(ParsedName::result).toList();
    }

    /**
     * Parses the names with all grammars, the first that reads a name wins.
     *
     * @param lipidNames the names
     * @return one parsed name per name, in order
     */
    public List<ParsedName> parse(List<String> lipidNames) {
        return parse(lipidNames, Collections.emptyList());
    }

    private List<ParsedName> parse(List<String> lipidNames, List<ValidationResult.Grammar> grammars) {
        long start = System.nanoTime();
        long requestDeadline = deadline(start, requestTimeLimit);
        List<String> names = lipidNames.stream().map(n -> n.replaceAll("\\s+", " ").trim()).toList();
        boolean anyGrammar = grammars.isEmpty();
        // a grammar named twice is tried once
        List<Grammar> tried = anyGrammar ? defaultGrammars : grammars.stream().distinct().toList();
        Semaphore requestSlots = new Semaphore(maxParallelPerRequest);
        ParsedName[] parsed = new ParsedName[names.size()];
        Deque<Pending> pending = new ArrayDeque<>();
        int next = 0;
        boolean requestTimedOut = false;
        try {
            while (next < names.size() || !pending.isEmpty()) {
                // start names while this request and the pool have free slots, without waiting
                while (!requestTimedOut && next < names.size()) {
                    long now = System.nanoTime();
                    if (now - requestDeadline >= 0) {
                        requestTimedOut = true;
                        break;
                    }
                    if (!acquire(requestSlots, now)) {
                        break;
                    }
                    long nameDeadline = Math.min(deadline(now, timeLimit), requestDeadline);
                    pending.add(start(next, names.get(next), tried, anyGrammar, nameDeadline, requestSlots));
                    next++;
                }
                if (pending.isEmpty()) {
                    if (requestTimedOut || next >= names.size()) {
                        break;
                    }
                    // Every slot this request may use is taken, also by parses that ran out of time and still run:
                    // wait for one. The wait counts against the name's deadline; a name that gets no slot in time
                    // fails without being parsed.
                    long nameDeadline = Math.min(deadline(System.nanoTime(), timeLimit), requestDeadline);
                    if (acquire(requestSlots, nameDeadline)) {
                        pending.add(start(next, names.get(next), tried, anyGrammar, nameDeadline, requestSlots));
                    } else {
                        parsed[next] = noSlot(names.get(next), nameDeadline, requestDeadline);
                        requestTimedOut = parsed[next].outcome() == Outcome.NOT_PROCESSED;
                    }
                    next++;
                    continue;
                }
                Pending p = pending.poll();
                try {
                    parsed[p.index()] = await(p);
                } catch (TimeoutException e) {
                    p.abandon();
                    parsed[p.index()] = expired(names.get(p.index()), p.deadline(), requestDeadline);
                    requestTimedOut = requestTimedOut || parsed[p.index()].outcome() == Outcome.NOT_PROCESSED;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            requestTimedOut = true;
        } finally {
            pending.forEach(Pending::abandon);
        }
        if (requestTimedOut) {
            int notProcessed = 0;
            for (int i = 0; i < parsed.length; i++) {
                if (parsed[i] == null) {
                    parsed[i] = notProcessed(names.get(i));
                    notProcessed++;
                }
            }
            log.info("Parsing {} names took longer than {}: {} not processed.", names.size(), requestTimeLimit,
                    notProcessed);
        }
        return Arrays.asList(parsed);
    }

    /**
     * Takes a slot of the request and one of the pool, waiting until the deadline at most; {@link Long#MAX_VALUE}
     * waits as long as it takes, a deadline in the past does not wait. The pool's slots are handed out in the order
     * they were asked for.
     */
    private boolean acquire(Semaphore requestSlots, long deadline) throws InterruptedException {
        if (deadline == Long.MAX_VALUE) {
            requestSlots.acquire();
            try {
                poolSlots.acquire();
            } catch (InterruptedException e) {
                requestSlots.release();
                throw e;
            }
            return true;
        }
        if (!requestSlots.tryAcquire(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
            return false;
        }
        boolean acquired = false;
        try {
            acquired = poolSlots.tryAcquire(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } finally {
            if (!acquired) {
                requestSlots.release();
            }
        }
        return acquired;
    }

    /**
     * Hands a name to the pool; the caller holds a slot of the request and one of the pool. The parse gives both
     * back when it ends, also after it ran out of time; if it never started, {@link Pending#abandon()} does.
     */
    private Pending start(int index, String name, List<Grammar> grammars, boolean anyGrammar, long deadline,
            Semaphore requestSlots) {
        Slots slots = new Slots(requestSlots, poolSlots);
        try {
            Future<ParsedName> future = pool.submit(() -> {
                if (!slots.claim()) {
                    return null; // abandoned before it started
                }
                try {
                    return parseName(name, grammars, anyGrammar, deadline);
                } finally {
                    slots.release();
                }
            });
            return new Pending(index, deadline, future, slots);
        } catch (RejectedExecutionException e) {
            slots.claim();
            slots.release();
            throw e;
        }
    }

    /** A name whose deadline passed: over the time limit, or not processed if it was the request's deadline. */
    private ParsedName expired(String name, long deadline, long requestDeadline) {
        if (deadline == requestDeadline && requestTimeLimit.isPositive()) {
            return notProcessed(name);
        }
        log.info("Parsing a name of {} characters took longer than {}.", name.length(), timeLimit);
        return timedOut(name);
    }

    /**
     * A name that got no slot before its deadline: it was never parsed. Not processed if it was the request's
     * deadline, otherwise the server was busy.
     */
    private ParsedName noSlot(String name, long deadline, long requestDeadline) {
        if (deadline == requestDeadline && requestTimeLimit.isPositive()) {
            return notProcessed(name);
        }
        log.info("A name got no parse slot within {}.", timeLimit);
        return failed(name, busyMessage(timeLimit), Outcome.BUSY);
    }

    /** The slots one parse holds; given back exactly once, by the parse or, if it never ran, by the caller. */
    private static final class Slots {

        private final Semaphore requestSlots;
        private final Semaphore poolSlots;
        private final AtomicBoolean claimed = new AtomicBoolean();

        Slots(Semaphore requestSlots, Semaphore poolSlots) {
            this.requestSlots = requestSlots;
            this.poolSlots = poolSlots;
        }

        boolean claim() {
            return claimed.compareAndSet(false, true);
        }

        void release() {
            poolSlots.release();
            requestSlots.release();
        }
    }

    private record Pending(int index, long deadline, Future<ParsedName> future, Slots slots) {

        /** Stops waiting for the parse: if it has not started it never will; if it runs, it keeps its slots. */
        void abandon() {
            if (slots.claim()) {
                slots.release();
            }
            future.cancel(true);
        }
    }

    private static ParsedName await(Pending p) throws InterruptedException, TimeoutException {
        try {
            ParsedName parsed = p.deadline() == Long.MAX_VALUE
                    ? p.future().get()
                    : p.future().get(Math.max(0, p.deadline() - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (parsed == null) {
                // the parse saw its deadline pass a moment before this wait did
                throw new TimeoutException();
            }
            return parsed;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(e.getCause());
        } catch (CancellationException e) {
            throw new IllegalStateException("The parse pool was shut down.", e);
        }
    }

    /** {@code start + limit} in {@link System#nanoTime()}, {@link Long#MAX_VALUE} for no limit. */
    private static long deadline(long start, Duration limit) {
        if (limit.isZero()) {
            return Long.MAX_VALUE;
        }
        long nanos;
        try {
            nanos = limit.toNanos();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
        long deadline = start + nanos;
        return deadline - start < 0 ? Long.MAX_VALUE : deadline;
    }

    /**
     * The message of a name that took longer than the time limit.
     *
     * @param limit the time limit
     * @return "Goslin stopped parsing this name after 20 seconds. …"
     */
    public static String timeLimitMessage(Duration limit) {
        return "Goslin stopped parsing this name after " + secondsText(limit)
                + ". Names that take this long are almost never valid lipid names.";
    }

    /**
     * The message of a name that got no parse slot within the time limit, so was never parsed.
     *
     * @param limit the time limit
     * @return "Goslin could not start parsing this name within 20 seconds because the server was busy. …"
     */
    public static String busyMessage(Duration limit) {
        return "Goslin could not start parsing this name within " + secondsText(limit)
                + " because the server was busy. Please try again.";
    }

    private static String secondsText(Duration limit) {
        String seconds = seconds(limit);
        return seconds + ("1".equals(seconds) ? " second" : " seconds");
    }

    /**
     * The limit in seconds, as few digits as needed: "20", "1.5".
     *
     * @param limit a duration
     * @return the seconds
     */
    public static String seconds(Duration limit) {
        return BigDecimal.valueOf(limit.toMillis()).movePointLeft(3).stripTrailingZeros().toPlainString();
    }

    /** The time limit per name; zero for none. */
    public Duration timeLimit() {
        return timeLimit;
    }

    private ParsedName timedOut(String name) {
        return failed(name, timeLimitMessage(timeLimit), Outcome.TIME_LIMIT);
    }

    private static ParsedName notProcessed(String name) {
        return failed(name, NOT_PROCESSED_MESSAGE, Outcome.NOT_PROCESSED);
    }

    private static ParsedName failed(String name, String message, Outcome outcome) {
        ValidationResult result = new ValidationResult();
        result.setLipidName(name);
        result.setGrammar(Grammar.NONE);
        result.setMessages(List.of(message));
        return new ParsedName(result, outcome, 0);
    }

    private Parser<LipidAdduct> parserFor(ValidationResult.Grammar grammar) {
        if (grammarToParser.containsKey(grammar)) {
            return grammarToParser.get(grammar);
        }
        throw new RuntimeException("No parser implementation available for grammar '" + grammar + "'!");
    }

    private Attempt jgoslinAttempt(Grammar grammar, String name) {
        Parser<LipidAdduct> parser = parserFor(grammar);
        log.debug("Using grammar " + grammar + " with parser: " + parser.getClass().getSimpleName());
        BaseParserEventHandler<LipidAdduct> handler = parser.newEventHandler();
        LipidAdduct la = parser.parse(name, handler, false);
        return new Attempt(la, handler.getErrorMessage());
    }

    /**
     * Parses one name with the grammars in order, on a pool thread. With all grammars ({@code anyGrammar}) it does
     * what jgoslin's {@code LipidParser} does, with its messages; with chosen grammars, what this service did
     * before. Stops between grammars once the name's deadline has passed or the parse was cancelled; the
     * caller has then reported the name already, so the result is not used.
     */
    private ParsedName parseName(String name, List<Grammar> grammars, boolean anyGrammar, long deadline) {
        int stop = 0;
        Attempt last = null;
        Grammar lastGrammar = null;
        String lastPosition = null;
        for (Grammar grammar : grammars) {
            if (Thread.currentThread().isInterrupted() || (deadline != Long.MAX_VALUE && System.nanoTime() - deadline >= 0)) {
                return null;
            }
            log.debug("Grammars left: {}", grammars.size() - grammars.indexOf(grammar));
            last = step.attempt(grammar, name);
            lastGrammar = grammar;
            stop = Math.max(stop, ParseStopLocator.stopOf(name, last.errorMessage()));
            String error = last.errorMessage();
            if (error != null && !error.isEmpty()) {
                lastPosition = error;
            }
            if (last.adduct() != null && (anyGrammar || error == null || error.isEmpty())) {
                return new ParsedName(createValidationResult(last.adduct(), name, grammar, ""), Outcome.PARSED, 0);
            }
            log.debug("Could not parse " + name + " with grammar " + grammar + "! Message: " + error);
        }
        ValidationResult result = new ValidationResult();
        result.setLipidName(name);
        String error = last == null ? null : last.errorMessage();
        if (anyGrammar) {
            // jgoslin's LipidParser: the message of its LipidParsingException, with the position from the last
            // grammar that reported one
            String at = lastPosition == null ? name : lastPosition;
            String message = "Could not parse lipid '" + name + "' with any parser!" + " Parsing failed " + "at or after " + at;
            result.setGrammar(Grammar.NONE);
            result.setMessages(Arrays.asList("Could not parse " + name + " with any parser. Original message:" + message));
        } else {
            result.setGrammar(lastGrammar);
            if (error == null || error.isEmpty()) {
                result.setMessages(Arrays.asList("Could not parse " + name + " with any parser. Please check that the headgroup is supported!"));
            } else {
                result.setMessages(Arrays.asList("Not a valid name after position " + error.length() + ": " + error));
            }
        }
        return new ParsedName(result, Outcome.FAILED, stop);
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
