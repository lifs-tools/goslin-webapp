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

import java.util.List;
import org.lifstools.jgoslin.domain.KnownFunctionalGroups;
import org.lifstools.jgoslin.domain.LipidAdduct;
import org.lifstools.jgoslin.parser.BaseParserEventHandler;
import org.lifstools.jgoslin.parser.FattyAcidParser;
import org.lifstools.jgoslin.parser.GoslinParser;
import org.lifstools.jgoslin.parser.HmdbParser;
import org.lifstools.jgoslin.parser.LipidMapsParser;
import org.lifstools.jgoslin.parser.Parser;
import org.lifstools.jgoslin.parser.ShorthandParser;
import org.lifstools.jgoslin.parser.SwissLipidsParser;
import org.lifstools.jgoslin.webapp.domain.FailureExplanation;
import org.lifstools.jgoslin.webapp.domain.FailureExplanation.Hint;
import org.springframework.stereotype.Service;

/**
 * Finds where parsing of a failed lipid name stopped and classifies the
 * failure. Safe for concurrent use: the parsers keep no per-parse state (the
 * dynamic programming tables are local to each parse call); the event handler
 * is created per call.
 */
@Service
public class ParseStopLocator {

    private final List<Parser<LipidAdduct>> parsers;

    public ParseStopLocator() {
        KnownFunctionalGroups groups = new KnownFunctionalGroups();
        parsers = List.of(
                new ShorthandParser(groups),
                new FattyAcidParser(groups),
                new GoslinParser(groups),
                new LipidMapsParser(groups),
                new SwissLipidsParser(groups),
                new HmdbParser(groups));
    }

    public int furthestStop(String name) {
        int best = 0;
        for (Parser<LipidAdduct> parser : parsers) {
            BaseParserEventHandler<LipidAdduct> handler = parser.newEventHandler();
            parser.parse(name, handler, false);
            String prefix = handler.getErrorMessage();
            if (prefix != null && !prefix.isEmpty() && prefix.length() <= name.length()
                    && name.startsWith(prefix)) {
                best = Math.max(best, prefix.length());
            }
        }
        return best;
    }

    public FailureExplanation explain(String name, String originalMessage) {
        return classify(name, furthestStop(name), originalMessage);
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
