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

import org.lifstools.jgoslin.webapp.domain.ValidationResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author nils.hoffmann
 */
@RestController
@RequestMapping("/rest/grammars")
@Api(value = "Return information on grammars")
@Slf4j
public class LipidGrammars {
    
    @ApiOperation(value = "Return a list of supported grammars", response = ValidationResult.Grammar[].class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Returned the list of grammars."),
        @ApiResponse(code = 401, message = "Authorization required to access this resource."),
        @ApiResponse(code = 403, message = "Access to resource is forbidden.")
    })
    @GetMapping()
    public ResponseEntity<ValidationResult.Grammar[]> grammars() {
        return ResponseEntity.status(HttpStatus.OK).body(ValidationResult.Grammar.values());
    }

}
