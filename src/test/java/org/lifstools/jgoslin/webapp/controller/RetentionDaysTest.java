package org.lifstools.jgoslin.webapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The retention period is the configured minCleanupAge, in the About page and on the "not found" page. */
@SpringBootTest(properties = "minCleanupAge=1")
@AutoConfigureMockMvc
@ActiveProfiles(profiles = {"test", "dev"})
class RetentionDaysTest {

    @Autowired
    private MockMvc mockMvc;

    private String text(String path, int status) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path)).andExpect(status().is(status)).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8)).text();
    }

    @Test
    void oneDay() throws Exception {
        assertThat(text("/about", 200)).contains("are kept for 1 day and are then deleted");
        assertThat(text("/result/" + UUID.randomUUID(), 404)).contains("Results are kept for 1 day.");
        assertThat(text("/about?lang=de", 200)).contains("werden 1 Tag aufbewahrt");
        assertThat(text("/result/" + UUID.randomUUID() + "?lang=de", 404)).contains("Ergebnisse werden 1 Tag aufbewahrt.");
    }
}
