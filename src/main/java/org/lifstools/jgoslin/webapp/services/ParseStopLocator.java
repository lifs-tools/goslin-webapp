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

import org.lifstools.jgoslin.webapp.domain.FailureExplanation;
import org.lifstools.jgoslin.webapp.domain.FailureExplanation.Hint;

/**
 * Classifies the failure of a lipid name that no grammar read, from where parsing stopped. Where parsing stopped
 * is found while the name is parsed ({@link LipidNameValidationService.ParsedName#stop()}): the longest
 * beginning of the name that one of the grammars read.
 */
public final class ParseStopLocator {

    private ParseStopLocator() {
    }

    /**
     * Where one grammar stopped: the length of the parser's error message if that is the beginning of the name
     * (jgoslin puts the part it could read there), otherwise 0.
     *
     * @param name the name
     * @param errorMessage the error message of the grammar's event handler
     * @return the position
     */
    static int stopOf(String name, String errorMessage) {
        if (errorMessage != null && !errorMessage.isEmpty() && errorMessage.length() <= name.length()
                && name.startsWith(errorMessage)) {
            return errorMessage.length();
        }
        return 0;
    }

    static FailureExplanation classify(String name, int position, String originalMessage) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                return new FailureExplanation(i, Hint.UNSUPPORTED_CHARACTER, originalMessage);
            }
        }
        if (count(name, '(') > count(name, ')') || count(name, '[') > count(name, ']')) {
            return new FailureExplanation(name.length(), Hint.MISSING_CLOSING_BRACKET, originalMessage);
        }
        int extra = firstExtraClosing(name);
        if (extra >= 0) {
            return new FailureExplanation(extra, Hint.EXTRA_CLOSING_BRACKET, originalMessage);
        }
        if (position < headGroupEnd(name)) {
            return new FailureExplanation(position, Hint.HEAD_GROUP, originalMessage);
        }
        return new FailureExplanation(position, Hint.AT_POSITION, originalMessage);
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    /** Index of the first closing bracket without a matching open one, or -1. */
    private static int firstExtraClosing(String name) {
        int round = 0;
        int square = 0;
        for (int i = 0; i < name.length(); i++) {
            switch (name.charAt(i)) {
                case '(' -> round++;
                case '[' -> square++;
                case ')' -> {
                    if (round-- == 0) {
                        return i;
                    }
                }
                case ']' -> {
                    if (square-- == 0) {
                        return i;
                    }
                }
                default -> {
                }
            }
        }
        return -1;
    }

    private static int headGroupEnd(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == ' ' || c == '(' || Character.isDigit(c)) {
                return i;
            }
        }
        return name.length();
    }
}
