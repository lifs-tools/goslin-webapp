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

/**
 * An external database reference for a lipid encodes the link to identify the
 * referenced lipid, contains its native abbreviation and name, the database's
 * element id and the name as normalized by Goslin / PaLiNom.
 *
 * @author nils.hoffmann
 */
public class ExternalDatabaseReference {

    private String databaseUrl;
    private String databaseElementId;
    private String lipidLevel;
    private String nativeAbbreviation;
    private String nativeName;
    private String normalizedName;

    public ExternalDatabaseReference() {
    }

    public ExternalDatabaseReference(String databaseUrl, String databaseElementId, String lipidLevel, String nativeAbbreviation, String nativeName, String normalizedName) {
        this.databaseUrl = databaseUrl;
        this.databaseElementId = databaseElementId;
        this.lipidLevel = lipidLevel;
        this.nativeAbbreviation = nativeAbbreviation;
        this.nativeName = nativeName;
        this.normalizedName = normalizedName;
    }

    public String getDatabaseUrl() {
        return databaseUrl;
    }

    public void setDatabaseUrl(String databaseUrl) {
        this.databaseUrl = databaseUrl;
    }

    public String getDatabaseElementId() {
        return databaseElementId;
    }

    public void setDatabaseElementId(String databaseElementId) {
        this.databaseElementId = databaseElementId;
    }

    public String getLipidLevel() {
        return lipidLevel;
    }

    public void setLipidLevel(String lipidLevel) {
        this.lipidLevel = lipidLevel;
    }

    public String getNativeAbbreviation() {
        return nativeAbbreviation;
    }

    public void setNativeAbbreviation(String nativeAbbreviation) {
        this.nativeAbbreviation = nativeAbbreviation;
    }

    public String getNativeName() {
        return nativeName;
    }

    public void setNativeName(String nativeName) {
        this.nativeName = nativeName;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    @Override
    public String toString() {
        return "ExternalDatabaseReference{" + "databaseUrl=" + databaseUrl + ", databaseElementId=" + databaseElementId + ", lipidLevel=" + lipidLevel + ", nativeAbbreviation=" + nativeAbbreviation + ", nativeName=" + nativeName + ", normalizedName=" + normalizedName + '}';
    }

}
