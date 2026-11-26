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
package org.lifstools.jgoslin.webapp.controller;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lifstools.jgoslin.webapp.Application;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two replicas share the storage folder: a result created on one renders on the other.
 */
public class ResultPageReplicaIT {

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private static ConfigurableApplicationContext start(Path storage) {
        return new SpringApplicationBuilder(Application.class)
                .profiles("test")
                // arguments, not defaults: application.properties sets server.port
                .run("--server.port=0", "--storage.location=" + storage.toAbsolutePath());
    }

    private static String base(ConfigurableApplicationContext context) {
        return "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
    }

    private Document get(String url) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).as(url).isEqualTo(200);
        return Jsoup.parse(response.body());
    }

    private static List<String> lines(Document doc, String selector) {
        return doc.select(selector).eachAttr("data-line");
    }

    @Test
    public void resultCreatedOnOneReplicaRendersOnTheOther(@TempDir Path storage) throws Exception {
        try (ConfigurableApplicationContext a = start(storage); ConfigurableApplicationContext b = start(storage)) {
            String names = "Cer(d18:1/18:0)\n\nPC(18:2-20:4\nPC 32:0";
            HttpResponse<String> posted = http.send(HttpRequest.newBuilder(URI.create(base(a) + "/goslin/validate"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("lipidNames=" + URLEncoder.encode(names, StandardCharsets.UTF_8)))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(posted.statusCode()).isEqualTo(303);
            String location = posted.headers().firstValue("Location").orElseThrow();
            assertThat(location).matches("/goslin/result/[0-9a-f-]{36}");

            Document onA = get(base(a) + location);
            Document onB = get(base(b) + location);
            assertThat(lines(onB, "tr.result-row")).containsExactly("1", "4").isEqualTo(lines(onA, "tr.result-row"));
            assertThat(lines(onB, ".fix__row")).containsExactly("3").isEqualTo(lines(onA, ".fix__row"));
            assertThat(onB.selectFirst("input[name=edit[3]]").val()).isEqualTo("PC(18:2-20:4");
        }
    }
}
