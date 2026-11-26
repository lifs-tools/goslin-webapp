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
package org.lifstools.jgoslin.webapp.services;

import java.util.UUID;
import org.lifstools.lifs.webapps.common.service.AnalyticsTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Counts validations in Matomo, on the server, anonymously: once per submitted list of names (web form) and once
 * per REST call. Views of a result, re-runs, removals and the check before harmonizing are not counted.
 * <p>
 * Category and action are those of 2.2.x ({@code LipidNameValidationService}, {@code validate-with-grammars}), so
 * that the figures stay comparable.
 */
@Service
public class UsageCounter {

    private static final Logger log = LoggerFactory.getLogger(UsageCounter.class);

    static final String CATEGORY = "LipidNameValidationService";
    static final String ACTION = "validate-with-grammars";

    private final AnalyticsTracker tracker;

    public UsageCounter(AnalyticsTracker tracker) {
        this.tracker = tracker;
    }

    /** Counts one validation: a submitted list of names, or a REST call. */
    public void countValidation() {
        try {
            tracker.count(UUID.randomUUID(), CATEGORY, ACTION);
        } catch (RestClientException e) {
            // tracking must never break validation, e.g. while Matomo is unreachable
            log.warn("Could not send tracking data: {}", e.getMessage());
        }
    }
}
