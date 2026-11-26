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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * The result page: failed names first with line numbers and hints, re-run,
 * remove, the TSV download and the harmonized table.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(profiles = {"test", "dev"})
public class ResultPageTest {

    private static final Pattern RESULT_ID = Pattern.compile("/result/([0-9a-f-]{36})$");

    @Autowired
    private MockMvc mockMvc;

    private static Document html(MvcResult result) throws Exception {
        return Jsoup.parse(body(result));
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** Posts the names with "Skip invalid names" checked and returns the new result id. */
    private String harmonize(String text) throws Exception {
        MvcResult result = mockMvc.perform(post("/validate").param("lipidNames", text))
                .andExpect(status().isSeeOther())
                .andReturn();
        Matcher m = RESULT_ID.matcher(result.getResponse().getHeader("Location"));
        assertThat(m.find()).as("Location %s", result.getResponse().getHeader("Location")).isTrue();
        return m.group(1);
    }

    private MvcResult page(String url) throws Exception {
        return mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(view().name("result"))
                .andReturn();
    }

    private Document result(String id) throws Exception {
        return html(page("/result/" + id));
    }

    private String perform303(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isSeeOther())
                .andReturn().getResponse().getHeader("Location");
    }

    private static Element row(Document doc, int line) {
        return doc.selectFirst("table#results tbody tr.result-row[data-line=" + line + "]");
    }

    private static void assertNotFoundPage(MvcResult result) throws Exception {
        Document doc = html(result);
        assertThat(doc.select(".lifs-callout").text())
                .contains("Result not found or expired. Results are kept for 7 days.");
        Element home = doc.selectFirst("main a[href=/]");
        assertThat(home).isNotNull();
    }

    @Test
    public void failedNamesComeFirstWithLineNumbers() throws Exception {
        Document doc = result(harmonize("Cer(d18:1/18:0)\n\nPC(18:2-20:4"));
        Element fix = doc.selectFirst("section#fix");
        Element table = doc.selectFirst("table#results");
        assertThat(fix).isNotNull();
        assertThat(table).isNotNull();
        assertThat(doc.getAllElements().indexOf(fix)).isLessThan(doc.getAllElements().indexOf(table));
        Element fixRow = fix.selectFirst(".fix__row[data-line=3]");
        assertThat(fixRow).isNotNull();
        assertThat(fixRow.text()).contains("line 3");
        assertThat(fixRow.selectFirst("input[name=edit[3]]").val()).isEqualTo("PC(18:2-20:4");
        assertThat(fixRow.selectFirst(".trace mark")).isNotNull();
        assertThat(fixRow.text()).contains("A closing bracket is missing.");
        assertThat(fixRow.selectFirst("details.fix__original")).isNotNull();
        Element remove = fixRow.selectFirst("button[name=line]");
        assertThat(remove.val()).isEqualTo("3");
        assertThat(remove.attr("formaction")).endsWith("/remove");
        assertThat(doc.selectFirst("button#rerun")).isNotNull();
        assertThat(fix.select(".fix__row")).hasSize(1);
    }

    @Test
    public void enterInACorrectionReruns() throws Exception {
        Document doc = result(harmonize("PC 32:0\nPC(18:2-20:4"));
        Element form = doc.selectFirst("form:has(section#fix)");
        assertThat(form).isNotNull();
        assertThat(form.attr("action")).endsWith("/rerun");
        // Implicit submission (Enter) uses the form's first submit button.
        Element first = form.selectFirst("button:not([type]), button[type=submit], input[type=submit]");
        assertThat(first.id()).isEqualTo("rerun-default");
        assertThat(first.hasClass("visually-hidden")).isTrue();
        assertThat(first.attr("tabindex")).isEqualTo("-1");
        assertThat(first.attr("aria-hidden")).isEqualTo("true");
        assertThat(first.hasAttr("formaction")).isFalse();
    }

    @Test
    public void hintNumbersAreOneBased() throws Exception {
        // Parsing stops at 0-based position 17, the "x": the hint says character 18.
        Document doc = result(harmonize("PC 32:0\nTG 16:0_18:1_18:2x"));
        Element fixRow = doc.selectFirst(".fix__row[data-line=2]");
        assertThat(fixRow.text()).contains("Not a valid name from character 18 on.");
        assertThat(fixRow.selectFirst(".trace mark").text()).isEqualTo("x");
        assertThat(fixRow.selectFirst(".trace").text()).isEqualTo("TG 16:0_18:1_18:2x");
    }

    @Test
    public void tableRowsCarryLineNumbers() throws Exception {
        Document doc = result(harmonize("Cer(d18:1/18:0)\n\nPC(18:2-20:4"));
        Element row = row(doc, 1);
        assertThat(row).isNotNull();
        assertThat(row.selectFirst("td").text()).isEqualTo("1");
        assertThat(row.attr("data-level")).isNotEmpty();
        assertThat(row.attr("data-category")).isEqualTo("SP");
        assertThat(row.attr("data-search")).contains("Cer(d18:1/18:0)");
        Element detail = row.nextElementSibling();
        assertThat(detail.hasClass("detail")).isTrue();
        assertThat(detail.selectFirst("details")).isNotNull();
        assertThat(row(doc, 3)).isNull();
    }

    @Test
    public void rerunRedirectsWithOutcome() throws Exception {
        String id = harmonize("Cer(d18:1/18:0)\n\nPC(18:2-20:4");
        String location = perform303(post("/result/" + id + "/rerun").param("edit[3]", "PC(18:2-20:4)"));
        assertThat(location).endsWith("/result/" + id + "?rerun=1,0");
    }

    @Test
    public void outcomeMessageIsShown() throws Exception {
        String id = harmonize("Cer(d18:1/18:0)\n\nPC(18:2-20:4");
        Document doc = html(page("/result/" + id + "?rerun=1,0"));
        assertThat(doc.select(".lifs-callout--ok").text())
                .contains("1 corrected name was harmonized and added to the table.");
        doc = html(page("/result/" + id + "?rerun=2,1"));
        assertThat(doc.select(".lifs-callout--ok").text())
                .contains("2 corrected names were harmonized and added to the table. 1 name still needs attention.");
        // invalid values are ignored
        for (String bad : new String[]{"abc", "1", "-1,0", "1,2,3", "99999999999,0"}) {
            doc = html(page("/result/" + id + "?rerun=" + bad));
            assertThat(doc.select(".lifs-callout--ok").text()).as(bad).doesNotContain("harmonized and added");
        }
        doc = html(page("/result/" + id + "?rerun=2,3&lang=de"));
        assertThat(doc.select(".lifs-callout--ok").text())
                .contains("2 korrigierte Namen wurden harmonisiert und in die Tabelle aufgenommen.");
        page("/result/" + id + "?lang=en");
    }

    @Test
    public void correctedRowShowsChipAndOriginal() throws Exception {
        String id = harmonize("Cer(d18:1/18:0)\n\nPC(18:2-20:4");
        perform303(post("/result/" + id + "/rerun").param("edit[3]", "PC(18:2-20:4)"));
        Document doc = result(id);
        Element row = row(doc, 3);
        assertThat(row).isNotNull();
        assertThat(row.selectFirst(".chip-corrected")).isNotNull();
        Element original = row.selectFirst("s");
        assertThat(original).isNotNull();
        assertThat(original.text()).isEqualTo("PC(18:2-20:4");
        assertThat(doc.selectFirst("section#fix")).isNull();
        // line order: the corrected name goes back to its place
        assertThat(doc.select("table#results tr.result-row").eachAttr("data-line")).containsExactly("1", "3");
    }

    @Test
    public void removeDropsRow() throws Exception {
        String id = harmonize("Cer(d18:1/18:0)\n\nPC(18:2-20:4");
        String location = perform303(post("/result/" + id + "/remove").param("line", "3"));
        assertThat(location).endsWith("/result/" + id);
        Document doc = result(id);
        assertThat(doc.select("[data-line=3]")).isEmpty();
        assertThat(doc.select("[name=edit[3]]")).isEmpty();
        assertThat(row(doc, 1)).isNotNull();
    }

    @Test
    public void removeKeepsEditsInOtherRows() throws Exception {
        String id = harmonize("Cer(d18:1/18:0)\nPC 32:0x\nPC(18:2-20:4");
        perform303(post("/result/" + id + "/remove").param("line", "3").param("edit[2]", "PC 32:0"));
        Document doc = result(id);
        assertThat(row(doc, 2)).isNotNull();
        assertThat(row(doc, 2).selectFirst(".chip-corrected")).isNotNull();
        assertThat(doc.select("[data-line=3]")).isEmpty();
        assertThat(doc.selectFirst("section#fix")).isNull();
    }

    @Test
    public void unknownResultIs404() throws Exception {
        MvcResult result = mockMvc.perform(get("/result/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("result"))
                .andReturn();
        assertNotFoundPage(result);
    }

    @Test
    public void rerunOnExpiredResultShowsNotFound() throws Exception {
        String id = UUID.randomUUID().toString();
        assertNotFoundPage(mockMvc.perform(post("/result/" + id + "/rerun").param("edit[3]", "PC 32:0"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("result"))
                .andReturn());
        assertNotFoundPage(mockMvc.perform(post("/result/" + id + "/remove").param("line", "3"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("result"))
                .andReturn());
    }

    @Test
    public void nonUuidIdIs404() throws Exception {
        assertNotFoundPage(mockMvc.perform(get("/result/not-a-uuid"))
                .andExpect(status().isNotFound())
                .andReturn());
        assertNotFoundPage(mockMvc.perform(post("/result/not-a-uuid/rerun").param("edit[1]", "PC 32:0"))
                .andExpect(status().isNotFound())
                .andReturn());
        assertNotFoundPage(mockMvc.perform(post("/result/not-a-uuid/remove").param("line", "1"))
                .andExpect(status().isNotFound())
                .andReturn());
    }

    @Test
    public void downloadButtonsPointToTsv() throws Exception {
        String id = harmonize("Cer(d18:1/18:0)\n\nPC(18:2-20:4");
        Document doc = result(id);
        for (String selector : new String[]{"a#download-tsv", "a#download-tsv-bottom"}) {
            Element a = doc.selectFirst(selector);
            assertThat(a).as(selector).isNotNull();
            assertThat(a.attr("href")).endsWith("/download/" + id + "/OUTPUT_TSV_FILE");
            assertThat(a.hasClass("lifs-btn--filled")).as(selector).isTrue();
        }
    }

    @Test
    public void downloadHasLineColumn() throws Exception {
        String id = harmonize("Cer(d18:1/18:0)\n\nPC(18:2-20:4");
        String href = result(id).selectFirst("a#download-tsv").attr("href");
        MvcResult tsv = mockMvc.perform(get(href)).andExpect(status().isOk()).andReturn();
        assertThat(body(tsv)).startsWith("Line\tNormalized Name");
        assertThat(tsv.getResponse().getHeader("Content-Disposition"))
                .contains("goslin-result-" + id.substring(0, 8) + ".tsv");
    }

    @Test
    public void downloadUnknownIs404() throws Exception {
        mockMvc.perform(get("/download/" + UUID.randomUUID() + "/OUTPUT_TSV_FILE"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/download/not-a-uuid/OUTPUT_TSV_FILE"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void namesAreEscapedEverywhere() throws Exception {
        String id = harmonize("<script>alert(1)</script>\n<b>PC 32:0");
        String body = body(page("/result/" + id));
        assertThat(body).doesNotContain("<script>alert(1)").doesNotContain("<b>PC");
        perform303(post("/result/" + id + "/rerun").param("edit[2]", "PC 32:0"));
        body = body(page("/result/" + id));
        assertThat(body).doesNotContain("<script>alert(1)").doesNotContain("<b>PC");
        Document doc = Jsoup.parse(body);
        assertThat(row(doc, 2)).isNotNull();
        assertThat(row(doc, 2).selectFirst("s").text()).isEqualTo("<b>PC 32:0");
        assertThat(body).contains("<s>&lt;b&gt;PC 32:0</s>");
        assertThat(body).containsPattern("data-search=\"[^\"]*&lt;b&gt;PC 32:0");
    }

    @Test
    public void tooLongCorrectionIsIgnored() throws Exception {
        String id = harmonize("Cer(d18:1/18:0)\nPC(18:2-20:4");
        String tooLong = "PC " + "1".repeat(298); // 301 characters
        String location = perform303(post("/result/" + id + "/rerun").param("edit[2]", "  " + tooLong + "  "));
        assertThat(location).contains("tooLong=1,2");
        Document doc = html(page(location.substring(location.indexOf("/result/"))));
        assertThat(doc.select(".lifs-callout--error").text())
                .contains("The correction on line 2 is longer than 300 characters and was not re-run.");
        assertThat(doc.selectFirst("input[name=edit[2]]").val()).isEqualTo("PC(18:2-20:4");
        assertThat(doc.selectFirst("input[name=edit[2]]").attr("maxlength")).isEqualTo("300");
        // exactly 300 characters after normalising is accepted (and fails to parse, but is saved);
        // a name that fails at its first character keeps the parse fast
        String fits = "\u00dc".repeat(300);
        location = perform303(post("/result/" + id + "/rerun").param("edit[2]", " " + fits + " "));
        assertThat(location).doesNotContain("tooLong");
        assertThat(result(id).selectFirst("input[name=edit[2]]").val()).isEqualTo(fits);
    }

    @Test
    public void noNormalizeWordingOnResult() throws Exception {
        String id = harmonize("Cer(d18:1/18:0)\n\nPC(18:2-20:4");
        for (String lang : new String[]{"de", "en"}) {
            Document doc = result(id + "?lang=" + lang);
            assertThat(doc.body().text().toLowerCase(Locale.ROOT)).doesNotContain("normaliz").doesNotContain("normalis");
            assertThat(doc.title().toLowerCase(Locale.ROOT)).doesNotContain("normali");
            assertThat(doc.body().text()).doesNotContain("Normalized Name");
        }
        Document doc = result(id);
        assertThat(doc.selectFirst("h1").text()).isEqualTo("Harmonized lipid names");
        assertThat(doc.selectFirst("table#results thead").text()).contains("Harmonized name");
    }

    @Test
    public void filtersAndPagerArePresent() throws Exception {
        Document doc = result(harmonize("Cer(d18:1/18:0)\nPC 32:0\nTG 52:2"));
        assertThat(doc.selectFirst("input#filter-text")).isNotNull();
        Element level = doc.selectFirst("select#filter-level");
        Element category = doc.selectFirst("select#filter-category");
        assertThat(level.select("option").eachAttr("value")).contains("");
        assertThat(category.select("option").eachAttr("value")).contains("", "SP", "GP", "GL");
        for (Element row : doc.select("tr.result-row")) {
            assertThat(level.select("option").eachAttr("value")).contains(row.attr("data-level"));
        }
        assertThat(doc.selectFirst("#pager")).isNotNull();
        assertThat(doc.select("table#results th[data-sort]")).isNotEmpty();
        assertThat(doc.selectFirst(".lifs-callout--ok").text()).contains("All 3 names were harmonized.");
    }
}
