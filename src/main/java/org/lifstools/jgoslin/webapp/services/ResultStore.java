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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.lifstools.jgoslin.webapp.domain.GoslinStorageServiceSlots;
import org.lifstools.jgoslin.webapp.domain.NameEntry;
import org.lifstools.jgoslin.webapp.domain.StoredResult;
import org.lifstools.lifs.webapps.common.domain.UserSessionFile;
import org.lifstools.lifs.webapps.common.service.SessionIdGenerator;
import org.lifstools.lifs.webapps.common.service.StorageService;
import org.lifstools.lifs.webapps.common.service.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps line-numbered results as JSON on the shared storage volume, so that
 * every replica can serve them.
 */
@Service
public class ResultStore {

    private static final Logger log = LoggerFactory.getLogger(ResultStore.class);

    private static final String FILE_NAME = GoslinStorageServiceSlots.INPUT_ENTRIES.getName()
            + GoslinStorageServiceSlots.INPUT_ENTRIES.getExtension();

    private final StorageService storage;
    private final SessionIdGenerator ids;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public ResultStore(StorageService storage, SessionIdGenerator ids) {
        this.storage = storage;
        this.ids = ids;
    }

    public StoredResult create(List<NameEntry> entries) {
        StoredResult result = new StoredResult(ids.generate(), 1, entries);
        save(result);
        return result;
    }

    public void save(StoredResult result) {
        storage.store(mapper.writeValueAsString(result), result.id(), GoslinStorageServiceSlots.INPUT_ENTRIES);
    }

    public Optional<StoredResult> load(UUID id) {
        try {
            UserSessionFile file = storage.load(id, GoslinStorageServiceSlots.INPUT_ENTRIES);
            // load falls back to another slot's file when ours is missing
            if (file == null || !FILE_NAME.equals(file.getFilename())) {
                return Optional.empty();
            }
            Path path = storage.load(file, GoslinStorageServiceSlots.INPUT_ENTRIES);
            return Optional.of(mapper.readValue(Files.readString(path), StoredResult.class));
        } catch (StorageException e) {
            return Optional.empty();
        } catch (JacksonException | java.io.IOException e) {
            log.warn("Stored result {} could not be read: {}", id, e.getMessage());
            return Optional.empty();
        }
    }
}
