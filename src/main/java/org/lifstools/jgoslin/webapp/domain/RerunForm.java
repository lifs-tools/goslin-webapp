/*
 * Copyright 2026 Leibniz Institut für Analytische Wissenschaften - ISAS e.V..
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

import java.util.HashMap;
import java.util.Map;

/**
 * The corrections posted from the result page, keyed by line number: inputs named {@code edit[34]}.
 */
public class RerunForm {

    private Map<Integer, String> edit = new HashMap<>();

    public Map<Integer, String> getEdit() {
        return edit;
    }

    public void setEdit(Map<Integer, String> edit) {
        this.edit = edit == null ? new HashMap<>() : edit;
    }
}
