/*
 * Copyright 2026 Lipidomics Informatics for Life Sciences.
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

import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.boot.jackson.JacksonMixinModule;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI document describes the JSON that the REST API writes: springdoc
 * uses its own mappers, so they get the {@code @JacksonMixin}s too.
 */
@Configuration
public class OpenApiConfig {

    public OpenApiConfig(JacksonMixinModule jacksonMixinModule) {
        // springdoc's public API for extra modules; the registry is keyed by module id, so this is idempotent
        ObjectMapperProvider.registerModules(jacksonMixinModule);
    }
}
