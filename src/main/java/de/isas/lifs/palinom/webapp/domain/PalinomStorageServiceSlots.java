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
package de.isas.lifs.palinom.webapp.domain;

import de.isas.lifs.webapps.common.domain.StorageServiceSlot;
import de.isas.lifs.webapps.common.domain.ToolResult;

/**
 *
 * @author nils.hoffmann
 */
public final class PalinomStorageServiceSlots {

    public static final StorageServiceSlot OUTPUT_TSV_FILE = new StorageServiceSlot() {
        @Override
        public String getExtension() {
            return ".tsv";
        }

        @Override
        public String getName() {
            return ToolResult.OUTPUT_FILE;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }
    };
    
    public static final StorageServiceSlot RESULTS = new StorageServiceSlot() {
        @Override
        public String getExtension() {
            return "";
        }

        @Override
        public String getName() {
            return ToolResult.RESULT_DIR;
        }

        @Override
        public boolean isDirectory() {
            return true;
        }
    };
    
}
