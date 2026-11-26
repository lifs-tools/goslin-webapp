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

}
