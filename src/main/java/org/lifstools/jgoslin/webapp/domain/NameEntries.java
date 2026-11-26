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

    /**
     * Longest name accepted, after normalising; longer lines are never parsed or stored. The longest real name in
     * Goslin's test data has 257 characters, and parse time grows steeply with length for some inputs.
     */
    public static final int MAX_NAME_LENGTH = 300;

    private NameEntries() {
    }

    /**
     * Trims, turns non-breaking spaces into spaces and collapses whitespace
     * runs to one space.
     *
     * @param line the raw line
     * @return the normalised line
     */
    public static String normalize(String line) {
        return line.replace('\u00a0', ' ').strip().replaceAll("\\s+", " ");
    }

    /**
     * Splits text into entries. Line numbers are 1-based positions in the
     * submitted text; blank lines count but produce no entry. Once there are
     * more than maxNames names, the remaining lines are only counted.
     *
     * @param text the submitted text, may be null
     * @param maxNames maximum number of non-blank lines
     * @return the entries, empty for null or blank text
     * @throws TooManyNamesException if there are more than maxNames non-blank lines
     * @throws LineTooLongException if one of the first maxNames names is longer
     * than {@link #MAX_NAME_LENGTH} characters after normalising
     */
    public static List<NameEntry> fromText(String text, int maxNames) {
        if (text == null) {
            return List.of();
        }
        String[] lines = split(text);
        List<NameEntry> entries = new ArrayList<>();
        int count = 0;
        for (int i = 0; i < lines.length; i++) {
            if (count >= maxNames) {
                if (!isBlank(lines[i])) {
                    count++;
                }
                continue;
            }
            String name = normalize(lines[i]);
            if (name.isEmpty()) {
                continue;
            }
            count++;
            if (name.length() > MAX_NAME_LENGTH) {
                throw new LineTooLongException(i + 1, MAX_NAME_LENGTH);
            }
            entries.add(NameEntry.of(i + 1, name));
        }
        if (count > maxNames) {
            throw new TooManyNamesException(count, maxNames);
        }
        return List.copyOf(entries);
    }

    /**
     * Counts the names (non-blank lines) without normalising them.
     *
     * @param text the submitted text, may be null
     * @return the number of non-blank lines
     */
    public static int countNames(String text) {
        if (text == null) {
            return 0;
        }
        int count = 0;
        for (String line : split(text)) {
            if (!isBlank(line)) {
                count++;
            }
        }
        return count;
    }

    private static String[] split(String text) {
        return text.replace("\ufeff", "").split("\\r\\n|\\r|\\n", -1);
    }

    /** Blank as {@link #normalize} sees it: only whitespace and non-breaking spaces. */
    private static boolean isBlank(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c != '\u00a0' && !Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }
}
