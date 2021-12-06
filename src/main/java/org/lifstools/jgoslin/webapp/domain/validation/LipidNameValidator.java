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
package org.lifstools.jgoslin.webapp.domain.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.lifstools.jgoslin.webapp.domain.annotations.ValidLipidName;
import org.lifstools.jgoslin.parser.LipidParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author nils.hoffmann
 */
public class LipidNameValidator implements ConstraintValidator<ValidLipidName, String> {

    private static final Logger log = LoggerFactory.getLogger(LipidNameValidator.class);

    private static final LipidParser lipidParser = LipidParser.newInstance();
    private ConstraintValidatorContext context;

    @Override
    public void initialize(ValidLipidName constraintAnnotation) {
    }

    @Override
    public boolean isValid(String lipidName, ConstraintValidatorContext context) {
        this.context = context;
        return (validateLipidName(lipidName));
    }

    private boolean validateLipidName(String lipidName) {
        if (lipidName == null) {
            log.warn("Lipid name must not be null!");
            this.context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Lipid name must not be null!").addConstraintViolation();
            return false;
        }
        String lipidNameToValidate = lipidName.replaceAll("\\s+", " ").trim();
        try {
            lipidParser.parse(lipidNameToValidate);
            return true;
        } catch (Exception lpe) {
            log.warn("Caught exception while parsing " + lipidNameToValidate + ": ", lpe);
            this.context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(lpe.getLocalizedMessage()).addConstraintViolation();
            return false;
        }
    }
}
