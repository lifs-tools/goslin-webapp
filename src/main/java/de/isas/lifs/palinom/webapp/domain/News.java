/*
 * Copyright 2020 Leibniz Institut für Analytische Wissenschaften - ISAS e.V..
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

import java.net.URL;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;

/**
 *
 * @author nilshoffmann
 */
@Data
public class News {

    @Value("${title}")
    private String title;
    @Value("${date}")
    private String date;
    @Value("${link}")
    private URL link;
    @Value("${content}")
    private String content;

}
