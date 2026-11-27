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
package org.lifstools.jgoslin.webapp.config;

import org.lifstools.jgoslin.webapp.domain.DatabaseCoverage.Database;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Version texts of the external database dumps, {@code goslin.databases.*} (see application.properties).
 */
@ConfigurationProperties("goslin.databases")
public record DatabaseProperties(@DefaultValue("3 March 2023") String lipidMapsVersion,
        @DefaultValue("early 2023") String swissLipidsVersion,
        @DefaultValue("18 January 2024") String chebiVersion) {

    public String versionOf(Database database) {
        return switch (database) {
            case LIPID_MAPS -> lipidMapsVersion;
            case SWISS_LIPIDS -> swissLipidsVersion;
            case CHEBI -> chebiVersion;
        };
    }
}
