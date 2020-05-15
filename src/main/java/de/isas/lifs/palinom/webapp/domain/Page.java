/*
 * Copyright 2017 Leibniz Institut für Analytische Wissenschaften - ISAS e.V..
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

import lombok.Data;

/**
 *
 * @author Nils Hoffmann &lt;nils.hoffmann@isas.de&gt;
 */
@Data
public class Page {

    private String title;
    private String appVersion;
    private String gaId;
    private String buildDate;
    private String scmCommitId;
    private String scmBranch;
    private String lifsUserUrl;
    private String jgoslinVersion;
    private String maxFileSize;

    public Page(String title, AppInfo appInfo) {
        this.title = title;
        this.appVersion = appInfo.getVersionNumber();
        String gaId = appInfo.getGaId();
        if (gaId != null) {
            gaId = (gaId.isEmpty() ? null : gaId);
        }
        this.gaId = gaId;
        this.buildDate = appInfo.getBuildDate();
        this.scmCommitId = appInfo.getScmCommitId();
        this.scmBranch = appInfo.getScmBranch();
        this.lifsUserUrl = appInfo.getLifsUserUrl();
        this.jgoslinVersion = appInfo.getJgoslinVersionNumber();
        this.maxFileSize = appInfo.getMaxFileSize();
    }
}
