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
package org.lifstools.jgoslin.webapp.domain;

import org.lifstools.lifs.webapps.common.domain.DefaultPage;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Nils Hoffmann &lt;nils.hoffmann@isas.de&gt;
 */
public class Page extends DefaultPage {

    private String userName = "";
    private List<String> userRoles = Collections.emptyList();

    private String systemMessage;
    private String systemMessageLevel;

    private String maxFileSize;

    public Page(String title, AppInfo appInfo) {
        this(
                title,
                appInfo.getVersionNumber(),
                appInfo.getGaId(),
                appInfo.getToolDescription(),
                appInfo.getToolAuthor(),
                appInfo.getBuildDate(),
                appInfo.getScmCommitId(),
                appInfo.getScmBranch(),
                appInfo.getScmUrl(),
                appInfo.getSupportUrl(),
                appInfo.getToolTitle(),
                appInfo.getToolDescription(),
                appInfo.getToolAuthor(),
                appInfo.getToolLicense(),
                appInfo.getToolLicenseUrl(),
                appInfo.getToolVersionNumber(),
                appInfo.getToolUrl(),
                appInfo.getToolContact(),
                appInfo.getAuthServerBaseUrl(),
                appInfo.getAuthServerRealm(),
                appInfo.getMaxFileSize()
        );
    }

    public Page(String title, String appVersion, String gaId, String description, String author, String buildDate, String scmCommitId, String scmBranch, String scmUrl, String supportUrl, String toolTitle, String toolDescription, String toolAuthor, String toolLicense, String toolLicenseUrl, String toolVersionNumber, String toolUrl, String toolContact, String authServerBaseUrl, String authServerRealm, String maxFileSize) {
        super(title, appVersion, gaId, description, author, buildDate, scmCommitId, scmBranch, scmUrl, supportUrl, toolTitle, toolDescription, toolAuthor, toolLicense, toolLicenseUrl, toolVersionNumber, toolUrl, toolContact, authServerBaseUrl, authServerRealm);
        this.maxFileSize = maxFileSize;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public List<String> getUserRoles() {
        return userRoles;
    }

    public void setUserRoles(List<String> userRoles) {
        this.userRoles = userRoles;
    }

    public String getSystemMessage() {
        return systemMessage;
    }

    public void setSystemMessage(String systemMessage) {
        this.systemMessage = systemMessage;
    }

    public String getSystemMessageLevel() {
        return systemMessageLevel;
    }

    public void setSystemMessageLevel(String systemMessageLevel) {
        this.systemMessageLevel = systemMessageLevel;
    }

    public String getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(String maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    @Override
    public String toString() {
        return "Page{" + "userName=" + userName + ", userRoles=" + userRoles + ", systemMessage=" + systemMessage + ", systemMessageLevel=" + systemMessageLevel + ", maxFileSize=" + maxFileSize + '}';
    }
    
}
