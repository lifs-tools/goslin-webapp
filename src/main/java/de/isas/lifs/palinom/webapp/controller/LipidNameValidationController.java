/*
 * Copyright 2017 Leibniz Institut für Analytische Wissenschaften - ISAS e.V..
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
package de.isas.lifs.palinom.webapp.controller;

import com.google.common.io.Files;
import de.isas.lifs.palinom.webapp.config.NewsPropertyConfig;
import de.isas.lifs.palinom.webapp.domain.AppInfo;
import de.isas.lifs.palinom.webapp.domain.Page;
import de.isas.lifs.palinom.webapp.domain.PalinomStorageServiceSlots;
import de.isas.lifs.palinom.webapp.domain.ValidationFileRequest;
import de.isas.lifs.palinom.webapp.domain.ValidationRequest;
import de.isas.lifs.palinom.webapp.domain.ValidationResult;
import de.isas.lifs.palinom.webapp.domain.ValidationResults;
import de.isas.lifs.palinom.webapp.services.LipidNameValidationService;
import de.isas.lifs.palinom.webapp.services.PageBuilderService;
import de.isas.lifs.webapps.common.domain.StorageServiceSlot;
import de.isas.lifs.webapps.common.domain.UserSessionFile;
import de.isas.lifs.webapps.common.service.SessionIdGenerator;
import de.isas.lifs.webapps.common.service.StorageService;
import de.isas.lipidomics.domain.FattyAcid;
import de.isas.lipidomics.domain.LipidClass;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;
import springfox.documentation.annotations.ApiIgnore;
//import uk.ac.ebi.pride.jmztab2.utils.errors.MZTabErrorType;

/**
 *
 * @author Nils Hoffmann &lt;nils.hoffmann@isas.de&gt;
 */
@ApiIgnore
@Slf4j
@Controller
public class LipidNameValidationController {

    private final AppInfo appInfo;
    private final StorageService storageService;
    private final SessionIdGenerator sessionIdGenerator;
    private final LipidNameValidationService lipidNameValidationService;
    private final PageBuilderService pageBuilderService;
    private final LocaleResolver localeResolver;
    private final Resource lipidnames;
    private final NewsPropertyConfig newsPropertyConfig;

    @Autowired
    public LipidNameValidationController(LipidNameValidationService lipidNameValidationService, StorageService storageService, PageBuilderService pageBuilderService,
            AppInfo appInfo, SessionIdGenerator sessionIdGenerator, LocaleResolver localeResolver, @Value("classpath:lipidnames.txt") Resource lipidnames, NewsPropertyConfig newsPropertyConfig) {
        this.appInfo = appInfo;
        this.lipidNameValidationService = lipidNameValidationService;
        this.storageService = storageService;
        this.pageBuilderService = pageBuilderService;
        this.sessionIdGenerator = sessionIdGenerator;
        this.localeResolver = localeResolver;
        this.lipidnames = lipidnames;
        this.newsPropertyConfig = newsPropertyConfig;
    }

    @GetMapping("/")
    public ModelAndView handleHome(@RequestParam(value = "lang", required = false) String language, HttpServletRequest request, HttpServletResponse response, Principal principal) throws IOException {
        if (language != null) {
            localeResolver.setLocale(request, response, Locale.forLanguageTag(language));
        }
        ModelAndView model = new ModelAndView("index");
        model.addObject("page", createPage("Translate Lipid Names", Optional.ofNullable(principal)));
        ValidationRequest vr = new ValidationRequest();
        vr.setLipidNames(Streams.asString(lipidnames.getInputStream(), "UTF8").lines().collect(Collectors.toList()));
        model.addObject("validationRequest", vr);
        model.addObject("validationFileRequest", new ValidationFileRequest());
        return model;
    }

    @GetMapping(path = "/logout")
    public String logout(HttpServletRequest request) throws ServletException {
        request.logout();
        return "redirect:/";
    }

    @GetMapping("/documentation")
    public ModelAndView handleInfo(@RequestParam(value = "lang", required = false) String language, HttpServletRequest request, HttpServletResponse response, Principal principal) throws IOException {
        if (language != null) {
            localeResolver.setLocale(request, response, Locale.forLanguageTag(language));
        }
        log.info("Retrieved the following news items: {}", newsPropertyConfig.getNews());
        ModelAndView model = new ModelAndView("documentation");
        model.addObject("page", createPage("Goslin Webapp Information", Optional.ofNullable(principal)));
        model.addObject("news", newsPropertyConfig.getNews());
        model.addObject("lipidClasses", Arrays.asList(LipidClass.values()).stream().sorted((t, t1) -> {
            int cmp = t.getCategory().name().compareTo(t1.getCategory().name());
            if (cmp != 0) {
                return cmp;
            }
            return t.getLipidMapsClassName().compareTo(t1.getLipidMapsClassName());
        }).collect(Collectors.toList()));
        return model;
    }

    @PostMapping("/validatefile")
    public RedirectView validate(@Valid @ModelAttribute("validationFileRequest") ValidationFileRequest validationFileRequest, BindingResult bindingResult,
            RedirectAttributes redirectAttributes, HttpServletRequest request,
            HttpSession session, Principal principal) throws IOException {
        ValidationRequest validationRequest = new ValidationRequest();
        validationRequest.setLipidNames(new String(validationFileRequest.getFile().getBytes(), StandardCharsets.UTF_8).lines().filter((t) -> {
            return !t.isEmpty();
        }).map((t) -> {
            return t.trim();
        }).collect(Collectors.toList()));
        redirectAttributes.addFlashAttribute("validationRequest", validationRequest);
        return new RedirectView("/validate", true);
    }

    @RequestMapping(path = {"/validate"}, method = {RequestMethod.GET,
        RequestMethod.POST})
    public ModelAndView validate(@Valid @ModelAttribute("validationRequest") ValidationRequest validationRequest, BindingResult bindingResult,
            RedirectAttributes redirectAttributes, HttpServletRequest request,
            HttpSession session, Principal principal) {
        if (bindingResult.hasErrors()) {
            log.warn("Binding result has errors: {}", bindingResult);
            ModelAndView modelAndView = new ModelAndView("index");
            modelAndView.addObject("page", createPage("Translate Lipid Names", Optional.ofNullable(principal)));
            modelAndView.addObject("validationRequest", validationRequest);
            modelAndView.addObject("hasBeenSubmitted", true);
            modelAndView.addObject("validationFileRequest", new ValidationFileRequest());
            return modelAndView;
        }
        ModelAndView mav = new ModelAndView("validationResult");
        mav.addObject("page", createPage("Lipid Name Validation Results", Optional.ofNullable(principal)));
        List<ValidationResult> validationResults = lipidNameValidationService.validate(Optional.ofNullable(validationRequest.getLipidNames()).orElse(Collections.emptyList()));
        log.info("Received {} validation results!", validationResults.size());
        ValidationResults results = new ValidationResults();
        results.setResults(validationResults);
        UUID sessionId = sessionIdGenerator.generate();
        UserSessionFile usf = storageService.store(toTable(results),
                sessionId, PalinomStorageServiceSlots.OUTPUT_TSV_FILE);
        mav.addObject("validationResults", results);
        mav.addObject("validationResultsTsv", "OUTPUT_TSV_FILE");
        mav.addObject(new ValidationFileRequest());
        mav.addObject("userSessionId", usf.getUserSessionId());
        long parsingErrors = validationResults.stream().filter((t) -> {
            return t.getLipidAdduct() == null;
        }).count();
        log.info("Encountered {} parsing errors!", parsingErrors);
        if (parsingErrors > 0) {
            mav.addObject("message", "Encountered " + parsingErrors + " parsing errors!");
            mav.addObject("messageLevel", "alert-danger");
        }
        return mav;
    }

    @GetMapping("/download/{sessionId:.+}/{storageServiceSlot:.+}")
    @ResponseBody
    public ResponseEntity<Resource> getResults(
            @PathVariable String sessionId, @PathVariable String storageServiceSlot,
            HttpServletRequest request,
            HttpSession session, Principal principal) throws FileNotFoundException {
        UUID resultSessionId = UUID.fromString(sessionId);
        if ("OUTPUT_TSV_FILE".equals(storageServiceSlot)) {
            MediaType mediaType = MediaType.ALL;
            StorageServiceSlot slot = PalinomStorageServiceSlots.OUTPUT_TSV_FILE;
            UserSessionFile usf = storageService.load(resultSessionId, slot);
            String ext = Files.getFileExtension(usf.getFilename()).toLowerCase();
            switch (ext) {
                case "zip":
                    mediaType = MediaType.valueOf("application/zip");
                    break;
                case "txt":
                case "log":
                case "csv":
                case "tsv":
                    mediaType = MediaType.TEXT_PLAIN;
                    break;
                case "svg":
                case "xml":
                    mediaType = MediaType.APPLICATION_XML;
                    break;
                case "png":
                    mediaType = MediaType.IMAGE_PNG;
                    break;
                case "gif":
                    mediaType = MediaType.IMAGE_GIF;
                    break;
                case "jpg":
                case "jpeg":
                    mediaType = MediaType.IMAGE_JPEG;
                    break;
                case "json":
                    mediaType = MediaType.APPLICATION_JSON;
                    break;
                default:
                    mediaType = MediaType.APPLICATION_OCTET_STREAM;
                    throw new FileNotFoundException(
                            "No handler for file extension '" + ext + "' available for '" + sessionId + "' and filename '" + usf.getFilename() + "'!");
            }
            Resource file = storageService.loadAsResource(usf, slot);
            return ResponseEntity.ok().
                    header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + file.getFilename() + "\"").
                    contentType(mediaType).
                    body(file);
        } else {
            throw new FileNotFoundException("No such file!");
        }
    }

    private String toTable(ValidationResults vr) {
        StringBuilder sb = new StringBuilder();
        HashSet<String> keys = new LinkedHashSet<>();
        List<Map<String, String>> entries = vr.getResults().stream().map((t) -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("Normalized Name", t.getGoslinName());
            m.put("Original Name", t.getLipidName());
            m.put("Grammar", t.getGrammar().name());
            m.put("Validation Messages", t.getMessages().stream().collect(Collectors.joining(" | ")));
            m.put("Adduct", t.getLipidAdduct().getAdduct().getAdductString());
            m.put("Lipid Maps Category", t.getLipidAdduct().getLipid().getLipidCategory().getFullName() + " [" + t.getLipidAdduct().getLipid().getLipidCategory().name() + "]");
            LipidClass lclass = t.getLipidAdduct().getLipid().getLipidClass();
            m.put("Lipid Maps Main Class", lclass.getLipidMapsClassName());
            m.put("Lipid Maps References", t.getLipidMapsReferences().stream().flatMap(Collection::stream).map((r) -> {
                return r.getDatabaseUrl() + r.getDatabaseElementId();
            }).collect(Collectors.joining(" | ")));
            m.put("Swiss Lipids References", t.getSwissLipidsReferences().stream().flatMap(Collection::stream).map((r) -> {
                return r.getDatabaseUrl() + r.getDatabaseElementId();
            }).collect(Collectors.joining(" | ")));
            m.put("Functional Class Abbr", "[" + lclass.getAbbreviation() + "]");
            m.put("Functional Class Synonyms", "[" + lclass.getSynonyms().stream().collect(Collectors.joining(", ")) + "]");
            m.put("Level", t.getLipidSpeciesInfo().getLevel().toString());
            m.put("Total #C", t.getLipidSpeciesInfo().getNCarbon() + "");
            m.put("Total #OH", t.getLipidSpeciesInfo().getNHydroxy() + "");
            m.put("Total #DB", t.getLipidSpeciesInfo().getNDoubleBonds() + "");
            m.put("Exact Mass", String.format("%.4f", t.getMass()));
            m.put("Formula", t.getSumFormula());
            for (FattyAcid fa : t.getFattyAcids().values()) {
                m.put(fa.getName() + " SN Position", fa.getPosition() + "");
                m.put(fa.getName() + " #C", fa.getNCarbon() + "");
                m.put(fa.getName() + " #OH", fa.getNHydroxy() + "");
                m.put(fa.getName() + " #DB", fa.getNDoubleBonds() + "");
                m.put(fa.getName() + " Bond Type", fa.getLipidFaBondType() + "");
                m.put(fa.getName() + " DB Positions", fa.getDoubleBondPositions().entrySet().stream().map((dbPosEntry) -> {
                    return dbPosEntry.getKey() + "" + dbPosEntry.getValue();
                }).collect(Collectors.toList()) + "");
                m.put(fa.getName() + " Modifications", fa.getModifications() + "");
            }
            keys.addAll(m.keySet());
            return m;
        }).collect(Collectors.toList());
        sb.append(keys.stream().collect(Collectors.joining("\t"))).append("\n");
        for (Map<String, String> m : entries) {
            List<String> l = new LinkedList();
            for (String key : keys) {
                l.add(m.getOrDefault(key, ""));
            }
            sb.append(l.stream().collect(Collectors.joining("\t"))).append("\n");
        }
        return sb.toString();
    }

    protected Page createPage(String title, Optional<Principal> principal) {
        return pageBuilderService.addPrincipalInfo(pageBuilderService.createPage(title), principal);
    }

}
