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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.lifstools.jgoslin.webapp.domain.NameEntries;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
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
//@Api(value = "Parses and validates Lipid names")
public class LipidNameValidation {

    private static final Logger log = LoggerFactory.getLogger(LipidGrammars.class);

    private final LipidNameValidationService validationService;

    @Autowired
    public LipidNameValidation(LipidNameValidationService validationService) {
        this.validationService = validationService;
    }

//    @ApiOperation(value = "Parse and validate the provided lipid names.", response = ValidationResults.class)
//    @ApiResponses(value = {
//        @ApiResponse(code = 200, message = "Successfully parsed and validated all lipids."),
//        @ApiResponse(code = 400, message = "Failed to parse and validate at least one lipid."),
//        @ApiResponse(code = 401, message = "Authorization required to access this resource."),
//        @ApiResponse(code = 403, message = "Access to resource is forbidden.")
//    })
    @Operation(summary = "Parse and validate the provided lipid names.",
            description = "Each name may have at most 300 characters (after trimming and collapsing whitespace). "
            + "If any name is longer, the whole request is rejected with 400 and no name is parsed.")
    @ApiResponse(responseCode = "200", description = "Successfully parsed and validated all lipid names.")
    @ApiResponse(responseCode = "400", description = "At least one name could not be parsed (the body is the result object), "
            + "or a name is longer than 300 characters (the body is a problem detail naming its position; nothing was parsed).")
    @PostMapping()
    public ResponseEntity<ValidationResults> validate(
            //@ApiParam(value = "Validation request with list of lipid names and grammars to use. For each grammar, a specialized parser is instantiated and used to parse the lipid name. The first successful parser result is returned. If no parser was successful, the returned list will contain validation messages to help you track down the issue and the name of the last grammar / parser used to validate the lipid name. If you provide an empty list or null for grammars, jgoslin will use the LipidParser internally, which supports all grammars. skipInvalid is currently only relevant to the form based validation. REST service validation will commence on all lipid names.", required = true)
            @RequestBody ValidationRequest validationRequest) {
        // The string that is measured is the string that is parsed.
        List<String> names = normalizedNames(validationRequest.getLipidNames());
        List<ValidationResult> results;
        HttpStatus httpStatus = HttpStatus.OK;
        if (validationRequest.getGrammars() == null || validationRequest.getGrammars().isEmpty()) {
            results = validationService.validate(names);
        } else {
            results = validationService.validate(names, validationRequest.getGrammars());
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
        validationResults.setTotalReceived(Long.valueOf(names.size()));
        validationResults.setTotalParsed(Long.valueOf(results.size()));
        validationResults.setResults(results);
        ResponseEntity re = ResponseEntity.status(httpStatus).body(validationResults);
        return re;
    }

    /** Answers with a problem detail (RFC 9457) instead of the HTML error page. */
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail rejected(ResponseStatusException e) {
        return ProblemDetail.forStatusAndDetail(e.getStatusCode(), e.getReason());
    }

    /** Normalises every name once, as the web form does, and refuses any longer than the limit; parse time grows steeply with length. */
    private static List<String> normalizedNames(List<String> names) {
        if (names == null) {
            return null;
        }
        List<String> normalized = new java.util.ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i) == null ? null : NameEntries.normalize(names.get(i));
            if (name != null && name.length() > NameEntries.MAX_NAME_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Name " + (i + 1) + " is longer than " + NameEntries.MAX_NAME_LENGTH + " characters.");
            }
            normalized.add(name);
        }
        return normalized;
    }
}
