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

import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * The documentation page and the error page in the LIFS design.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(profiles = {"test", "dev"})
public class DocumentationPageTest {

    @Autowired
    private MockMvc mockMvc;

    private static Document html(MvcResult result) throws Exception {
        return Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private Document documentation() throws Exception {
        return html(mockMvc.perform(get("/documentation")).andExpect(status().isOk()).andReturn());
    }

    @Test
    public void documentationUsesLifsLayout() throws Exception {
        Document doc = documentation();
        assertThat(doc.select("header .lifs-bar")).hasSize(1);
        assertThat(doc.select("footer.lifs-footer")).hasSize(1);
        assertThat(doc.select("nav#toc a[href^=#]").size()).isGreaterThanOrEqualTo(4);
        assertThat(doc.getElementById("citing")).isNull();
        assertThat(doc.getElementById("lipid-classes")).isNotNull();
        // no Bootstrap-era scripts or markup remain
        assertThat(doc.select("script[src*=jquery], script[src*=bootstrap], script[src*=app.js], link[href*=bootstrap], link[href*=site.css]")).isEmpty();
        assertThat(doc.select(".oi, .card-body, [data-toggle]")).isEmpty();
        // every toc link points at an element of the page
        doc.select("nav#toc a[href^=#]").forEach(a -> assertThat(doc.getElementById(a.attr("href").substring(1))).as(a.attr("href")).isNotNull());
    }

    @Test
    public void documentationIncludesTrackingScript() throws Exception {
        assertThat(documentation().selectFirst("script[data-lifs-tracking][data-site-id]")).isNotNull();
    }

    @Test
    public void lipidClassTableHasFilter() throws Exception {
        Document doc = documentation();
        assertThat(doc.select("table#lipid-classes tbody tr").size()).isGreaterThan(100);
        assertThat(doc.select("input#filter-classes")).hasSize(1);
        assertThat(doc.select("input#filter-classes").attr("data-filter-target")).isEqualTo("#lipid-classes");
    }

    @Test
    public void manualDescribesNewFlow() throws Exception {
        String text = documentation().getElementById("interactive-usage").text();
        assertThat(text).contains("Correct these names", "Load example names", "Skip invalid names", "7 days", "Corrected Name");
        assertThat(text).doesNotContain("Lipid File Input").doesNotContain("CTRL+V");
    }

    @Test
    public void errorPageUsesLifsLayout() throws Exception {
        MvcResult result = mockMvc.perform(get("/error")
                .requestAttr("jakarta.servlet.error.status_code", 404)
                .requestAttr("jakarta.servlet.error.request_uri", "/x"))
                .andExpect(view().name("error"))
                .andReturn();
        Document doc = html(result);
        assertThat(doc.select("header .lifs-bar")).hasSize(1);
        assertThat(doc.select("footer.lifs-footer")).hasSize(1);
        assertThat(doc.select("[data-testid=error-status]").text()).contains("404");
    }

    @Test
    public void noNormalizeWordingInManual() throws Exception {
        assertThat(documentation().getElementById("interactive-usage").text()).doesNotContain("Normaliz");
    }
}
