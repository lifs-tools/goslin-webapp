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
package org.lifstools.jgoslin.webapp.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An external database reference in ChEBI for a lipid encodes the link to
 * identify the referenced lipid, contains its native abbreviation and name, the
 * database's element id and the name as normalized by Goslin / PaLiNom.
 *
 * @author nils.hoffmann
 */
public class ExternalChebiDatabaseReference {

    /*
        ID      COMPOUND_ID     TYPE    SOURCE  NAME    ADAPTED LANGUAGE

     */
    private final String BASE_URL = "https://www.ebi.ac.uk/chebi/searchId.do?chebiId=CHEBI:";

    private String databaseUrl;
    private String id;
    private String compoundId;
    private String type;
    private String source;
    private String name;
    private String adapted;
    private String language;

    public ExternalChebiDatabaseReference() {
    }

    public ExternalChebiDatabaseReference(String id, String compoundId, String type, String source, String name, String adapted, String language) {
        this.id = id;
        this.compoundId = compoundId;
        this.type = type;
        this.source = source;
        this.name = name;
        this.adapted = adapted;
        this.language = language;
    }

    public String getDatabaseUrl() {
        return BASE_URL + this.compoundId;
    }

    @JsonProperty("ID")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("COMPOUND_ID")
    public String getCompoundId() {
        return compoundId;
    }

    public void setCompoundId(String compoundId) {
        this.compoundId = compoundId;
    }

    @JsonProperty("TYPE")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("SOURCE")
    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @JsonProperty("NAME")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("ADAPTED")
    public String getAdapted() {
        return adapted;
    }

    public void setAdapted(String adapted) {
        this.adapted = adapted;
    }
    
    @JsonProperty("LANGUAGE")
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    } 

    @Override
    public String toString() {
        return "ExternalChebiDatabaseReference{" + "databaseUrl=" + getDatabaseUrl() + ", id=" + id + ", compoundId=" + compoundId + ", type=" + type + ", source=" + source + ", name=" + name + ", adapted=" + adapted + ", language=" + language + '}';
    }
    
}
