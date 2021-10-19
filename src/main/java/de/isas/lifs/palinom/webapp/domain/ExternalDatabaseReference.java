/*
 * Copyright 2020  nils.hoffmann.
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

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * An external database reference for a lipid encodes the link to identify the
 * referenced lipid, contains its native abbreviation and name, the database's
 * element id and the name as normalized by Goslin / PaLiNom.
 *
 * @author nils.hoffmann
 */
@Data
@AllArgsConstructor
public class ExternalDatabaseReference {

    private String databaseUrl;
    private String databaseElementId;
    private String lipidLevel;
    private String nativeAbbreviation;
    private String nativeName;
    private String normalizedName;

    public ExternalDatabaseReference() {
    }
}
