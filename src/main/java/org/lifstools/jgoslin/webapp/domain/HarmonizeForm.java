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
package org.lifstools.jgoslin.webapp.domain;

/**
 * The input page form: the text area with one lipid name per line and the
 * "Skip invalid names" checkbox, which is checked by default.
 */
public class HarmonizeForm {

    private String lipidNames;
    private boolean skipInvalid = true;

    public HarmonizeForm() {
    }

    public HarmonizeForm(String lipidNames, boolean skipInvalid) {
        this.lipidNames = lipidNames;
        this.skipInvalid = skipInvalid;
    }

    public String getLipidNames() {
        return lipidNames;
    }

    public void setLipidNames(String lipidNames) {
        this.lipidNames = lipidNames;
    }

    public boolean isSkipInvalid() {
        return skipInvalid;
    }

    public void setSkipInvalid(boolean skipInvalid) {
        this.skipInvalid = skipInvalid;
    }
}
