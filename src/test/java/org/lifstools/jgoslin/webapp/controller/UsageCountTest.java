package org.lifstools.jgoslin.webapp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lifstools.lifs.webapps.common.service.AnalyticsTracker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The server counts one validation per submitted list of names and one per REST call; never views, re-runs,
 * removals or the check before harmonizing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(profiles = {"test", "dev"})
class UsageCountTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsTracker tracker;

    @BeforeEach
    void reset() {
        clearInvocations(tracker);
    }

    private void verifyCounted(int times) {
        verify(tracker, times(times)).count(any(), eq("LipidNameValidationService"), eq("validate-with-grammars"));
    }

    @Test
    void submissionRerunAndViewsCountOnce() throws Exception {
        // "Skip invalid names" unchecked: the names are checked first, then harmonized
        String location = mockMvc.perform(post("/validate").param("lipidNames", "PC 32:0\nTG 52:2")
                .param("_skipInvalid", "on"))
                .andExpect(status().isSeeOther())
                .andReturn().getResponse().getHeader("Location");
        verifyCounted(1);

        // with a failed name, re-run and remove it
        String other = mockMvc.perform(post("/validate").param("lipidNames", "PC 32:0\nPC(18:2-20:4\nCholesterl"))
                .andExpect(status().isSeeOther())
                .andReturn().getResponse().getHeader("Location");
        verifyCounted(2);
        String id = other.substring(other.lastIndexOf('/') + 1);
        mockMvc.perform(post("/result/" + id + "/rerun").param("edit[2]", "PC(18:2-20:4)"))
                .andExpect(status().isSeeOther());
        mockMvc.perform(post("/result/" + id + "/remove").param("line", "3"))
                .andExpect(status().isSeeOther());
        mockMvc.perform(get(location)).andExpect(status().isOk());
        mockMvc.perform(get(other)).andExpect(status().isOk());
        mockMvc.perform(get(other)).andExpect(status().isOk());
        verifyCounted(2);
    }

    @Test
    void submissionStoppedByTheCheckCountsOnce() throws Exception {
        mockMvc.perform(post("/validate").param("lipidNames", "PC 32:0\nPC(18:2-20:4").param("_skipInvalid", "on"))
                .andExpect(status().isOk());
        verifyCounted(1);
    }

    @Test
    void emptySubmissionIsNotCounted() throws Exception {
        mockMvc.perform(post("/validate").param("lipidNames", "\n  \n")).andExpect(status().isOk());
        verifyNoInteractions(tracker);
    }

    @Test
    void restCallCountsOnce() throws Exception {
        mockMvc.perform(post("/rest/validate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"lipidNames\":[\"PC 32:0\",\"TG 52:2\",\"PC(18:2-20:4\"]}"))
                .andExpect(status().isBadRequest());
        verifyCounted(1);
    }
}
