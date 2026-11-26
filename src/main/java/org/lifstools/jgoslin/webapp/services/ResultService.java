package org.lifstools.jgoslin.webapp.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.lifstools.jgoslin.webapp.domain.FailureExplanation;
import org.lifstools.jgoslin.webapp.domain.GoslinStorageServiceSlots;
import org.lifstools.jgoslin.webapp.domain.NameEntry;
import org.lifstools.jgoslin.webapp.domain.RerunOutcome;
import org.lifstools.jgoslin.webapp.domain.ResultView;
import org.lifstools.jgoslin.webapp.domain.StoredResult;
import org.lifstools.jgoslin.webapp.domain.ValidationResult;
import org.lifstools.lifs.webapps.common.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Parses stored, line-numbered entries into a {@link ResultView}, re-runs
 * corrections, removes lines and keeps the downloadable TSV in step.
 * <p>
 * Parse results are cached per {@code id:revision} in an LRU bounded by the
 * total number of entries across cached views. The cache is a pure
 * optimisation: the stored result is the source of truth, so a miss (or
 * another replica) simply parses again. Changes to one result are serialized
 * in this JVM by a striped lock on its id; the parse itself runs outside the
 * cache lock.
 */
@Service
public class ResultService {

    private static final int LOCK_STRIPES = 64;

    private final ResultStore store;
    private final LipidNameValidationService validation;
    private final ParseStopLocator locator;
    private final TsvWriter tsvWriter;
    private final StorageService storage;
    private final int maxCachedEntries;

    private final Object[] locks = new Object[LOCK_STRIPES];
    private final Map<String, ResultView> cache = new LinkedHashMap<>(16, 0.75f, true);
    private int cachedEntries;

    @Autowired
    public ResultService(ResultStore store, LipidNameValidationService validation, ParseStopLocator locator,
            TsvWriter tsvWriter, StorageService storage,
            @Value("${goslin.result-cache.max-entries:20000}") int maxCachedEntries) {
        this.store = store;
        this.validation = validation;
        this.locator = locator;
        this.tsvWriter = tsvWriter;
        this.storage = storage;
        this.maxCachedEntries = maxCachedEntries;
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new Object();
        }
    }

    public ResultView harmonize(List<NameEntry> entries) {
        StoredResult stored = store.create(entries);
        ResultView view = parse(stored);
        writeTsv(view);
        return view;
    }

    public Optional<ResultView> view(UUID id) {
        return store.load(id).map(this::cachedParse);
    }

    public Optional<RerunOutcome> rerun(UUID id, Map<Integer, String> edits) {
        synchronized (lockFor(id)) {
            Optional<StoredResult> loaded = store.load(id);
            if (loaded.isEmpty()) {
                return Optional.empty();
            }
            StoredResult stored = loaded.get();
            ResultView before = cachedParse(stored);
            Set<Integer> failedLines = new HashSet<>();
            before.failed().forEach(f -> failedLines.add(f.entry().line()));
            Map<Integer, String> applicable = new HashMap<>();
            edits.forEach((line, text) -> {
                if (failedLines.contains(line)) {
                    applicable.put(line, text);
                }
            });
            StoredResult updated = stored.withEdits(applicable);
            if (updated == stored) {
                return Optional.of(new RerunOutcome(0, before.failed().size()));
            }
            store.save(updated);
            ResultView after = parse(updated);
            writeTsv(after);
            Set<Integer> stillFailed = new HashSet<>();
            after.failed().forEach(f -> stillFailed.add(f.entry().line()));
            int harmonized = 0;
            for (NameEntry entry : updated.entries()) {
                if (!entry.removed() && entry.corrected() && applicable.containsKey(entry.line())
                        && !stillFailed.contains(entry.line())) {
                    harmonized++;
                }
            }
            return Optional.of(new RerunOutcome(harmonized, after.failed().size()));
        }
    }

    public Optional<ResultView> remove(UUID id, int line) {
        synchronized (lockFor(id)) {
            Optional<StoredResult> loaded = store.load(id);
            if (loaded.isEmpty()) {
                return Optional.empty();
            }
            StoredResult stored = loaded.get();
            StoredResult updated = stored.withRemoved(line);
            if (updated == stored) {
                return Optional.of(cachedParse(stored));
            }
            store.save(updated);
            ResultView view = parse(updated);
            writeTsv(view);
            return Optional.of(view);
        }
    }

    public List<ResultView.FailedRow> preview(List<NameEntry> entries) {
        return build(null, 0, entries).failed();
    }

    private Object lockFor(UUID id) {
        return locks[(id.hashCode() & Integer.MAX_VALUE) % LOCK_STRIPES];
    }

    private void writeTsv(ResultView view) {
        storage.store(tsvWriter.write(view), view.id(), GoslinStorageServiceSlots.OUTPUT_TSV_FILE);
    }

    private ResultView cachedParse(StoredResult stored) {
        String key = stored.id() + ":" + stored.revision();
        synchronized (cache) {
            ResultView hit = cache.get(key);
            if (hit != null) {
                return hit;
            }
        }
        return parse(stored);
    }

    /** Parses and caches. */
    private ResultView parse(StoredResult stored) {
        ResultView view = build(stored.id(), stored.revision(), stored.active());
        cachePut(stored.id() + ":" + stored.revision(), view);
        return view;
    }

    private ResultView build(UUID id, int revision, List<NameEntry> active) {
        List<NameEntry> entries = active.stream().filter(e -> !e.removed()).toList();
        List<ValidationResult> results = entries.isEmpty()
                ? List.of()
                : validation.validate(entries.stream().map(NameEntry::current).toList());
        List<ResultView.Row> harmonized = new ArrayList<>();
        List<ResultView.FailedRow> failed = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            NameEntry entry = entries.get(i);
            ValidationResult result = results.get(i);
            if (result.getLipidAdduct() == null) {
                String message = result.getMessages() == null ? "" : String.join(" | ", result.getMessages());
                FailureExplanation explanation = locator.explain(entry.current(), message);
                failed.add(new ResultView.FailedRow(entry, result, explanation));
            } else {
                harmonized.add(new ResultView.Row(entry, result));
            }
        }
        return new ResultView(id, revision, entries.size(), List.copyOf(harmonized), List.copyOf(failed));
    }

    private void cachePut(String key, ResultView view) {
        int size = view.submitted();
        if (size > maxCachedEntries) {
            return;
        }
        synchronized (cache) {
            ResultView old = cache.remove(key);
            if (old != null) {
                cachedEntries -= old.submitted();
            }
            var it = cache.entrySet().iterator();
            while (cachedEntries + size > maxCachedEntries && it.hasNext()) {
                cachedEntries -= it.next().getValue().submitted();
                it.remove();
            }
            cache.put(key, view);
            cachedEntries += size;
        }
    }
}
