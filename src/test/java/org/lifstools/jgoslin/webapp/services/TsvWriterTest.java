package org.lifstools.jgoslin.webapp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lifstools.jgoslin.webapp.domain.NameEntry;
import org.lifstools.jgoslin.webapp.domain.ResultView;
import org.lifstools.lifs.webapps.common.service.DefaultSessionIdGenerator;
import org.lifstools.lifs.webapps.common.service.storage.FileSystemStorageService;
import org.lifstools.lifs.webapps.common.service.storage.StorageProperties;

class TsvWriterTest {

    @TempDir
    Path dir;

    private ResultService service;
    private TsvWriter writer;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        props.setLocation(dir.toString());
        var storage = new FileSystemStorageService(props);
        var validation = new LipidNameValidationService(mock(ExternalDatabaseMappingLoader.class),
                org.lifstools.jgoslin.webapp.config.ParserProperties.defaults());
        writer = new TsvWriter(validation);
        service = new ResultService(new ResultStore(storage, new DefaultSessionIdGenerator()), validation,
                writer, storage, 20000);
    }

    private ResultView view() {
        return service.harmonize(List.of(NameEntry.of(7, "Cer(d18:1/18:0)"), NameEntry.of(34, "PC(18:2-20:4"), NameEntry.of(35, "PC 32:0")));
    }

    private static String[] row(String tsv, int line) {
        return tsv.lines().filter(l -> l.startsWith(line + "\t")).findFirst().orElseThrow().split("\t", -1);
    }

    @Test
    void headerStartsWithLineAndKeepsOldColumns() {
        String header = writer.write(view()).lines().findFirst().orElseThrow();
        assertTrue(header.startsWith("Line\tNormalized Name\tOriginal Name\tCorrected Name\tGrammar\tValidation Messages"));
    }

    @Test
    void correctedRowKeepsOriginalName() {
        ResultView v = view();
        service.rerun(v.id(), Map.of(34, "PC(18:2-20:4)"));
        String[] r = row(writer.write(service.view(v.id()).orElseThrow()), 34);
        assertEquals("PC(18:2-20:4", r[2]);
        assertEquals("PC(18:2-20:4)", r[3]);
        assertFalse(r[1].isEmpty());
    }

    @Test
    void failedRowsAreIncludedWithMessage() {
        String[] r = row(writer.write(view()), 34);
        assertEquals("", r[1]);
        assertEquals("", r[3]);
        assertFalse(r[5].isEmpty());
    }
}
