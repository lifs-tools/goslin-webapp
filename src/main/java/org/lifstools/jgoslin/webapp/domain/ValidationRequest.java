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
package org.lifstools.jgoslin.webapp.domain;

import org.lifstools.jgoslin.webapp.domain.annotations.ValidLipidName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Data;

/**
 *
 * @author nils.hoffmann
 */
@Data
public class ValidationRequest {

    @ApiModelProperty(position = 1, required = true, example = "[\"Cer 18:1;2/16:0\",\"Cer(d18:1/16:0)\"]")
    private List<@ValidLipidName String> lipidNames;
    
    @ApiModelProperty(position = 2, allowEmptyValue = true, required = false, example = "[\"GOSLIN\",\"LIPIDMAPS\"]")
    private List<ValidationResult.Grammar> grammars;

}
