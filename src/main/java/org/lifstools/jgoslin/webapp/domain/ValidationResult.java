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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.lifstools.jgoslin.domain.FattyAcid;
import org.lifstools.jgoslin.domain.LipidAdduct;

/**
 *
 * @author nils.hoffmann
 */
public class ValidationResult {

    public static enum Grammar {
        GOSLIN, //GOSLIN_FRAGMENTS, 
        LIPIDMAPS, SWISSLIPIDS, HMDB, SHORTHAND, FATTY_ACID, NONE;

        public static Grammar forName(String name) {
            switch (name.toUpperCase()) {
                case "GOSLIN" -> {
                    return GOSLIN;
                }
                case "LIPIDMAPS" -> {
                    return LIPIDMAPS;
                }
                case "SWISSLIPIDS" -> {
                    return SWISSLIPIDS;
                }
                case "HMDB" -> {
                    return HMDB;
                }
                case "SHORTHAND2020" -> {
                    return SHORTHAND;
                }
                case "FATTYACIDS" -> {
                    return FATTY_ACID;
                }
                default -> {
                    return NONE;
                }
            }
        }
    };

    private String lipidName;

    private Grammar grammar;

    private List<String> messages = Collections.emptyList();

    private LipidAdduct lipidAdduct;

    private Map<String, Integer> functionalGroupCounts;

    private String normalizedName;

    private String lipidMapsCategory;

    private String lipidMapsClass;

    private String lipidCategoryName;

    private String lipidClassName;

    private String lipidExtendedSpeciesName;

    private String lipidSpeciesName;

    private String lipidMolecularSpeciesName;

    private String lipidSnPositionName;

    private String lipidStructureDefinedName;

    private String lipidFullStructureName;

    private String lipidCompleteStructureName;

    private Double mass;

    private String sumFormula;

    private Optional<Collection<ExternalDatabaseReference>> lipidMapsReferences;

    private Optional<Collection<ExternalDatabaseReference>> swissLipidsReferences;

    private Optional<Collection<ExternalDatabaseReference>> chebiReferences;
    
    public static String toFunctionalGroupString(LipidAdduct la, FattyAcid fa) {
        return fa.getFunctionalGroups().entrySet().stream().map((entry) -> {
            return entry.getValue().stream().map((functionalGroup) -> {
                return functionalGroup.toString(la.getLipidLevel());
            }).collect(Collectors.joining(",", "[", "]"));
        }).collect(Collectors.joining(",", "{", "}"));
    }

    public static Integer getTotalFunctionalGroupCount(LipidAdduct la, String functionalGroup) {
        return la.getLipid().getInfo().getTotalFunctionalGroupCount(functionalGroup);
    }

    public ValidationResult() {

    }

    public ValidationResult(String lipidName, Grammar grammar, LipidAdduct lipidAdduct, Map<String, Integer> functionalGroupCounts, String normalizedName, String lipidMapsCategory, String lipidMapsClass,
            String lipidCategoryName, String lipidClassName, String lipidExtendedSpeciesName,
            String lipidSpeciesName, String lipidMolecularSpeciesName, String lipidSnPositionName, String lipidStructureDefinedName, String lipidFullStructureName, String lipidCompleteStructureName,
            Double mass, String sumFormula, Optional<Collection<ExternalDatabaseReference>> lipidMapsReferences, Optional<Collection<ExternalDatabaseReference>> swissLipidsReferences, Optional<Collection<ExternalDatabaseReference>> chebiReferences) {
        this.lipidName = lipidName;
        this.grammar = grammar;
        this.lipidAdduct = lipidAdduct;
//        this.lipidSpeciesInfo = lipidSpeciesInfo;
        this.functionalGroupCounts = functionalGroupCounts;
        this.normalizedName = normalizedName;
        this.lipidMapsCategory = lipidMapsCategory;
        this.lipidMapsClass = lipidMapsClass;
        this.lipidCategoryName = lipidCategoryName;
        this.lipidClassName = lipidClassName;
        this.lipidExtendedSpeciesName = lipidExtendedSpeciesName;
        this.lipidSpeciesName = lipidSpeciesName;
        this.lipidMolecularSpeciesName = lipidMolecularSpeciesName;
        this.lipidSnPositionName = lipidSnPositionName;
        this.lipidStructureDefinedName = lipidStructureDefinedName;
        this.lipidFullStructureName = lipidFullStructureName;
        this.lipidCompleteStructureName = lipidCompleteStructureName;
        this.mass = mass;
        this.sumFormula = sumFormula;
        this.lipidMapsReferences = lipidMapsReferences;
        this.swissLipidsReferences = swissLipidsReferences;
        this.chebiReferences = chebiReferences;
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

    public Map<String, Integer> getFunctionalGroupCounts() {
        return functionalGroupCounts;
    }

    public void setFunctionalGroupCounts(Map<String, Integer> functionalGroupCounts) {
        this.functionalGroupCounts = functionalGroupCounts;
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

    public String getLipidCategoryName() {
        return lipidCategoryName;
    }

    public void setLipidCategoryName(String lipidCategoryName) {
        this.lipidCategoryName = lipidCategoryName;
    }

    public String getLipidClassName() {
        return lipidClassName;
    }

    public void setLipidClassName(String lipidClassName) {
        this.lipidClassName = lipidClassName;
    }

    public String getLipidExtendedSpeciesName() {
        return lipidExtendedSpeciesName;
    }

    public void setLipidExtendedSpeciesName(String lipidExtendedSpeciesName) {
        this.lipidExtendedSpeciesName = lipidExtendedSpeciesName;
    }

    public String getLipidSpeciesName() {
        return lipidSpeciesName;
    }

    public void setLipidSpeciesName(String lipidSpeciesName) {
        this.lipidSpeciesName = lipidSpeciesName;
    }

    public String getLipidMolecularSpeciesName() {
        return lipidMolecularSpeciesName;
    }

    public void setLipidMolecularSpeciesName(String lipidMolecularSpeciesName) {
        this.lipidMolecularSpeciesName = lipidMolecularSpeciesName;
    }

    public String getLipidSnPositionName() {
        return lipidSnPositionName;
    }

    public void setLipidSnPositionName(String lipidSnPositionName) {
        this.lipidSnPositionName = lipidSnPositionName;
    }

    public String getLipidStructureDefinedName() {
        return lipidStructureDefinedName;
    }

    public void setLipidStructureDefinedName(String lipidStructureDefinedName) {
        this.lipidStructureDefinedName = lipidStructureDefinedName;
    }

    public String getLipidFullStructureName() {
        return lipidFullStructureName;
    }

    public void setLipidFullStructureName(String lipidFullStructureName) {
        this.lipidFullStructureName = lipidFullStructureName;
    }

    public String getLipidCompleteStructureName() {
        return lipidCompleteStructureName;
    }

    public void setLipidCompleteStructureName(String lipidCompleteStructureName) {
        this.lipidCompleteStructureName = lipidCompleteStructureName;
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
    
    public Optional<Collection<ExternalDatabaseReference>> getChebiReferences() {
        return chebiReferences;
    }

    public void setChebiReferences(Optional<Collection<ExternalDatabaseReference>> chebiReferences) {
        this.chebiReferences = chebiReferences;
    }

    @Override
    public String toString() {
        return "ValidationResult{" + "lipidName=" + lipidName
                + ", grammar=" + grammar + ", messages=" + messages
                + ", lipidAdduct=" + lipidAdduct + ", functionalGroupCounts=" + functionalGroupCounts
                + ", normalizedName=" + normalizedName + ", lipidMapsCategory=" + lipidMapsCategory
                + ", lipidMapsClass=" + lipidMapsClass
                + ", lipidCategoryName=" + lipidCategoryName
                + ", lipidClassName=" + lipidClassName
                + ", lipidExtendedSpeciesName=" + lipidExtendedSpeciesName
                + ", lipidSpeciesName=" + lipidSpeciesName
                + ", lipidMolecularSpeciesName=" + lipidMolecularSpeciesName
                + ", lipidSnPositionName" + lipidSnPositionName
                + ", lipidStructureDefinedName" + lipidStructureDefinedName
                + ", lipidFullStructureName" + lipidFullStructureName
                + ", lipidCompleteStructureName" + lipidCompleteStructureName
                + ", mass=" + mass
                + ", sumFormula=" + sumFormula
                + ", lipidMapsReferences=" + lipidMapsReferences
                + ", swissLipidsReferences=" + swissLipidsReferences
                + ", chebiReferences=" + chebiReferences
                + '}';
    }

}
