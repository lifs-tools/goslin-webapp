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
package de.isas.lifs.palinom.webapp.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;

/**
 *
 * @author nilshoffmann
 */
@Data
public class AppInfo {

    @Value("${app.build.date}")
    private String buildDate;

    @Value("${app.scm.commit.id}")
    private String scmCommitId;

    @Value("${app.scm.branch}")
    private String scmBranch;

    @Value("${app.version.number}")
    private String versionNumber;

    @Value("${ga.id}")
    private String gaId;
    
    @Value("${lifs.user.url}")
    private String lifsUserUrl;
    
    public String getBuildDate() {
        if(buildDate!=null && !buildDate.isEmpty()) {
            LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(buildDate)), ZoneId.of("Z"));
            return ldt.toString();
        }
        return "<unknown date>";
    }
}
