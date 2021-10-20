/*
 * Copyright 2019 Leibniz Institut für Analytische Wissenschaften - ISAS e.V..
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
package de.isas.lifs.palinom.webapp.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import org.lifstools.jgoslin.domain.FattyAcid;
import org.lifstools.jgoslin.domain.LipidAdduct;
import org.lifstools.jgoslin.domain.LipidSpeciesInfo;

/**
 *
 * @author nils.hoffmann
 */
@Data
public class ValidationResult {

    public static enum Grammar {
        GOSLIN, //GOSLIN_FRAGMENTS, 
        LIPIDMAPS, SWISSLIPIDS, HMDB, SHORTHAND, FATTY_ACID // NONE
    };

    private String lipidName;

    private Grammar grammar;

    private List<String> messages = Collections.emptyList();

    private LipidAdduct lipidAdduct;

    private LipidSpeciesInfo lipidSpeciesInfo;

    private String normalizedName;

    private String lipidMapsCategory;

    private String lipidMapsClass;

    private Double mass;

    private String sumFormula;

    private Optional<Collection<ExternalDatabaseReference>> lipidMapsReferences;

    private Optional<Collection<ExternalDatabaseReference>> swissLipidsReferences;

    private Map<String, FattyAcid> fattyAcids = Collections.emptyMap();

}
