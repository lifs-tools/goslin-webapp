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
import java.util.List;
import org.jsoup.Jsoup;
import org.lifstools.jgoslin.webapp.domain.AppInfo;
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
    @Autowired
    private AppInfo appInfo;

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
        assertThat(text).contains("7 days", "anyone", "Matomo", "never", "lipid names", "Do Not Track", "site id", "regardless of your choice");
    }

    @Test
    public void privacyIsExact() throws Exception {
        String text = page("/about").getElementById("privacy").text();
        // a result address also lets you change the result
        assertThat(text).contains("anyone who has the address can open the result and also correct or remove names in it");
        // Deny is not remembered (lifs-tracking.js 2.0.1): the banner comes back on the next page
        assertThat(text).doesNotContain("asks for this once");
        assertThat(text).contains("The consent banner asks until you accept; if you decline, nothing is counted, "
                + "but the banner asks again on the next page.");
        // a page view includes the page address, also that of a result
        assertThat(text).contains("A page view includes the address of the page, so the address of a result you open "
                + "is recorded in Matomo as well.");
        // what the server counts
        assertThat(text).contains("the server counts each submitted list of names and each validation request to "
                + "the REST API in Matomo, anonymously");
    }

    @Test
    public void privacyIsExactInGerman() throws Exception {
        String text = page("/about?lang=de").getElementById("privacy").text();
        assertThat(text).contains("kann das Ergebnis \u00f6ffnen und darin auch Namen korrigieren oder entfernen");
        assertThat(text).doesNotContain("fragt einmal danach");
        assertThat(text).contains("Der Einwilligungshinweis fragt, bis Sie zustimmen");
        assertThat(text).contains("Ein Seitenaufruf enth\u00e4lt die Adresse der Seite");
        assertThat(text).contains("jede eingereichte Namensliste und jede Validierungsanfrage an die REST-API");
    }

    @Test
    public void newsListsEveryReleaseNewestFirst() throws Exception {
        List<String> dates = page("/about").select("#news .news__date").eachText();
        assertThat(dates).hasSize(16);
        assertThat(dates.stream().map(d -> d.substring(0, d.indexOf(' '))).toList()).containsExactly(
                "v2.3.0", "v2.2.2", "v2.2.1", "v2.2.0", "v2.1.1", "v2.1.0", "v2.0.2", "v2.0.1", "v2.0.0",
                "v1.1.8", "v1.1.7", "v1.1.5", "v1.1.3", "v1.1.2", "v1.1.0", "v1.0.18");
    }

    @Test
    public void releaseNoteListsTheRestChanges() throws Exception {
        String note = page("/about").select("#news .news__entry").first().text();
        assertThat(note).doesNotContain("The REST API is unchanged");
        assertThat(note).contains("300 characters", "400", "problem detail", "Non-breaking spaces", "lipidName",
                "OpenAPI 3.1", "time limit", "German translation is partial");
    }

    @Test
    public void citationHeadingMatchesTocEntry() throws Exception {
        Document doc = page("/about");
        assertThat(doc.select("nav#toc a[href=#citing]").text()).isEqualTo(doc.getElementById("citing-title").text());
    }

    @Test
    public void versionShowsWebappAndJgoslin() throws Exception {
        String text = page("/about").getElementById("version").text();
        assertThat(appInfo.getVersionNumber()).isNotBlank();
        assertThat(appInfo.getToolVersionNumber()).isNotBlank();
        assertThat(text).contains(appInfo.getVersionNumber()).contains(appInfo.getToolVersionNumber());
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
        assertThat(doc.select("section.about a[href$=/about]").text()).isEqualTo("More about Goslin");
    }
}
