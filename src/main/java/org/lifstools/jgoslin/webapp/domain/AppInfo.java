/*
 * Copyright 2018 Leibniz Institut für Analytische Wissenschaften - ISAS e.V..
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

import de.isas.lifs.webapps.common.domain.DefaultAppInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 *
 * @author nilshoffmann
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Data
public class AppInfo extends DefaultAppInfo {

    private String maxFileSize;

    public AppInfo(DefaultAppInfoBuilder<?, ?> b) {
        super(b);
    }

    public AppInfo(String buildDate, String scmCommitId, String scmBranch, String scmUrl, String supportUrl, String versionNumber, String toolTitle, String toolDescription, String toolAuthor, String toolLicense, String toolLicenseUrl, String toolVersionNumber, String toolUrl, String toolContact, String gaId, String authServerBaseUrl, String authServerRealm, String maxFileSize) {
        super(buildDate, scmCommitId, scmBranch, scmUrl, supportUrl, versionNumber, toolTitle, toolDescription, toolAuthor, toolLicense, toolLicenseUrl, toolVersionNumber, toolUrl, toolContact, gaId, authServerBaseUrl, authServerRealm);
        this.maxFileSize = maxFileSize;
    }

}
