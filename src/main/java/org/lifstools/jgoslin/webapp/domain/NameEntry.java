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

/**
 * A single submitted lipid name with its 1-based line number in the submitted text.
 */
public record NameEntry(int line, String original, String current, boolean corrected, boolean removed) {

    public static NameEntry of(int line, String text) {
        return new NameEntry(line, text, text, false, false);
    }

    public NameEntry withCorrection(String text) {
        return new NameEntry(line, original, text, true, removed);
    }

    public NameEntry asRemoved() {
        return new NameEntry(line, original, current, corrected, true);
    }
}
