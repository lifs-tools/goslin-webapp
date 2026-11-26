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

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NameEntriesTest {

    private static List<Integer> lines(List<NameEntry> entries) {
        return entries.stream().map(NameEntry::line).toList();
    }

    @Test
    void numbersLinesFromOneAndSkipsBlankLines() {
        List<NameEntry> e = NameEntries.fromText("PC 32:0\n\nTG 52:2", 10);
        assertEquals(List.of(1, 3), lines(e));
        assertEquals("TG 52:2", e.get(1).current());
    }

    @Test
    void windowsLineEndingsAndTrailingBlankLines() {
        List<NameEntry> e = NameEntries.fromText("a\r\n\r\nb\r\n\r\n", 10);
        assertEquals(List.of(NameEntry.of(1, "a"), NameEntry.of(3, "b")), e);
    }

    @Test
    void oldMacLineEndings() {
        assertEquals(List.of(1, 2), lines(NameEntries.fromText("a\rb", 10)));
    }

    @Test
    void trimsAndCollapsesWhitespace() {
        NameEntry e = NameEntries.fromText("  PE  16:0 /\t18:1 ", 10).get(0);
        assertEquals("PE 16:0 / 18:1", e.current());
        assertEquals("PE 16:0 / 18:1", e.original());
    }

    @Test
    void keepsDuplicatesAsSeparateEntries() {
        List<NameEntry> e = NameEntries.fromText("PC 32:0\nPC 32:0", 10);
        assertEquals(2, e.size());
        assertEquals(List.of(1, 2), lines(e));
    }

    @Test
    void rejectsMoreThanMax() {
        TooManyNamesException ex = assertThrows(TooManyNamesException.class,
                () -> NameEntries.fromText("a\nb\nc", 2));
        assertEquals(3, ex.getCount());
        assertEquals(2, ex.getMax());
    }

    @Test
    void blankLinesDoNotCountTowardsMax() {
        assertEquals(2, NameEntries.fromText("a\n\n\nb", 2).size());
    }

    @Test
    void correctionKeepsLineAndOriginal() {
        NameEntry e = NameEntry.of(34, "PC(18:2").withCorrection("PC(18:2)");
        assertEquals(34, e.line());
        assertEquals("PC(18:2", e.original());
        assertEquals("PC(18:2)", e.current());
        assertTrue(e.corrected());
        assertFalse(e.removed());
        assertTrue(e.asRemoved().removed());
    }

    @Test
    void dropsByteOrderMarkAndNonBreakingSpaces() {
        List<NameEntry> e = NameEntries.fromText("﻿PC 32:0\nTG 52:2", 10);
        assertEquals(List.of(NameEntry.of(1, "PC 32:0"), NameEntry.of(2, "TG 52:2")), e);
    }

    @Test
    void nullOrBlankGivesEmptyList() {
        assertTrue(NameEntries.fromText(null, 10).isEmpty());
        assertTrue(NameEntries.fromText(" \n ", 10).isEmpty());
    }

    @Test
    void rejectsALineLongerThanTheCapAndNamesIt() {
        String longName = "a".repeat(NameEntries.MAX_LINE_LENGTH + 1);
        LineTooLongException ex = assertThrows(LineTooLongException.class,
                () -> NameEntries.fromText("PC 32:0\n\n" + longName, 10));
        assertEquals(3, ex.getLine());
        assertEquals(NameEntries.MAX_LINE_LENGTH, ex.getMax());
    }

    @Test
    void lineLengthIsMeasuredAfterNormalising() {
        String name = "a".repeat(NameEntries.MAX_LINE_LENGTH);
        assertEquals(1, NameEntries.fromText("   " + name + "   ", 10).size());
    }

    @Test
    void countsLinesBeyondTheMaxWithoutCheckingThem() {
        String longName = "a".repeat(NameEntries.MAX_LINE_LENGTH + 1);
        TooManyNamesException ex = assertThrows(TooManyNamesException.class,
                () -> NameEntries.fromText("a\nb\n \u00a0 \n" + longName + "\nc", 1));
        assertEquals(4, ex.getCount());
    }

    @Test
    void countNamesCountsNonBlankLines() {
        assertEquals(3, NameEntries.countNames("a\r\n\r\nb\n\u00a0\n\ufeff\nc"));
        assertEquals(0, NameEntries.countNames(null));
    }
}
