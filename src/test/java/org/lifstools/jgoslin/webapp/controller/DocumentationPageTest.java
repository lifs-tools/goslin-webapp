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
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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

    private static Element row(Document doc, String firstCell) {
        return doc.select("#databases table.db-overview tbody tr").stream()
                .filter(r -> r.child(0).text().equals(firstCell)).findFirst().orElseThrow();
    }

    @Test
    public void databasesSectionListsThreeDatabases() throws Exception {
        Document doc = documentation();
        Element section = doc.getElementById("databases");
        assertThat(section).isNotNull();
        assertThat(doc.select("nav#toc a[href=#databases]")).hasSize(1);
        Elements rows = doc.select("#databases table.db-overview tbody tr");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).child(0).text()).isEqualTo("LIPID MAPS");
        assertThat(rows.get(1).child(0).text()).isEqualTo("SwissLipids");
        assertThat(rows.get(2).child(0).text()).isEqualTo("ChEBI");
        assertThat(row(doc, "LIPID MAPS").text()).contains("28,942", "26,832", "3 March 2023");
        assertThat(row(doc, "SwissLipids").text()).contains("777,956", "early 2023");
        assertThat(row(doc, "ChEBI").text()).contains("18 January 2024");
        assertThat(section.text()).contains("computationally generated COMPDB", "combinatorially", "Manually curated");
        assertThat(section.select("a[href$=issues/129]")).isNotEmpty();
    }

    @Test
    public void coverageChartHasOneBarPerDatabase() throws Exception {
        Document doc = documentation();
        Elements bars = doc.select(".coverage-bar");
        assertThat(bars).hasSize(3);
        bars.forEach(b -> assertThat(b.select("[data-category]")).isNotEmpty());
        Element gp = bars.get(1).selectFirst("[data-category=GP]");
        assertThat(gp).isNotNull();
        assertThat(gp.attr("title")).contains("%");
    }

    @Test
    public void coverageTableListsEveryCategory() throws Exception {
        Document doc = documentation();
        Element table = doc.selectFirst("table.db-categories");
        assertThat(table).isNotNull();
        assertThat(doc.getElementById("db-categories-title").text()).isEqualTo("Entries per lipid category");
        for (String label : new String[]{"Fatty acyls (FA)", "Glycerolipids (GL)", "Glycerophospholipids (GP)", "Sphingolipids (SP)", "Sterol lipids (ST)", "Not classified"}) {
            assertThat(table.select("tbody tr th").eachText()).contains(label);
        }
        Element fa = table.select("tbody tr").stream().filter(r -> r.child(0).text().equals("Fatty acyls (FA)")).findFirst().orElseThrow();
        String swiss = fa.child(2).text();
        assertThat(swiss).isNotBlank().doesNotStartWith("0 ");
        assertThat(swiss).isNotEqualTo("0");
    }

    @Test
    public void classTableShowsCoverageColumns() throws Exception {
        Document doc = documentation();
        assertThat(doc.select("table#lipid-classes thead th").eachText()).contains("LIPID MAPS", "SwissLipids", "ChEBI");
        Elements heads = doc.select("table#lipid-classes thead th");
        int swissIdx = heads.eachText().indexOf("SwissLipids");
        Element pc = doc.select("table#lipid-classes tbody tr").stream()
                .filter(r -> r.child(1).text().equals("Glycerophosphocholines [GP01]")).findFirst().orElseThrow();
        assertThat(Long.parseLong(pc.child(swissIdx).text().replace(",", ""))).isGreaterThan(1000);
    }

    @Test
    public void classTableShowsNoneForGaps() throws Exception {
        Document doc = documentation();
        assertThat(doc.select("table#lipid-classes tbody .db-none")).isNotEmpty();
        assertThat(doc.select("input#filter-classes").attr("data-filter-target")).isEqualTo("#lipid-classes");
    }
}
