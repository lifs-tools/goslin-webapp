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
package de.isas.lifs.palinom.webapp.services;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import de.isas.lipidomics.domain.ExternalDatabaseReference;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 *
 * @author nilshoffmann
 */
@Slf4j
@Service
public class ExternalDatabaseMappingLoader {
    
    private final Map<String, ExternalDatabaseReference> lipidMapsReferences;
    private final Map<String, ExternalDatabaseReference> swissLipidsReferences;
    
    public ExternalDatabaseMappingLoader() {
        this.lipidMapsReferences = new HashMap<>();
        this.swissLipidsReferences = new HashMap<>();
        int lipidMapsEntries = 0;
        for (ExternalDatabaseReference edr : loadObjectList(ExternalDatabaseReference.class, "lipidmaps-normalized.tsv", '\t')) {
            this.lipidMapsReferences.put(edr.getNormalizedName(), edr);
            this.lipidMapsReferences.put(edr.getNativeAbbreviation(), edr);
            lipidMapsEntries++;
        }
        int swissLipidsEntries = 0;
        for (ExternalDatabaseReference edr : loadObjectList(ExternalDatabaseReference.class, "swiss-lipids-normalized.tsv", '\t')) {
            this.swissLipidsReferences.put(edr.getNormalizedName(), edr);
            this.swissLipidsReferences.put(edr.getNativeAbbreviation(), edr);
            swissLipidsEntries++;
        }
        
        log.info("Loaded {} records for Lipid MAPS!", lipidMapsEntries);
        log.info("Loaded {} records for Swiss Lipids!", swissLipidsEntries);
    }
    
    public Optional<ExternalDatabaseReference> findSwissLipidsEntry(String... names) {
        List<String> namesList = Arrays.asList(names);
        return Optional.ofNullable(namesList.stream().map((t) -> {
            return this.swissLipidsReferences.get(t);
        }).filter((t) -> {
            return t != null;
        }).findFirst().orElse(null));
    }
    
    public Optional<ExternalDatabaseReference> findLipidMapsEntry(String lipidMapsNames) {
        Optional<ExternalDatabaseReference> ref = Optional.ofNullable(this.lipidMapsReferences.get(lipidMapsNames));
        if (ref.isPresent()) {
            log.info("Found LipidMAPS match for " + lipidMapsNames + ": " + ref.get());
        }
        return ref;
    }
    
    protected <T> List<T> loadObjectList(Class<T> type, String fileName, char columnSeparator) {
        try {
            CsvSchema bootstrapSchema = CsvSchema.emptySchema().withHeader().withColumnSeparator(columnSeparator);
            CsvMapper mapper = new CsvMapper();
            InputStream fileIs = new ClassPathResource(fileName).getInputStream();
            MappingIterator<T> readValues
                    = mapper.reader(type).with(bootstrapSchema).readValues(fileIs);
            return readValues.readAll();
        } catch (Exception e) {
            log.error("Error occurred while loading object list from file " + fileName, e);
            return Collections.emptyList();
        }
    }
}
