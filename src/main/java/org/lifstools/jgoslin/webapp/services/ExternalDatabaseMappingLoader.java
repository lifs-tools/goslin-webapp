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
package org.lifstools.jgoslin.webapp.services;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.lifstools.jgoslin.webapp.domain.ExternalDatabaseReference;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 *
 * @author nilshoffmann
 */
@Service
public class ExternalDatabaseMappingLoader {

    private static final Logger log = LoggerFactory.getLogger(ExternalDatabaseMappingLoader.class);

    private final MultiValuedMap<String, ExternalDatabaseReference> lipidMapsReferences;
    private final MultiValuedMap<String, ExternalDatabaseReference> swissLipidsReferences;

    public ExternalDatabaseMappingLoader() {
        this.lipidMapsReferences = new ArrayListValuedHashMap<>();
        this.swissLipidsReferences = new ArrayListValuedHashMap<>();
        int lipidMapsEntries = 0;
        for (ExternalDatabaseReference edr : loadObjectList(ExternalDatabaseReference.class, "lipidmaps-normalized.tsv", '\t')) {
            this.lipidMapsReferences.put(edr.getNormalizedName(), edr);
            this.lipidMapsReferences.put(edr.getNativeAbbreviation(), edr);
            this.lipidMapsReferences.put(edr.getNativeName(), edr);
            lipidMapsEntries++;
        }
        log.info("Loaded {} records for Lipid MAPS!", lipidMapsEntries);
        int swissLipidsEntries = 0;
        for (ExternalDatabaseReference edr : loadObjectList(ExternalDatabaseReference.class, "swiss-lipids-normalized.tsv", '\t')) {
            this.swissLipidsReferences.put(edr.getNormalizedName(), edr);
            this.swissLipidsReferences.put(edr.getNativeAbbreviation(), edr);
            this.swissLipidsReferences.put(edr.getNativeName(), edr);
            swissLipidsEntries++;
        }

        log.info("Loaded {} records for Swiss Lipids!", swissLipidsEntries);
    }

    public Optional<Collection<ExternalDatabaseReference>> findSwissLipidsEntry(String... names) {
        List<String> namesList = Arrays.asList(names);
        return Optional.of(namesList.stream().filter((t) -> t!=null).map((t) -> {
            return this.swissLipidsReferences.get(t);
        }).flatMap(Collection::stream).filter((t) -> {
            return t != null;
        }).distinct().collect(Collectors.toList()));
    }

    public Optional<Collection<ExternalDatabaseReference>> findLipidMapsEntry(String... lipidMapsNames) {
        List<String> namesList = Arrays.asList(lipidMapsNames);
        return Optional.of(namesList.stream().filter((t) -> t!=null).map((t) -> {
            return this.lipidMapsReferences.get(t);
        }).flatMap(Collection::stream).filter((t) -> {
            return t != null;
        }).distinct().collect(Collectors.toList()));
    }

    protected final <T> List<T> loadObjectList(Class<T> type, String fileName, char columnSeparator) {
        try {
            CsvSchema bootstrapSchema = CsvSchema.emptySchema().withHeader().withColumnSeparator(columnSeparator);
            CsvMapper mapper = new CsvMapper();
            InputStream fileIs = new ClassPathResource(fileName).getInputStream();
            MappingIterator<T> readValues
                    = mapper.readerFor(type).with(bootstrapSchema).readValues(fileIs);
            return readValues.readAll();
        } catch (Exception e) {
            log.error("Error occurred while loading object list from file " + fileName, e);
            return Collections.emptyList();
        }
    }
}
