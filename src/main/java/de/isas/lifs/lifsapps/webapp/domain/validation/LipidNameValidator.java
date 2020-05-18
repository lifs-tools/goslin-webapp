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
package de.isas.lifs.lifsapps.webapp.domain.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import de.isas.lifs.lifsapps.webapp.domain.annotations.ValidLipidName;
import de.isas.lipidomics.domain.LipidAdduct;
import de.isas.lipidomics.palinom.lipidmaps.LipidMapsVisitorParser;
import de.isas.lipidomics.palinom.goslin.GoslinVisitorParser;
import de.isas.lipidomics.palinom.SyntaxErrorListener;
import de.isas.lipidomics.palinom.exceptions.ParsingException;
import de.isas.lipidomics.palinom.goslinfragments.GoslinFragmentsVisitorParser;
import de.isas.lipidomics.palinom.swisslipids.SwissLipidsVisitorParser;
import javax.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author nils.hoffmann
 */
@Slf4j
public class LipidNameValidator implements ConstraintValidator<ValidLipidName, String> {

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
        SyntaxErrorListener listener = new SyntaxErrorListener();
        String lipidNameToValidate = lipidName.trim();
        try {
            LipidAdduct la = new GoslinVisitorParser().parse(lipidNameToValidate, listener);
            return true;
        } catch (ParsingException ex) {
            log.warn("Caught exception while parsing " + lipidNameToValidate + " with Goslin grammar: ", ex);
            return parseWithGoslinFragmentsParser(lipidNameToValidate);
        } catch (ValidationException vex) {
            log.warn("Caught validation exception while parsing " + lipidNameToValidate + " with Goslin grammar: ", vex);
            this.context.disableDefaultConstraintViolation();
            listener.getSyntaxErrors().forEach((t) -> {
                context.buildConstraintViolationWithTemplate(t.getMessage()).addConstraintViolation();
            });
            return false;
        } catch (NullPointerException npe) {
            log.warn("Caught null pointer exception while parsing " + lipidNameToValidate + " with Goslin grammar: ", npe);
            this.context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Unsupported name: " + lipidNameToValidate).addConstraintViolation();
            return false;
        } catch (RuntimeException re) {
            log.warn("Caught runtime exception while parsing " + lipidNameToValidate + " with Goslin grammar: ", re);
            this.context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(re.getLocalizedMessage()).addConstraintViolation();
            return false;
        }
    }

    protected boolean parseWithGoslinFragmentsParser(String lipidName) {
        SyntaxErrorListener lmListener = new SyntaxErrorListener();
        try {
            LipidAdduct lma = new GoslinFragmentsVisitorParser().parse(lipidName, lmListener);
            return true;
        } catch (ParsingException ex1) {
            log.warn("Caught exception while parsing " + lipidName + " with GoslinFragments grammar: ", ex1);
            return parseWithSwissLipidsParser(lipidName);
        } catch (ValidationException vex) {
            log.warn("Caught validation exception while parsing " + lipidName + " with GoslinFragments grammar: ", vex);
            this.context.disableDefaultConstraintViolation();
            lmListener.getSyntaxErrors().forEach((t) -> {
                context.buildConstraintViolationWithTemplate(t.getMessage()).addConstraintViolation();
            });
            return false;
        } catch (NullPointerException npe) {
            log.warn("Caught null pointer exception while parsing " + lipidName + " with GoslinFragments grammar: ", npe);
            this.context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Unsupported name: " + lipidName).addConstraintViolation();
            return false;
        } catch (RuntimeException re) {
            log.warn("Caught exception while parsing " + lipidName + " with GoslinFragments grammar: ", re);
            this.context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(re.getLocalizedMessage()).addConstraintViolation();
            return false;
        }
    }

    protected boolean parseWithSwissLipidsParser(String lipidName) {
        SyntaxErrorListener lmListener = new SyntaxErrorListener();
        try {
            LipidAdduct lma = new SwissLipidsVisitorParser().parse(lipidName, lmListener);
            return true;
        } catch (ParsingException ex1) {
            log.warn("Caught exception while parsing " + lipidName + " with SwissLipids grammar: ", ex1);
            return parseWithLipidMapsParser(lipidName);
        } catch (ValidationException vex) {
            log.warn("Caught validation exception while parsing " + lipidName + " with SwissLipids grammar: ", vex);
            this.context.disableDefaultConstraintViolation();
            lmListener.getSyntaxErrors().forEach((t) -> {
                context.buildConstraintViolationWithTemplate(t.getMessage()).addConstraintViolation();
            });
            return false;
        } catch (NullPointerException npe) {
            log.warn("Caught null pointer exception while parsing " + lipidName + " with SwissLipids grammar: ", npe);
            this.context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Unsupported name: " + lipidName).addConstraintViolation();
            return false;
        } catch (RuntimeException re) {
            log.warn("Caught exception while parsing " + lipidName + " with SwissLipids grammar: ", re);
            this.context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(re.getLocalizedMessage()).addConstraintViolation();
            return false;
        }
    }

    protected boolean parseWithLipidMapsParser(String lipidName) {
        SyntaxErrorListener lmListener = new SyntaxErrorListener();
        try {
            LipidAdduct lma = new LipidMapsVisitorParser().parse(lipidName, lmListener);
            return true;
        } catch (ParsingException ex1) {
            log.warn("Caught exception while parsing " + lipidName + " with LipidMaps grammar: ", ex1);
            this.context.disableDefaultConstraintViolation();
            lmListener.getSyntaxErrors().forEach((t) -> {
                context.buildConstraintViolationWithTemplate(t.getMessage()).addConstraintViolation();
            });
            return false;
        } catch (ValidationException vex) {
            log.warn("Caught validation exception while parsing " + lipidName + " with LipidMaps grammar: ", vex);
            this.context.disableDefaultConstraintViolation();
            lmListener.getSyntaxErrors().forEach((t) -> {
                context.buildConstraintViolationWithTemplate(t.getMessage()).addConstraintViolation();
            });
            return false;
        } catch (NullPointerException npe) {
            log.warn("Caught null pointer exception while parsing " + lipidName + " with LipidMaps grammar: ", npe);
            this.context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Unsupported name: " + lipidName).addConstraintViolation();
            return false;
        } catch (RuntimeException re) {
            log.warn("Caught exception while parsing " + lipidName + " with LipidMaps grammar: ", re);
            this.context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(re.getLocalizedMessage()).addConstraintViolation();
            return false;
        }
    }
}
