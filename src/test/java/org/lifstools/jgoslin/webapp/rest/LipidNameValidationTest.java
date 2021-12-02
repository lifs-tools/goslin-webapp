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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.lifstools.jgoslin.webapp.domain.ValidationRequest;
import org.lifstools.jgoslin.webapp.domain.ValidationResult;
import java.util.Arrays;
import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *
 * @author nilshoffmann
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
public class LipidNameValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void validateNames() throws Exception {
        ValidationRequest vfr = new ValidationRequest();
        vfr.setGrammars(Arrays.asList(ValidationResult.Grammar.values()));
        vfr.setLipidNames(Arrays.asList("CL 18:3(9Z,12Z,15Z)/16:0/22:5(16Z,10Z,19Z,13Z,7Z)/20:0"));

        mockMvc.perform(MockMvcRequestBuilders.post("/rest/validate")
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .content(objectMapper.writeValueAsString(vfr))
                .accept(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.totalReceived", org.hamcrest.Matchers.is(1)))
                .andExpect(jsonPath("$.totalParsed", org.hamcrest.Matchers.is(1)))
                .andExpect(jsonPath("$.failedToParse", org.hamcrest.Matchers.is(0)));
    }

}
