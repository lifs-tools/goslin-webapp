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
package de.isas.lifs.palinom.webapp.config;

import de.isas.lifs.palinom.webapp.domain.News;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 *
 * @author nilshoffmann
 */
@Configuration
@PropertySource(name = "news", value = "classpath:news.properties", ignoreResourceNotFound = false)
@ConfigurationProperties(prefix = "news")
public class NewsPropertyConfig {

    private List<News> news = new ArrayList<>();

    /**
     * Returns the news in reverse order.
     *
     * @return the news.
     */
    public List<News> getNews() {
        int num = news.size() - 1;
        return IntStream.rangeClosed(0, num).mapToObj(i -> news.get(num - i)).collect(Collectors.toList());
    }

    public void setNews(List<News> news) {
        this.news = news;
    }

}
