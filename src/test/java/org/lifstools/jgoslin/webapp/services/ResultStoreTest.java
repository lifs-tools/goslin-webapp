/*
 * Copyright 2026 Leibniz Institut für Analytische Wissenschaften - ISAS e.V..
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.lifstools.jgoslin.webapp.domain.StoredResult;
import org.lifstools.lifs.webapps.common.service.DefaultSessionIdGenerator;
import org.lifstools.lifs.webapps.common.service.StorageService;
import org.lifstools.lifs.webapps.common.service.storage.FileSystemStorageService;
import org.lifstools.lifs.webapps.common.service.storage.StorageException;
import org.lifstools.lifs.webapps.common.service.storage.StorageProperties;

class ResultStoreTest {

    @TempDir
    Path dir;

    private StorageService newStorage() {
        StorageProperties props = new StorageProperties();
        props.setLocation(dir.toString());
        return new FileSystemStorageService(props);
    }

    private ResultStore store;
    private StorageService storage;

    @BeforeEach
    void setUp() {
        storage = newStorage();
        store = new ResultStore(storage, new DefaultSessionIdGenerator());
    }

    private StoredResult sample() {
        return store.create(List.of(NameEntry.of(34, "PC(18:2"), NameEntry.of(35, "PC 32:0")));
    }

    @Test
    void createThenLoadRoundTrips() {
        StoredResult created = sample();
        assertEquals(1, created.revision());
        assertEquals(Optional.of(created), store.load(created.id()));
    }

    @Test
    void unknownIdIsEmpty() {
        assertTrue(store.load(UUID.randomUUID()).isEmpty());
    }

    @Test
    void secondStoreOnSameFolderSeesResult() {
        StoredResult created = sample();
        ResultStore replica = new ResultStore(newStorage(), new DefaultSessionIdGenerator());
        assertEquals(Optional.of(created), replica.load(created.id()));
    }

    @Test
    void saveReplacesPreviousRevision() {
        StoredResult created = sample();
        store.save(created.withEdits(Map.of(34, "PC(18:2)")));
        StoredResult loaded = store.load(created.id()).orElseThrow();
        assertEquals(2, loaded.revision());
        NameEntry e = loaded.entries().get(0);
        assertEquals("PC(18:2)", e.current());
        assertTrue(e.corrected());
    }

    @Test
    void withRemovedHidesFromActive() {
        StoredResult r = sample().withRemoved(34);
        assertEquals(List.of(35), r.active().stream().map(NameEntry::line).toList());
    }

    @Test
    void otherSlotsFileIsNotParsed() {
        UUID id = UUID.randomUUID();
        storage.store("Line\tName\n", id, GoslinStorageServiceSlots.OUTPUT_TSV_FILE);
        assertTrue(store.load(id).isEmpty());
    }

    @Test
    void corruptJsonIsEmpty() throws Exception {
        UUID id = UUID.randomUUID();
        storage.store("{not json", id, GoslinStorageServiceSlots.INPUT_ENTRIES);
        assertTrue(store.load(id).isEmpty());
    }

    @Test
    void blankOrUnchangedEditsReturnSameInstance() {
        StoredResult r = sample();
        assertSame(r, r.withEdits(Map.of(34, "  ", 35, "PC  32:0")));
        assertSame(r, r.withEdits(Map.of(99, "X")));
    }

    @Test
    void editsSkipRemovedEntries() {
        StoredResult r = sample().withRemoved(34);
        assertSame(r, r.withEdits(Map.of(34, "PC(18:2)")));
    }

    @Test
    void removingUnknownOrRemovedLineReturnsSameInstance() {
        StoredResult r = sample();
        assertSame(r, r.withRemoved(99));
        StoredResult removed = r.withRemoved(34);
        assertSame(removed, removed.withRemoved(34));
    }

    @Test
    void plainStorageFailurePropagates() {
        StorageService failing = org.mockito.Mockito.mock(StorageService.class);
        org.mockito.Mockito.when(failing.load(org.mockito.ArgumentMatchers.any(UUID.class),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new StorageException("Failed to read stored files"));
        ResultStore broken = new ResultStore(failing, new DefaultSessionIdGenerator());
        assertThrows(StorageException.class, () -> broken.load(UUID.randomUUID()));
    }
}
