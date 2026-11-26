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
 * Plain-language classification of where and why a lipid name failed to parse.
 *
 * @param position 0-based character index the hint refers to
 * @param hint the kind of problem
 * @param originalMessage the parser's original message
 */
public record FailureExplanation(int position, Hint hint, String originalMessage) {

    public enum Hint {
        UNSUPPORTED_CHARACTER, MISSING_CLOSING_BRACKET, EXTRA_CLOSING_BRACKET, HEAD_GROUP, AT_POSITION
    }

    public int markFrom() {
        return hint == Hint.HEAD_GROUP ? 0 : position;
    }

    public int markTo(String name) {
        return switch (hint) {
            case UNSUPPORTED_CHARACTER, EXTRA_CLOSING_BRACKET -> position + 1;
            default -> name.length();
        };
    }

    public String marked(String name) {
        return name.substring(from(name), to(name));
    }

    /** The text before the marked part. */
    public String beforeMark(String name) {
        return name.substring(0, from(name));
    }

    /** The text after the marked part. */
    public String afterMark(String name) {
        return name.substring(to(name));
    }

    private int from(String name) {
        return Math.min(markFrom(), name.length());
    }

    private int to(String name) {
        return Math.max(from(name), Math.min(markTo(name), name.length()));
    }
}
