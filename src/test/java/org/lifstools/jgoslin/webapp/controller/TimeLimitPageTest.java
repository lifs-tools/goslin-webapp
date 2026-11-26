package org.lifstools.jgoslin.webapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Names that ran out of time on the result page. A run of 200 digits takes jgoslin seconds per grammar; the limits
 * here are a fraction of a second, and one parse thread, so that names wait for it.
 */
@SpringBootTest(properties = {"goslin.parser.time-limit=200ms", "goslin.parser.request-time-limit=500ms",
    "goslin.parser.threads=1"})
@AutoConfigureMockMvc
@ActiveProfiles(profiles = {"test", "dev"})
class TimeLimitPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void namesOverTheTimeLimitsAreListedWithAHint() throws Exception {
        String junk = "1".repeat(200);
        String text = "PC 32:0\n" + junk + "\nTG 52:2\nPC 34:1\nPC 36:2\nPC 38:4";
        String location = mockMvc.perform(post("/validate").param("lipidNames", text))
                .andExpect(status().isSeeOther())
                .andReturn().getResponse().getHeader("Location");
        // the view must come from the cache: parsing again would run into the limits again, differently
        Document doc = Jsoup.parse(mockMvc.perform(get(location)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(doc.selectFirst("tr.result-row[data-line=1]")).isNotNull();
        Element slow = doc.selectFirst(".fix__row[data-line=2]");
        assertThat(slow).isNotNull();
        assertThat(slow.text()).contains("Goslin stopped parsing this name after 0.2 seconds. "
                + "Names that take this long are almost never valid lipid names.");
        assertThat(slow.selectFirst(".trace mark")).isNull();
        assertThat(slow.selectFirst(".trace").text()).isEqualTo(junk);

        // line 3 waited for the one thread, which the runaway parse of line 2 still holds: it was never parsed
        Element waited = doc.selectFirst(".fix__row[data-line=3]");
        assertThat(waited).isNotNull();
        assertThat(waited.text()).contains("Goslin could not start parsing this name within 0.2 seconds because the "
                + "server was busy. Please try again.");
        assertThat(waited.text()).doesNotContain("stopped parsing");
        assertThat(waited.selectFirst(".trace mark")).isNull();

        Document de = Jsoup.parse(mockMvc.perform(get(location + "?lang=de")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(de.selectFirst(".fix__row[data-line=2]").text())
                .contains("Goslin hat das Lesen dieses Namens nach 0,2 Sekunden abgebrochen.");
        assertThat(de.selectFirst(".fix__row[data-line=3]").text())
                .contains("Goslin konnte das Lesen dieses Namens nicht innerhalb von 0,2 Sekunden beginnen, weil der "
                        + "Server ausgelastet war.");

        Element last = doc.selectFirst(".fix__row[data-line=6]");
        assertThat(last).isNotNull();
        assertThat(last.text()).contains("Not processed: the time limit for this request was reached.");
        assertThat(last.selectFirst(".trace mark")).isNull();
        assertThat(last.selectFirst("details.fix__original").text())
                .contains("Not processed: the time limit for this request was reached.");
    }
}
