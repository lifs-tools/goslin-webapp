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

import java.util.List;
import org.junit.jupiter.api.Test;
import org.lifstools.jgoslin.webapp.domain.DatabaseCoverage;
import org.lifstools.jgoslin.webapp.domain.DatabaseCoverage.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles(profiles = {"test", "dev"})
class DatabaseCoverageTest {

    @Autowired
    private ExternalDatabaseMappingLoader loader;

    @Test
    void coverageMatchesTheLoadedDumps() {
        List<DatabaseCoverage> coverage = loader.getCoverage();
        assertThat(coverage).extracting(DatabaseCoverage::database)
                .containsExactly(Database.LIPID_MAPS, Database.SWISS_LIPIDS, Database.CHEBI);
        DatabaseCoverage lipidMaps = coverage.get(0);
        DatabaseCoverage swissLipids = coverage.get(1);
        DatabaseCoverage chebi = coverage.get(2);
        assertThat(lipidMaps.entriesLoaded()).isEqualTo(28_942);
        assertThat(lipidMaps.namesRead()).isEqualTo(26_832);
        assertThat(swissLipids.entriesLoaded()).isEqualTo(777_956);
        assertThat(swissLipids.namesRead()).isEqualTo(766_511);
        assertThat(chebi.entriesLoaded()).isEqualTo(381_571);
        // depends on the jgoslin build
        assertThat(chebi.namesRead()).isBetween(2_000L, 3_000L);
        assertThat(lipidMaps.classified() / (double) lipidMaps.namesRead()).isGreaterThan(0.99);
        assertThat(swissLipids.classified() / (double) swissLipids.namesRead()).isGreaterThan(0.99);
    }
}
