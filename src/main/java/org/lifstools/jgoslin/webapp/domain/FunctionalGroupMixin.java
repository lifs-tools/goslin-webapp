/*
 * Copyright 2026 Lipidomics Informatics for Life Sciences.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.lifstools.jgoslin.domain.FunctionalGroup;
import org.springframework.boot.jackson.JacksonMixin;

/**
 * Keeps the JSON name "ndoubleBonds" of {@code getNDoubleBonds()} in jgoslin's
 * {@link FunctionalGroup} (and its subclasses FattyAcid, LipidSpeciesInfo and
 * Cycle). Jackson 2 lower-cased the leading upper-case letters; Jackson 3 keeps
 * them ("NDoubleBonds"), which would change the REST API.
 */
@JacksonMixin(FunctionalGroup.class)
public abstract class FunctionalGroupMixin {

    @JsonProperty("ndoubleBonds")
    abstract int getNDoubleBonds();
}
