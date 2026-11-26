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
package org.lifstools.jgoslin.webapp.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Time limits for parsing, {@code goslin.parser.*} (see application.properties).
 *
 * @param timeLimit longest time for one name, across all grammars tried for it; zero for no limit
 * @param requestTimeLimit longest time for all names of one validation request; zero for no limit
 * @param threads size of the parse pool; null or less than one for {@code max(2, available processors)}
 */
@ConfigurationProperties(prefix = "goslin.parser")
public record ParserProperties(@DefaultValue("20s") Duration timeLimit, @DefaultValue("120s") Duration requestTimeLimit,
        Integer threads) {

    public ParserProperties {
        if (timeLimit == null || timeLimit.isNegative()) {
            throw new IllegalArgumentException("goslin.parser.time-limit must be zero or positive: " + timeLimit);
        }
        if (requestTimeLimit == null || requestTimeLimit.isNegative()) {
            throw new IllegalArgumentException("goslin.parser.request-time-limit must be zero or positive: " + requestTimeLimit);
        }
        if (threads == null || threads < 1) {
            threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        }
    }

    /** The defaults: 20 s per name, 120 s per request, {@code max(2, available processors)} threads. */
    public static ParserProperties defaults() {
        return new ParserProperties(Duration.ofSeconds(20), Duration.ofSeconds(120), null);
    }
}
