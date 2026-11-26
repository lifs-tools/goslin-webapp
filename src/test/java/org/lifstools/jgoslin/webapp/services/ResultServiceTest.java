package org.lifstools.jgoslin.webapp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lifstools.jgoslin.webapp.domain.GoslinStorageServiceSlots;
import org.lifstools.jgoslin.webapp.domain.NameEntry;
import org.lifstools.jgoslin.webapp.domain.RerunOutcome;
import org.lifstools.jgoslin.webapp.domain.ResultView;
import org.lifstools.lifs.webapps.common.service.AnalyticsTracker;
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
        validation = spy(new LipidNameValidationService(mock(AnalyticsTracker.class), loader));
        service = newService(20000);
    }

    private ResultService newService(int maxEntries) {
        return new ResultService(store, validation, new ParseStopLocator(), new TsvWriter(validation), storage, maxEntries);
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
        verify(validation, times(2)).validate(any());
        service.view(second.id());
        verify(validation, times(2)).validate(any());
        service.view(first.id());
        verify(validation, times(3)).validate(any());
    }
}
