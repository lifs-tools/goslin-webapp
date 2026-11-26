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

/**
 * Turns submitted text into line-numbered {@link NameEntry} records.
 */
public final class NameEntries {

    private NameEntries() {
    }

    /**
     * Splits text into entries. Line numbers are 1-based positions in the
     * submitted text; blank lines count but produce no entry.
     *
     * @param text the submitted text, may be null
     * @param maxNames maximum number of non-blank lines
     * @return the entries, empty for null or blank text
     * @throws TooManyNamesException if there are more than maxNames non-blank lines
     */
    public static List<NameEntry> fromText(String text, int maxNames) {
        if (text == null) {
            return List.of();
        }
        String[] lines = text.replace("﻿", "").split("\\r\\n|\\r|\\n", -1);
        List<NameEntry> entries = new ArrayList<>();
        int count = 0;
        for (int i = 0; i < lines.length; i++) {
            String name = lines[i].replace(' ', ' ').strip().replaceAll("\\s+", " ");
            if (name.isEmpty()) {
                continue;
            }
            count++;
            if (count <= maxNames) {
                entries.add(NameEntry.of(i + 1, name));
            }
        }
        if (count > maxNames) {
            throw new TooManyNamesException(count, maxNames);
        }
        return List.copyOf(entries);
    }
}
