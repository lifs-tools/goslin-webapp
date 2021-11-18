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
package org.lifstools.jgoslin.webapp.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.lifstools.jgoslin.domain.FattyAcid;
import org.lifstools.jgoslin.domain.LipidAdduct;
import org.lifstools.jgoslin.domain.LipidSpeciesInfo;

/**
 *
 * @author nils.hoffmann
 */
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

    private List<FattyAcid> fattyAcids = Collections.emptyList();

    public static String toFunctionalGroupString(LipidAdduct la, FattyAcid fa) {
        return fa.getFunctionalGroups().entrySet().stream().map((entry) -> {
            return entry.getValue().stream().map((functionalGroup) -> {
                return functionalGroup.toString(la.getLipidLevel());
            }).collect(Collectors.joining(",", "[", "]"));
        }).collect(Collectors.joining(",", "{", "}"));
    }

    public ValidationResult() {

    }

    public ValidationResult(String lipidName, Grammar grammar, LipidAdduct lipidAdduct, LipidSpeciesInfo lipidSpeciesInfo, String normalizedName, String lipidMapsCategory, String lipidMapsClass, Double mass, String sumFormula, Optional<Collection<ExternalDatabaseReference>> lipidMapsReferences, Optional<Collection<ExternalDatabaseReference>> swissLipidsReferences) {
        this.lipidName = lipidName;
        this.grammar = grammar;
        this.lipidAdduct = lipidAdduct;
        this.lipidSpeciesInfo = lipidSpeciesInfo;
        this.normalizedName = normalizedName;
        this.lipidMapsCategory = lipidMapsCategory;
        this.lipidMapsClass = lipidMapsClass;
        this.mass = mass;
        this.sumFormula = sumFormula;
        this.lipidMapsReferences = lipidMapsReferences;
        this.swissLipidsReferences = swissLipidsReferences;
    }

    public String getLipidName() {
        return lipidName;
    }

    public void setLipidName(String lipidName) {
        this.lipidName = lipidName;
    }

    public Grammar getGrammar() {
        return grammar;
    }

    public void setGrammar(Grammar grammar) {
        this.grammar = grammar;
    }

    public List<String> getMessages() {
        return messages;
    }

    public void setMessages(List<String> messages) {
        this.messages = messages;
    }

    public LipidAdduct getLipidAdduct() {
        return lipidAdduct;
    }

    public void setLipidAdduct(LipidAdduct lipidAdduct) {
        this.lipidAdduct = lipidAdduct;
    }

    public LipidSpeciesInfo getLipidSpeciesInfo() {
        return lipidSpeciesInfo;
    }

    public void setLipidSpeciesInfo(LipidSpeciesInfo lipidSpeciesInfo) {
        this.lipidSpeciesInfo = lipidSpeciesInfo;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String getLipidMapsCategory() {
        return lipidMapsCategory;
    }

    public void setLipidMapsCategory(String lipidMapsCategory) {
        this.lipidMapsCategory = lipidMapsCategory;
    }

    public String getLipidMapsClass() {
        return lipidMapsClass;
    }

    public void setLipidMapsClass(String lipidMapsClass) {
        this.lipidMapsClass = lipidMapsClass;
    }

    public Double getMass() {
        return mass;
    }

    public void setMass(Double mass) {
        this.mass = mass;
    }

    public String getSumFormula() {
        return sumFormula;
    }

    public void setSumFormula(String sumFormula) {
        this.sumFormula = sumFormula;
    }

    public Optional<Collection<ExternalDatabaseReference>> getLipidMapsReferences() {
        return lipidMapsReferences;
    }

    public void setLipidMapsReferences(Optional<Collection<ExternalDatabaseReference>> lipidMapsReferences) {
        this.lipidMapsReferences = lipidMapsReferences;
    }

    public Optional<Collection<ExternalDatabaseReference>> getSwissLipidsReferences() {
        return swissLipidsReferences;
    }

    public void setSwissLipidsReferences(Optional<Collection<ExternalDatabaseReference>> swissLipidsReferences) {
        this.swissLipidsReferences = swissLipidsReferences;
    }

    public List<FattyAcid> getFattyAcids() {
        return fattyAcids;
    }

    public void setFattyAcids(List<FattyAcid> fattyAcids) {
        this.fattyAcids = fattyAcids;
    }

    @Override
    public String toString() {
        return "ValidationResult{" + "lipidName=" + lipidName + ", grammar=" + grammar + ", messages=" + messages + ", lipidAdduct=" + lipidAdduct + ", lipidSpeciesInfo=" + lipidSpeciesInfo + ", normalizedName=" + normalizedName + ", lipidMapsCategory=" + lipidMapsCategory + ", lipidMapsClass=" + lipidMapsClass + ", mass=" + mass + ", sumFormula=" + sumFormula + ", lipidMapsReferences=" + lipidMapsReferences + ", swissLipidsReferences=" + swissLipidsReferences + ", fattyAcids=" + fattyAcids + '}';
    }

}
