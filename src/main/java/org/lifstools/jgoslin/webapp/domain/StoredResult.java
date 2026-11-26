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
package org.lifstools.jgoslin.webapp.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The line-numbered names of one submission, with an edit revision.
 */
public record StoredResult(UUID id, int revision, List<NameEntry> entries) {

    public StoredResult {
        entries = List.copyOf(entries);
    }

    /**
     * Applies corrections by line number. Blank edits and edits equal to the
     * current text are ignored.
     *
     * @param edits new text by line number
     * @return a result with revision + 1 if anything changed, otherwise this
     */
    public StoredResult withEdits(Map<Integer, String> edits) {
        boolean changed = false;
        List<NameEntry> updated = new ArrayList<>(entries.size());
        for (NameEntry entry : entries) {
            String edit = edits.get(entry.line());
            if (edit != null && !entry.removed()) {
                String normalized = NameEntries.normalize(edit);
                if (!normalized.isEmpty() && !normalized.equals(entry.current())) {
                    entry = entry.withCorrection(normalized);
                    changed = true;
                }
            }
            updated.add(entry);
        }
        return changed ? new StoredResult(id, revision + 1, updated) : this;
    }

    /**
     * Marks a line as removed.
     *
     * @param line the line number
     * @return a result with revision + 1, or this if no active entry has that line
     */
    public StoredResult withRemoved(int line) {
        if (entries.stream().noneMatch(e -> e.line() == line && !e.removed())) {
            return this;
        }
        List<NameEntry> updated = entries.stream()
                .map(e -> e.line() == line ? e.asRemoved() : e)
                .toList();
        return new StoredResult(id, revision + 1, updated);
    }

    /**
     * @return the entries that are not removed, in line order
     */
    public List<NameEntry> active() {
        return entries.stream().filter(e -> !e.removed()).toList();
    }
}
