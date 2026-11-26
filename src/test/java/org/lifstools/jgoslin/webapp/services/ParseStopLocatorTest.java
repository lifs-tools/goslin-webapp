package org.lifstools.jgoslin.webapp.services;

import org.lifstools.jgoslin.webapp.domain.FailureExplanation;
import org.lifstools.jgoslin.webapp.domain.FailureExplanation.Hint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParseStopLocatorTest {

    @Test
    void unsupportedCharacterWins() {
        String name = "PC 16:0–18:1";
        FailureExplanation e = ParseStopLocator.classify(name, 0, "m");
        assertEquals(Hint.UNSUPPORTED_CHARACTER, e.hint());
        assertEquals(7, e.position());
        assertEquals("–", e.marked(name));
    }

    @Test
    void missingClosingBracket() {
        String name = "PC(18:2-20:4";
        FailureExplanation e = ParseStopLocator.classify(name, 2, "m");
        assertEquals(Hint.MISSING_CLOSING_BRACKET, e.hint());
        assertEquals(12, e.position());
        assertEquals("", e.marked(name));
    }

    @Test
    void extraClosingBracket() {
        String name = "SM d18:1/24:0)";
        FailureExplanation e = ParseStopLocator.classify(name, 13, "m");
        assertEquals(Hint.EXTRA_CLOSING_BRACKET, e.hint());
        assertEquals(13, e.position());
        assertEquals(")", e.marked(name));
    }

    @Test
    void wholeWordIsHeadGroup() {
        String name = "Cholesterl";
        FailureExplanation e = ParseStopLocator.classify(name, 4, "m");
        assertEquals(Hint.HEAD_GROUP, e.hint());
        assertEquals(name, e.marked(name));
    }

    @Test
    void stopInsideHeadGroup() {
        assertEquals(Hint.HEAD_GROUP, ParseStopLocator.classify("Xyz 16:0", 0, "m").hint());
    }

    @Test
    void stopAfterHeadGroupIsAtPosition() {
        String name = "TG 16:0_18:1_18:2x";
        FailureExplanation e = ParseStopLocator.classify(name, 17, "m");
        assertEquals(Hint.AT_POSITION, e.hint());
        assertEquals("x", e.marked(name));
    }

    @Test
    void keepsOriginalMessage() {
        assertEquals("Could not parse x", ParseStopLocator.classify("PC 34:1x", 7, "Could not parse x").originalMessage());
    }

    @Test
    void stopIsTheErrorMessageIfThatBeginsTheName() {
        assertEquals(7, ParseStopLocator.stopOf("PC 34:1x", "PC 34:1"));
        assertEquals(0, ParseStopLocator.stopOf("PC 34:1x", "TG"));
        assertEquals(0, ParseStopLocator.stopOf("PC 34:1x", ""));
        assertEquals(0, ParseStopLocator.stopOf("PC 34:1x", null));
    }
}
