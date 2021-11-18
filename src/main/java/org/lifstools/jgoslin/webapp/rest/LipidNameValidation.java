/*
 * Copyright 2019 Leibniz Institut für Analytische Wissenschaften - ISAS e.V..
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
import org.lifstools.jgoslin.webapp.domain.ValidationResults;
import org.lifstools.jgoslin.webapp.services.LipidNameValidationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author nils.hoffmann
 */
@RestController
@RequestMapping("/rest/validate")
@Api(value = "Parses and validates Lipid names")
public class LipidNameValidation {

    private static final Logger log = LoggerFactory.getLogger(LipidGrammars.class);

    private final LipidNameValidationService validationService;

    @Autowired
    public LipidNameValidation(LipidNameValidationService validationService) {
        this.validationService = validationService;
    }

    @ApiOperation(value = "Parse and validate the provided lipid names.", response = ValidationResults.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully parsed and validated all lipids."),
        @ApiResponse(code = 400, message = "Failed to parse and validate at least one lipid."),
        @ApiResponse(code = 401, message = "Authorization required to access this resource."),
        @ApiResponse(code = 403, message = "Access to resource is forbidden.")
    })
    @PostMapping()
    public ResponseEntity<ValidationResults> validate(
            @ApiParam(value = "Validation request with list of lipid names and grammars to use. For each grammar, a specialized parser is instantiated and used to parse the lipid name. The first successful parser result is returned. If no parser was successful, the returned list will contain validation messages to help you track down the issue and the name of the last grammar / parser used to validate the lipid name.", required = true)
            @RequestBody ValidationRequest validationRequest) {
        List<ValidationResult> results;
        HttpStatus httpStatus = HttpStatus.OK;
        if (validationRequest.getGrammars() == null || validationRequest.getGrammars().isEmpty()) {
            results = validationService.validate(validationRequest.getLipidNames());
        } else {
            results = validationService.validate(validationRequest.getLipidNames(), validationRequest.getGrammars());
        }
        long failedResults = results.stream().filter((arg0) -> {
            return !arg0.getMessages().isEmpty();
        }).count();
        if (failedResults > 0l) {
            log.warn("{} lipid names failed to parse/validate!", failedResults);
            httpStatus = HttpStatus.BAD_REQUEST;
        }
        ValidationResults validationResults = new ValidationResults();
        validationResults.setFailedToParse(failedResults);
        validationResults.setTotalReceived(Long.valueOf(validationRequest.getLipidNames().size()));
        validationResults.setTotalParsed(Long.valueOf(results.size()));
        validationResults.setResults(results);
        ResponseEntity re = ResponseEntity.status(httpStatus).body(validationResults);
        return re;
    }
}
