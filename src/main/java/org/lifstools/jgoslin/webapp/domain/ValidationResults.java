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

import java.util.List;

/**
 *
 * @author nils.hoffmann
 */
public class ValidationResults {

    private List<ValidationResult> results;
    private Long totalReceived;
    private Long totalParsed;
    private Long failedToParse;

    public ValidationResults() {
    }

    public ValidationResults(List<ValidationResult> results, Long totalReceived, Long totalParsed, Long failedToParse) {
        this.results = results;
        this.totalReceived = totalReceived;
        this.totalParsed = totalParsed;
        this.failedToParse = failedToParse;
    }

    public List<ValidationResult> getResults() {
        return results;
    }

    public void setResults(List<ValidationResult> results) {
        this.results = results;
    }

    public Long getTotalReceived() {
        return totalReceived;
    }

    public void setTotalReceived(Long totalReceived) {
        this.totalReceived = totalReceived;
    }

    public Long getTotalParsed() {
        return totalParsed;
    }

    public void setTotalParsed(Long totalParsed) {
        this.totalParsed = totalParsed;
    }

    public Long getFailedToParse() {
        return failedToParse;
    }

    public void setFailedToParse(Long failedToParse) {
        this.failedToParse = failedToParse;
    }

    @Override
    public String toString() {
        return "ValidationResults{" + "results=" + results + ", totalReceived=" + totalReceived + ", totalParsed=" + totalParsed + ", failedToParse=" + failedToParse + '}';
    }

}
