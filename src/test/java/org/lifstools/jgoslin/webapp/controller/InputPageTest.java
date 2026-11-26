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
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * The input page: one text box, "Load example names", "Skip invalid names" and
 * "Harmonize", plus the Goslin logo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(profiles = {"test", "dev"})
@TestPropertySource(properties = {"jgoslin.maxItemsInRequest=2", "spring.servlet.multipart.max-file-size=2KB"})
public class InputPageTest {

    private static final String RESULT_LOCATION = ".*/result/[0-9a-f-]{36}";

    @Autowired
    private MockMvc mockMvc;

    private static Document html(MvcResult result) throws Exception {
        return Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private Document getHome(String url) throws Exception {
        return html(mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andReturn());
    }

    private static String textArea(Document doc) {
        Element textarea = doc.selectFirst("textarea#lipids[name=lipidNames]");
        assertThat(textarea).isNotNull();
        return textarea.wholeText();
    }

    @Test
    public void homeShowsEmptyTextAreaAndCheckedSkip() throws Exception {
        Document doc = getHome("/");
        assertThat(doc.selectFirst("h1").text()).isEqualTo("Harmonize lipid names");
        assertThat(textArea(doc)).isEmpty();
        Element skip = doc.selectFirst("input#skipInvalid[name=skipInvalid][type=checkbox]");
        assertThat(skip).isNotNull();
        assertThat(skip.hasAttr("checked")).isTrue();
        Element harmonize = doc.selectFirst("button#harmonize[type=submit]");
        assertThat(harmonize).isNotNull();
        assertThat(harmonize.text()).isEqualTo("Harmonize");
        // the rest of the DOM contract that later tasks rely on
        assertThat(doc.selectFirst("#lipid-count[aria-live=polite]")).isNotNull();
        assertThat(doc.selectFirst("a#load-example").attr("href")).endsWith("?example");
        Element upload = doc.selectFirst("form#upload-fallback");
        assertThat(upload).isNotNull();
        assertThat(upload.attr("action")).endsWith("/validatefile");
        assertThat(upload.attr("enctype")).isEqualTo("multipart/form-data");
        assertThat(upload.selectFirst("input#file[type=file][name=file]")).isNotNull();
    }

    @Test
    public void exampleFillsTextArea() throws Exception {
        String text = textArea(getHome("/?example"));
        assertThat(text).startsWith("12-HETE");
        assertThat(text.lines().filter(l -> !l.isBlank()).count()).isEqualTo(56);
    }

    @Test
    public void noNormalizeWordingOnHome() throws Exception {
        for (String url : new String[]{"/", "/?lang=de"}) {
            Document doc = getHome(url);
            assertThat(doc.body().text().toLowerCase(Locale.ROOT)).doesNotContain("normali");
            assertThat(doc.title().toLowerCase(Locale.ROOT)).doesNotContain("normali");
        }
        assertThat(getHome("/?lang=de").selectFirst("h1").text()).isEqualTo("Lipidnamen harmonisieren");
    }

    @Test
    public void validateRedirectsToResult() throws Exception {
        mockMvc.perform(post("/validate").param("lipidNames", "PC 32:0\nTG 52:2"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(RESULT_LOCATION)));
    }

    @Test
    public void tooManyNamesKeepsText() throws Exception {
        String text = "PC 32:0\nTG 52:2\nPE 34:1";
        Document doc = html(mockMvc.perform(post("/validate").param("lipidNames", text))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andReturn());
        assertThat(doc.select(".lifs-callout--error").text())
                .contains("You submitted 3 names, but up to 2 can be harmonized at once. Please split the list.");
        assertThat(textArea(doc)).isEqualTo(text);
        assertThat(doc.selectFirst("#lipid-count").text()).startsWith("3 names");
    }

    @Test
    public void skipUncheckedListsFailuresByLine() throws Exception {
        String text = "PC 32:0\n\nPC(18:2";
        Document doc = html(mockMvc.perform(post("/validate")
                .param("lipidNames", text)
                .param("_skipInvalid", "on")) // an unchecked box, as a browser posts it
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andReturn());
        Element callout = doc.selectFirst(".lifs-callout--error");
        assertThat(callout).isNotNull();
        assertThat(callout.text()).contains("line 3").contains("PC(18:2").doesNotContain("line 1");
        assertThat(textArea(doc)).isEqualTo(text);
        assertThat(doc.selectFirst("input#skipInvalid").hasAttr("checked")).isFalse();
    }

    @Test
    public void tooLongTextIsNotEchoed() throws Exception {
        // The text cap is min(2KB, 2 names x 302) = 604 bytes.
        String text = "PC 32:0\n".repeat(100);
        Document doc = html(mockMvc.perform(post("/validate").param("lipidNames", text))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andReturn());
        assertThat(doc.select(".lifs-callout--error").text())
                .contains("The text is larger than 604 B. Please submit fewer names at once.");
        assertThat(doc.selectFirst("input#file").attr("data-max-bytes")).isEqualTo("604");
        assertThat(textArea(doc)).isEmpty();
    }

    @Test
    public void tooLongLineIsNamed() throws Exception {
        String text = "PC 32:0\n" + "C".repeat(301);
        Document doc = html(mockMvc.perform(post("/validate").param("lipidNames", text))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andReturn());
        assertThat(doc.select(".lifs-callout--error").text())
                .contains("Line 2 is longer than 300 characters and cannot be a lipid name.");
        assertThat(textArea(doc)).isEqualTo(text);
        assertThat(doc.selectFirst("#lipid-count").text()).startsWith("2 names");
    }

    @Test
    public void fileFallbackRedirectsToResult() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "names.txt", "text/plain",
                "PC 32:0\n".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/validatefile").file(file))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(RESULT_LOCATION)));
    }

    @Test
    public void logoIsDecorativeInlineSvg() throws Exception {
        Document doc = getHome("/");
        Element logo = doc.selectFirst(".goslin-logo");
        assertThat(logo).isNotNull();
        assertThat(logo.selectFirst("svg[role=img][aria-label=Goslin logo]")).isNotNull();
        assertThat(logo.selectFirst("svg .g-head")).isNotNull();
        assertThat(logo.selectFirst("svg .g-head .g-eye")).isNotNull();
        assertThat(logo.hasAttr("tabindex")).isFalse();
        assertThat(logo.select("[tabindex]")).isEmpty();
        assertThat(logo.select("a, button")).isEmpty();
        assertThat(logo.selectFirst(".g-bubble[aria-hidden=true]")).isNotNull();
    }

    @Test
    public void trackingScriptHasSiteId() throws Exception {
        Document doc = getHome("/");
        assertThat(doc.selectFirst("script[data-lifs-tracking][data-site-id]")).isNotNull();
    }

    @Test
    public void documentationStillRenders() throws Exception {
        mockMvc.perform(get("/documentation"))
                .andExpect(status().isOk())
                .andExpect(view().name("documentation"));
    }
}
