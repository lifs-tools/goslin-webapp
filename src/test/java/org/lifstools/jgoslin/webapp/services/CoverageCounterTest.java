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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.lifstools.jgoslin.domain.Headgroup;
import org.lifstools.jgoslin.domain.LipidAdduct;
import org.lifstools.jgoslin.domain.LipidCategory;
import org.lifstools.jgoslin.domain.LipidClassMeta;
import org.lifstools.jgoslin.domain.LipidClasses;
import org.lifstools.jgoslin.parser.GoslinParser;
import org.lifstools.jgoslin.webapp.domain.DatabaseCoverage;
import org.lifstools.jgoslin.webapp.domain.DatabaseCoverage.Database;

class CoverageCounterTest {

    private static LipidClassMeta meta(int classIndex) {
        return LipidClasses.getInstance().get(classIndex);
    }

    @Test
    void classifiesByHeadGroupToken() {
        OptionalInt pc = CoverageCounter.classOf("PC 34:1");
        assertThat(pc).isPresent();
        assertThat(meta(pc.getAsInt()).lipidClassName).isEqualTo(meta(Headgroup.getClass("PC")).lipidClassName);
        assertThat(meta(pc.getAsInt()).lipidCategory).isEqualTo(LipidCategory.GP);
    }

    @Test
    void classifiesHeadGroupWithBracket() {
        OptionalInt bracket = CoverageCounter.classOf("PE-N(FA 16:0) 34:1");
        assertThat(bracket).isPresent();
        int plain = Headgroup.getClass("PE-N");
        if (plain != LipidClasses.UNDEFINED_CLASS) {
            assertThat(bracket.getAsInt()).isEqualTo(plain);
        } else {
            assertThat(meta(bracket.getAsInt()).lipidCategory).isEqualTo(LipidCategory.GP);
        }
    }

    @Test
    void unknownHeadGroupIsNotClassified() {
        assertThat(CoverageCounter.classOf("NeuAc-Gal-Glc-Cer 36:1;O2")).isEmpty();
    }

    @Test
    void unreadNamesAreNotClassified() {
        CoverageCounter counter = new CoverageCounter(Database.LIPID_MAPS);
        counter.countName("UNDEFINED");
        counter.countName("NA");
        counter.countName("");
        counter.countName("PC 34:1");
        DatabaseCoverage coverage = counter.build();
        assertThat(coverage.entriesLoaded()).isEqualTo(4);
        assertThat(coverage.namesRead()).isEqualTo(1);
        assertThat(coverage.byClass()).doesNotContainKey("UNDEFINED");
        assertThat(coverage.byCategory()).isEqualTo(Map.of(LipidCategory.GP, 1L));
    }

    @Test
    void unclassifiedReadNamesGoToUndefined() {
        CoverageCounter counter = new CoverageCounter(Database.SWISS_LIPIDS);
        counter.countName("NeuAc-Gal-Glc-Cer 36:1;O2");
        counter.countName("FA 18:1");
        DatabaseCoverage coverage = counter.build();
        assertThat(coverage.namesRead()).isEqualTo(2);
        assertThat(coverage.classified()).isEqualTo(1);
        assertThat(coverage.byCategory()).isEqualTo(Map.of(LipidCategory.FA, 1L, LipidCategory.UNDEFINED, 1L));
    }

    @Test
    void sharesSumToHundred() {
        CoverageCounter counter = new CoverageCounter(Database.LIPID_MAPS);
        for (String name : new String[]{"PC 34:1", "PE 36:2", "FA 18:1", "TG 50:1", "Cer 18:1;O2/16:0", "NeuAc-Gal-Glc-Cer 36:1;O2"}) {
            counter.countName(name);
        }
        DatabaseCoverage coverage = counter.build();
        double sum = coverage.byCategory().keySet().stream().mapToDouble(coverage::share).sum();
        assertThat(sum).isCloseTo(100.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(coverage.share(LipidCategory.PK)).isZero();
    }

    @Test
    void chebiCountsParsedAndUnparsed() {
        GoslinParser parser = new GoslinParser();
        LipidAdduct adduct = parser.parse("PC 16:0/18:1", parser.newEventHandler(), false);
        CoverageCounter counter = new CoverageCounter(Database.CHEBI);
        counter.countParsed(adduct);
        counter.countUnparsed();
        counter.countUnparsed();
        DatabaseCoverage coverage = counter.build();
        assertThat(coverage.entriesLoaded()).isEqualTo(3);
        assertThat(coverage.namesRead()).isEqualTo(1);
        assertThat(coverage.byCategory()).isEqualTo(new EnumMap<>(Map.of(LipidCategory.GP, 1L)));
    }
}
