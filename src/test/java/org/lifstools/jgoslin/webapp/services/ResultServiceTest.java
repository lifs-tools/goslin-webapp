package org.lifstools.jgoslin.webapp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lifstools.jgoslin.webapp.config.ParserProperties;
import org.lifstools.jgoslin.webapp.domain.GoslinStorageServiceSlots;
import org.lifstools.jgoslin.webapp.domain.NameEntry;
import org.lifstools.jgoslin.webapp.domain.RerunOutcome;
import org.lifstools.jgoslin.webapp.domain.ResultView;
import org.lifstools.jgoslin.webapp.domain.StoredResult;
import org.lifstools.lifs.webapps.common.service.DefaultSessionIdGenerator;
import org.lifstools.lifs.webapps.common.service.StorageService;
import org.lifstools.lifs.webapps.common.service.storage.FileSystemStorageService;
import org.lifstools.lifs.webapps.common.service.storage.StorageProperties;

class ResultServiceTest {

    @TempDir
    Path dir;

    private StorageService storage;
    private ResultStore store;
    private LipidNameValidationService validation;
    private ResultService service;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        props.setLocation(dir.toString());
        storage = new FileSystemStorageService(props);
        store = new ResultStore(storage, new DefaultSessionIdGenerator());
        ExternalDatabaseMappingLoader loader = mock(ExternalDatabaseMappingLoader.class);
        validation = spy(new LipidNameValidationService(loader, ParserProperties.defaults()));
        service = newService(20000);
    }

    @AfterEach
    void tearDown() {
        validation.close();
    }

    private ResultService newService(int maxEntries) {
        return new ResultService(store, validation, new TsvWriter(validation), storage, maxEntries);
    }

    private static List<NameEntry> sample() {
        return List.of(NameEntry.of(7, "Cer(d18:1/18:0)"), NameEntry.of(34, "PC(18:2-20:4"), NameEntry.of(35, "PC 32:0"));
    }

    private static List<Integer> lines(ResultView v) {
        return v.harmonized().stream().map(r -> r.entry().line()).toList();
    }

    private String tsv(UUID id) throws Exception {
        var usf = storage.load(id, GoslinStorageServiceSlots.OUTPUT_TSV_FILE);
        return Files.readString(storage.load(usf, GoslinStorageServiceSlots.OUTPUT_TSV_FILE));
    }

    @Test
    void splitsHarmonizedAndFailedInLineOrder() {
        ResultView v = service.harmonize(sample());
        assertEquals(List.of(7, 35), lines(v));
        assertEquals(List.of(34), v.failed().stream().map(f -> f.entry().line()).toList());
        assertEquals(3, v.submitted());
        assertTrue(service.view(v.id()).isPresent());
    }

    @Test
    void rerunMovesCorrectedNameIntoTable() {
        ResultView v = service.harmonize(sample());
        Optional<RerunOutcome> outcome = service.rerun(v.id(), Map.of(34, "PC(18:2-20:4)"));
        assertEquals(Optional.of(new RerunOutcome(1, 0)), outcome);
        ResultView after = service.view(v.id()).orElseThrow();
        assertEquals(List.of(7, 34, 35), lines(after));
        NameEntry e = after.harmonized().get(1).entry();
        assertTrue(e.corrected());
        assertEquals("PC(18:2-20:4", e.original());
        assertEquals(2, after.revision());
    }

    @Test
    void rerunIgnoresLinesThatDidNotFail() {
        ResultView v = service.harmonize(sample());
        service.rerun(v.id(), Map.of(35, "XYZ"));
        ResultView after = service.view(v.id()).orElseThrow();
        assertEquals(1, after.revision());
        assertFalse(after.harmonized().get(1).entry().corrected());
        assertEquals("PC 32:0", after.harmonized().get(1).entry().current());
    }

    @Test
    void blankOrUnchangedEditIsIgnored() {
        ResultView v = service.harmonize(sample());
        service.rerun(v.id(), Map.of(34, "  "));
        service.rerun(v.id(), Map.of(34, "PC(18:2-20:4"));
        ResultView after = service.view(v.id()).orElseThrow();
        assertEquals(1, after.revision());
        assertFalse(after.failed().get(0).entry().corrected());
    }

    @Test
    void duplicateNamesAreSeparateEntries() {
        ResultView v = service.harmonize(List.of(NameEntry.of(1, "PC(18:2-20:4"), NameEntry.of(2, "PC(18:2-20:4")));
        service.rerun(v.id(), Map.of(1, "PC(18:2-20:4)"));
        ResultView after = service.view(v.id()).orElseThrow();
        assertEquals(List.of(1), lines(after));
        assertEquals(List.of(2), after.failed().stream().map(f -> f.entry().line()).toList());
    }

    @Test
    void removeDropsLineFromViewAndTsv() throws Exception {
        ResultView v = service.harmonize(sample());
        assertTrue(tsv(v.id()).lines().anyMatch(l -> l.startsWith("34\t")));
        ResultView after = service.remove(v.id(), 34).orElseThrow();
        assertTrue(after.failed().isEmpty());
        assertEquals(2, after.submitted());
        assertTrue(tsv(v.id()).lines().noneMatch(l -> l.startsWith("34\t")));
    }

    @Test
    void unknownIdGivesEmpty() {
        UUID id = UUID.randomUUID();
        assertTrue(service.view(id).isEmpty());
        assertTrue(service.rerun(id, Map.of(1, "x")).isEmpty());
        assertTrue(service.remove(id, 1).isEmpty());
    }

    @Test
    void cacheStaysWithinEntryBound() {
        service = newService(3);
        ResultView first = service.harmonize(List.of(NameEntry.of(1, "PC 32:0"), NameEntry.of(2, "PC 34:1")));
        ResultView second = service.harmonize(List.of(NameEntry.of(1, "PC 32:0"), NameEntry.of(2, "PC 34:1")));
        verify(validation, times(2)).parse(any());
        service.view(second.id());
        verify(validation, times(2)).parse(any());
        service.view(first.id());
        verify(validation, times(3)).parse(any());
    }

    @Test
    void emptyResultsCountTowardsTheCacheBound() {
        service = newService(3);
        List<ResultView> empty = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ResultView v = service.harmonize(List.of(NameEntry.of(1, "PC 32:0")));
            empty.add(service.remove(v.id(), 1).orElseThrow());
        }
        // Six views were cached, three of them empty. Each weighs at least 1, so the bound of 3 keeps the last
        // three; were empty views weightless, all of them would still be cached.
        assertSame(empty.get(2), service.view(empty.get(2).id()).orElseThrow());
        assertSame(empty.get(1), service.view(empty.get(1).id()).orElseThrow());
        assertNotSame(empty.get(0), service.view(empty.get(0).id()).orElseThrow());
    }

    @Test
    void editThatStillFailsIsSavedAndStillFails() {
        ResultView v = service.harmonize(sample());
        Optional<RerunOutcome> outcome = service.rerun(v.id(), Map.of(34, "PC(18:2-20:4]"));
        assertEquals(Optional.of(new RerunOutcome(0, 1)), outcome);
        ResultView after = service.view(v.id()).orElseThrow();
        assertEquals(2, after.revision());
        NameEntry e = after.failed().get(0).entry();
        assertTrue(e.corrected());
        assertEquals("PC(18:2-20:4]", e.current());
        assertEquals("PC(18:2-20:4", e.original());
    }

    @Test
    void replicaThatLostTheRaceShowsTheStoredResult() throws Exception {
        // Two replicas share the store. Both re-run revision 1 of the same result at the same time: B loads
        // revision 1 before A's save arrives, so both save a revision 2; B's save is last and wins. A writes its
        // TSV last.
        ResultView v = service.harmonize(List.of(NameEntry.of(1, "PC(18:2-20:4"), NameEntry.of(2, "PC 32:0")));
        StoredResult revision1 = store.load(v.id()).orElseThrow();
        ResultService replicaA = service;
        ResultStore staleOnce = new ResultStore(storage, new DefaultSessionIdGenerator()) {
            private boolean first = true;

            @Override
            public Optional<StoredResult> load(UUID id) {
                if (first) {
                    first = false;
                    return Optional.of(revision1);
                }
                return super.load(id);
            }
        };
        ResultService replicaB = new ResultService(staleOnce, validation, new TsvWriter(validation), storage, 20000);

        replicaA.rerun(v.id(), Map.of(1, "PC(18:2/20:4)"));
        ResultView viewOfA = replicaA.view(v.id()).orElseThrow();
        replicaB.rerun(v.id(), Map.of(1, "PC(18:2_20:4)"));
        storage.store(new TsvWriter(validation).write(viewOfA), v.id(), GoslinStorageServiceSlots.OUTPUT_TSV_FILE);

        StoredResult stored = store.load(v.id()).orElseThrow();
        assertEquals(2, stored.revision());
        assertEquals("PC(18:2_20:4)", stored.entries().get(0).current());
        assertTrue(tsv(v.id()).contains("PC(18:2/20:4)"));

        // A still has its own revision 2 cached; its next view shows what is stored, and the TSV follows
        ResultView seenByA = replicaA.view(v.id()).orElseThrow();
        assertEquals("PC(18:2_20:4)", seenByA.harmonized().get(0).entry().current());
        assertTrue(tsv(v.id()).contains("PC(18:2_20:4)"));
        assertFalse(tsv(v.id()).contains("PC(18:2/20:4)"));
        // and B, which won, shows the same
        assertEquals("PC(18:2_20:4)", replicaB.view(v.id()).orElseThrow().harmonized().get(0).entry().current());
    }
}
