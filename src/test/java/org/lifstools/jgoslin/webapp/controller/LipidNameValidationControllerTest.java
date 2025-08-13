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
package org.lifstools.jgoslin.webapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.lifstools.jgoslin.webapp.Application;
import org.lifstools.jgoslin.webapp.domain.ValidationRequest;
import org.lifstools.jgoslin.webapp.domain.ValidationResult.Grammar;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.FormLoginRequestBuilder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
//import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 *
 * @author nilshoffmann
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
  classes = Application.class)
@AutoConfigureMockMvc
@WebAppConfiguration
@ActiveProfiles(profiles = {"test", "dev"})
public class LipidNameValidationControllerTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .build();
    }

// /* This test currently has issues with object serialization of the multipart file */
//    @Test
//    public void validateFile() throws Exception {
//        ValidationFileRequest vfr = new ValidationFileRequest();
//        String fileName = "filename.txt";
//        File file = File.createTempFile(fileName, "");
//        file.delete();
//        Files.writeString(file.toPath(), "CL 18:3(9Z,12Z,15Z)/16:0/22:5(16Z,10Z,19Z,13Z,7Z)/20:0\n");
//
//        MockMultipartFile lipidNamesFile = new MockMultipartFile("file", fileName, "text/plain", Files.readAllBytes(file.toPath()));
//        vfr.setFile(lipidNamesFile);
//
//        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
//        mockMvc.perform(MockMvcRequestBuilders.post("/validatefile")
//                .contentType(MediaType.MULTIPART_FORM_DATA)
//                .content(objectMapper.writeValueAsString(vfr))
//                .accept(MediaType.MULTIPART_FORM_DATA))
//                .andExpect(status().isOk())
//                //                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
//                .andExpect(view().name("index"));
////                    .andExpect(content().string("success"));
//    }
    @Test
    public void validateNames() throws Exception {
        ValidationRequest vfr = new ValidationRequest();
        vfr.setGrammars(Arrays.asList(Grammar.values()));
        vfr.setLipidNames(Arrays.asList("CL 18:3(9Z,12Z,15Z)/16:0/22:5(16Z,10Z,19Z,13Z,7Z)/20:0"));

        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        mockMvc.perform(MockMvcRequestBuilders.post("/validate")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .content(objectMapper.writeValueAsString(vfr))
                .accept(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(view().name("validationResult"));
    }
}
