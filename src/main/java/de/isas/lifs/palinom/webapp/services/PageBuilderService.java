/*
 * Copyright 2021 Leibniz Institut für Analytische Wissenschaften - ISAS e.V..
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
package de.isas.lifs.palinom.webapp.services;


import de.isas.lifs.palinom.webapp.domain.AppInfo;
import de.isas.lifs.palinom.webapp.domain.Page;
import java.security.Principal;
import java.util.Optional;
import java.util.stream.Collectors;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.keycloak.representations.AccessToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 *
 * @author nilshoffmann
 */
@Service
public class PageBuilderService {

    private final AppInfo appInfo;

    @Autowired
    public PageBuilderService(AppInfo appInfo) {
        this.appInfo = appInfo;
    }

    public Page createPage(String title) {
        return Page.builder().title(title).
                appVersion(appInfo.getVersionNumber()).
                authServerBaseUrl(appInfo.getAuthServerBaseUrl()).
                authServerRealm(appInfo.getAuthServerRealm()).
                author(appInfo.getToolAuthor()).
                buildDate(appInfo.getBuildDate()).
                description(appInfo.getToolDescription()).
                gaId(appInfo.getGaId()).
                scmBranch(appInfo.getScmBranch()).
                scmCommitId(appInfo.getScmCommitId()).
                scmUrl(appInfo.getScmUrl()).
                supportUrl(appInfo.getSupportUrl()).
                toolAuthor(appInfo.getToolAuthor()).
                toolContact(appInfo.getToolContact()).
                toolDescription(appInfo.getToolDescription()).
                toolLicense(appInfo.getToolLicense()).
                toolLicenseUrl(appInfo.getToolLicenseUrl()).
                toolTitle(appInfo.getToolTitle()).
                toolUrl(appInfo.getToolUrl()).
                toolVersionNumber(appInfo.getToolVersionNumber()).
                maxFileSize(appInfo.getMaxFileSize()).build();
    }

    public Page addPrincipalInfo(Page page, Optional<Principal> optionalPrincipal) {
        if (optionalPrincipal.isPresent()) {
            Principal principal = optionalPrincipal.get();
            if (principal instanceof KeycloakAuthenticationToken) {
                KeycloakAuthenticationToken keycloakAuthenticationToken = (KeycloakAuthenticationToken) principal;
                AccessToken accessToken = keycloakAuthenticationToken.getAccount().getKeycloakSecurityContext().getToken();
                page.setUserName(accessToken.getGivenName() + " " + accessToken.getFamilyName());
                page.setUserRoles(keycloakAuthenticationToken.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
            }
        }
        return page;
    }
}
