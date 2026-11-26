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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The re-run form posts one parameter per failed name; real Tomcat must accept more than its default of 1,000.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ResultPageParamsIT {

    private static final int NAMES = 1100;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private HttpResponse<String> postForm(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Test
    public void rerunWithElevenHundredFailedNames() throws Exception {
        // Unknown head groups fail fast.
        String names = IntStream.rangeClosed(1, NAMES).mapToObj(i -> "Xyz" + i).collect(Collectors.joining("\n"));
        HttpResponse<String> created = postForm("/goslin/validate", "lipidNames=" + encode(names));
        assertThat(created.statusCode()).isEqualTo(303);
        String location = created.headers().firstValue("Location").orElseThrow();
        assertThat(location).matches("/goslin/result/[0-9a-f-]{36}");

        String edits = IntStream.rangeClosed(1, NAMES)
                .mapToObj(i -> encode("edit[" + i + "]") + "=" + encode("PC 32:0"))
                .collect(Collectors.joining("&"));
        HttpResponse<String> rerun = postForm(location + "/rerun", edits);
        assertThat(rerun.statusCode()).as(rerun.body()).isEqualTo(303);
        assertThat(rerun.headers().firstValue("Location").orElseThrow()).endsWith(location + "?rerun=" + NAMES + ",0");
    }
}
