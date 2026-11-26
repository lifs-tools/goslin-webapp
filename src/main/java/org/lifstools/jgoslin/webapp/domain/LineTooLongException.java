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
 * Thrown when a line of a submission is longer than a lipid name can be.
 */
public class LineTooLongException extends RuntimeException {

    private final int line;
    private final int max;

    public LineTooLongException(int line, int max) {
        super("Line " + line + " is longer than " + max + " characters");
        this.line = line;
        this.max = max;
    }

    public int getLine() {
        return line;
    }

    public int getMax() {
        return max;
    }
}
