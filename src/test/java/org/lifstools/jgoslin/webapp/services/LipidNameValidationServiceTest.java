/*
 * Copyright 2020 Leibniz Institut für Analytische Wissenschaften - ISAS e.V..
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author nilshoffmann
 */
public class LipidNameValidationServiceTest {

    public LipidNameValidationServiceTest() {
    }

    @Test
    public void testRegexForLipidMapsClass() {
        String lipidMapsClass = "Ceramides [SP02]";
        String regexp = ".+\\[([A-Z0-9]+)\\]";
        Pattern lmcRegexp = Pattern.compile(regexp);
        Matcher lmcMatcher = lmcRegexp.matcher(lipidMapsClass);
        String expectedResult = "SP02";
        String actualResult = "";
        assertTrue(lmcMatcher.matches());
        assertEquals(1, lmcMatcher.groupCount());
        if (lmcMatcher.matches() && lmcMatcher.groupCount() == 1) {
            actualResult = lmcMatcher.group(1);
        } else {
            actualResult = null;
        }
        assertEquals(expectedResult, actualResult);
    }

}
