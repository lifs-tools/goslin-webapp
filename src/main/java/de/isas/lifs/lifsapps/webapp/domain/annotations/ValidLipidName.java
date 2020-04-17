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
package de.isas.lifs.lifsapps.webapp.domain.annotations;

import de.isas.lifs.lifsapps.webapp.domain.validation.LipidNameValidator;
import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.ElementType.TYPE_USE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

/**
 *
 * @author nils.hoffmann
 */
@Target({TYPE, FIELD, TYPE_USE, ANNOTATION_TYPE}) 
@Retention(RUNTIME)
@Constraint(validatedBy = LipidNameValidator.class)
@Documented
public @interface ValidLipidName {   
    String message() default "Invalid lipid name!";
    Class<?>[] groups() default {}; 
    Class<? extends Payload>[] payload() default {};
}
