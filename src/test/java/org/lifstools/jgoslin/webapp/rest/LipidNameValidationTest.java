/*
 * Copyright 2020 Leibniz Institut für Analytische Wissenschaften - ISAS e.V..
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
package org.lifstools.jgoslin.webapp.rest;

import org.lifstools.jgoslin.webapp.domain.ValidationRequest;
import org.lifstools.jgoslin.webapp.domain.ValidationResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.Test;
import org.lifstools.jgoslin.webapp.services.LipidNameValidationService;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 *
 * @author nilshoffmann
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LipidNameValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Test
    public void validateNames() throws Exception {
        ValidationRequest vfr = new ValidationRequest();
        vfr.setGrammars(Arrays.asList(ValidationResult.Grammar.values()));
        vfr.setLipidNames(Arrays.asList("CL 18:3(9Z,12Z,15Z)/16:0/22:5(16Z,10Z,19Z,13Z,7Z)/20:0"));

        mockMvc.perform(MockMvcRequestBuilders.post("/rest/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(vfr))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.totalReceived", org.hamcrest.Matchers.is(1)))
                .andExpect(jsonPath("$.totalParsed", org.hamcrest.Matchers.is(1)))
                .andExpect(jsonPath("$.failedToParse", org.hamcrest.Matchers.is(0)));
    }

    @Test
    public void keepsTheJsonFieldNamesOfJackson2() throws Exception {
        // Jackson 3 would name getNDoubleBonds() "NDoubleBonds"; the REST API keeps "ndoubleBonds".
        ValidationRequest vfr = new ValidationRequest();
        vfr.setLipidNames(Arrays.asList("PC 18:1_16:0"));

        mockMvc.perform(MockMvcRequestBuilders.post("/rest/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(vfr))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].lipidAdduct.lipid.info.ndoubleBonds", org.hamcrest.Matchers.is(1)))
                .andExpect(jsonPath("$.results[0].lipidAdduct.lipid.faList[0].ndoubleBonds", org.hamcrest.Matchers.is(1)))
                .andExpect(jsonPath("$.results[0].lipidAdduct.lipid.info.NDoubleBonds").doesNotExist());
    }

    @Test
    public void acceptsNullForSkipInvalid() throws Exception {
        // Jackson 2 coerced null to false for the primitive skipInvalid; the REST API keeps doing so.
        mockMvc.perform(MockMvcRequestBuilders.post("/rest/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lipidNames\":[\"PC 18:1_16:0\"],\"skipInvalid\":null}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalParsed", org.hamcrest.Matchers.is(1)));
    }

    @Test
    public void writesTheResultFieldsInDeclarationOrder() throws Exception {
        // Jackson 3 would sort the fields alphabetically; the REST API keeps the order of Jackson 2.
        String body = mockMvc.perform(MockMvcRequestBuilders.post("/rest/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lipidNames\":[\"PC 18:1_16:0\"]}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode result = objectMapper.readTree(body).get("results").get(0);
        List<String> names = new ArrayList<>(result.propertyNames());
        assertEquals(List.of("lipidName", "grammar", "messages", "lipidAdduct"), names.subList(0, 4));
    }

    @MockitoSpyBean
    private LipidNameValidationService validationService;

    private static String request(String... names) {
        return "{\"lipidNames\":[" + String.join(",", java.util.Arrays.stream(names).map(n -> "\"" + n + "\"").toList()) + "]}";
    }

    @Test
    public void restRejectsNameLongerThanLimit() throws Exception {
        String tooLong = "C".repeat(301);
        mockMvc.perform(MockMvcRequestBuilders.post("/rest/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("PC 18:1_16:0", "PE 18:1_16:0", tooLong))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Name 3 is longer than 300 characters.")))
                .andExpect(content().string(not(containsString(tooLong))));
        verifyNoInteractions(validationService);
    }

    @Test
    public void restMeasuresTheLimitAfterNormalising() throws Exception {
        // 300 characters plus surrounding and doubled blanks are 300 after normalising
        doReturn(List.of()).when(validationService).validate(anyList());
        mockMvc.perform(MockMvcRequestBuilders.post("/rest/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("  " + "C".repeat(150) + "   " + "C".repeat(149) + " "))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void restAcceptsNameAtLimit() throws Exception {
        doReturn(List.of()).when(validationService).validate(anyList());
        mockMvc.perform(MockMvcRequestBuilders.post("/rest/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("C".repeat(300)))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void restCapCountsNoBreakSpaceRuns() throws Exception {
        // 150 x ("C" + 1 no-break space): 300 raw characters of text plus 150 spaces; normalised it is 299 characters
        String name = "C\u00a0".repeat(150);
        org.mockito.ArgumentCaptor<List<String>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        doReturn(List.of()).when(validationService).validate(anyList());
        mockMvc.perform(MockMvcRequestBuilders.post("/rest/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(name + name))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        // a long run of no-break spaces collapses to one space
        String runs = "C" + "\u00a0".repeat(1000) + "C";
        mockMvc.perform(MockMvcRequestBuilders.post("/rest/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(runs))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(validationService).validate(captor.capture());
        assertEquals(List.of("C C"), captor.getValue());
    }

    @Test
    public void restCapCountsLeadingUnicodeSpaces() throws Exception {
        org.mockito.ArgumentCaptor<List<String>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        doReturn(List.of()).when(validationService).validate(anyList());
        mockMvc.perform(MockMvcRequestBuilders.post("/rest/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("\u2003".repeat(10000) + "PC 32:0"))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(validationService).validate(captor.capture());
        assertEquals(List.of("PC 32:0"), captor.getValue());
    }

}
