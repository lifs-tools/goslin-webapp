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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The About page: citation, news, privacy and version.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(profiles = {"test", "dev"})
public class AboutPageTest {

    @Autowired
    private MockMvc mockMvc;

    private Document page(String path) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    public void aboutUsesLifsLayoutAndToc() throws Exception {
        Document doc = page("/about");
        assertThat(doc.select("header .lifs-bar")).hasSize(1);
        assertThat(doc.select("footer.lifs-footer")).hasSize(1);
        assertThat(doc.select("nav#toc a[href^=#]").size()).isGreaterThanOrEqualTo(6);
        doc.select("nav#toc a[href^=#]").forEach(a -> assertThat(doc.getElementById(a.attr("href").substring(1))).as(a.attr("href")).isNotNull());
        assertThat(doc.getElementById("about-goslin")).isNotNull();
    }

    @Test
    public void aboutHasMovedSections() throws Exception {
        Document about = page("/about");
        Document documentation = page("/documentation");
        for (String id : new String[]{"citing", "news", "contributing", "acknowledgements"}) {
            assertThat(about.getElementById(id)).as("/about #" + id).isNotNull();
            assertThat(documentation.getElementById(id)).as("/documentation #" + id).isNull();
        }
        assertThat(documentation.getElementById("lipid-classes")).isNotNull();
    }

    @Test
    public void privacyMentionsRetentionAndServerSideCounting() throws Exception {
        String text = page("/about").getElementById("privacy").text();
        assertThat(text).contains("7 days", "anyone", "Matomo", "never", "lipid names", "Do Not Track");
    }

    @Test
    public void versionShowsWebappAndJgoslin() throws Exception {
        String text = page("/about").getElementById("version").text();
        assertThat(text).contains("2.3.0-SNAPSHOT").contains("2.2.1");
    }

    @Test
    public void navHasAbout() throws Exception {
        Document doc = page("/");
        assertThat(doc.select("header .lifs-bar a[href$=/about]").size()).isGreaterThanOrEqualTo(1);
        Document about = page("/about");
        assertThat(about.select("header .lifs-bar a.lifs-bar__item--active[href$=/about]").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void homeCitationLinksToAbout() throws Exception {
        Document doc = page("/");
        assertThat(doc.select("#cite-title").first().parent().select("a[href$=/about#citing]")).hasSize(1);
        assertThat(doc.select("section.about a[href$=/about]")).hasSize(1);
    }
}
