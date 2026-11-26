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
package org.lifstools.jgoslin.webapp.controller;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.lifstools.jgoslin.domain.LipidClasses;
import org.lifstools.jgoslin.webapp.config.NewsPropertyConfig;
import org.lifstools.jgoslin.webapp.domain.AppInfo;
import org.lifstools.jgoslin.webapp.domain.GoslinStorageServiceSlots;
import org.lifstools.jgoslin.webapp.domain.HarmonizeForm;
import org.lifstools.jgoslin.webapp.domain.LineTooLongException;
import org.lifstools.jgoslin.webapp.domain.NameEntries;
import org.lifstools.jgoslin.webapp.domain.NameEntry;
import org.lifstools.jgoslin.webapp.domain.ResultView;
import org.lifstools.jgoslin.webapp.domain.TooManyNamesException;
import org.lifstools.jgoslin.webapp.services.PageBuilderService;
import org.lifstools.jgoslin.webapp.services.ResultService;
import org.lifstools.lifs.webapps.common.domain.PageMessage;
import org.lifstools.lifs.webapps.common.domain.StorageServiceSlot;
import org.lifstools.lifs.webapps.common.domain.UserSessionFile;
import org.lifstools.lifs.webapps.common.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/**
 * The input page, its form posts, the documentation page and the TSV download.
 *
 * @author Nils Hoffmann &lt;nils.hoffmann@isas.de&gt;
 */
@Controller
public class LipidNameValidationController {

    private static final Logger log = LoggerFactory.getLogger(LipidNameValidationController.class);
    private static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

    private final AppInfo appInfo;
    private final StorageService storageService;
    private final ResultService resultService;
    private final PageBuilderService pageBuilderService;
    private final LocaleResolver localeResolver;
    private final MessageSource messageSource;
    private final String exampleNames;
    private final int exampleCount;
    private final NewsPropertyConfig newsPropertyConfig;
    private final int maxItemsInRequest;
    /** Largest text accepted, pasted or read from a file: the upload limit, or less if no valid list can be longer. */
    private final long maxTextBytes;

    @Autowired
    public LipidNameValidationController(ResultService resultService, StorageService storageService,
            PageBuilderService pageBuilderService, AppInfo appInfo, LocaleResolver localeResolver,
            MessageSource messageSource, @Value("classpath:lipidnames.txt") Resource lipidnames,
            NewsPropertyConfig newsPropertyConfig, @Value("${jgoslin.maxItemsInRequest}") int maxItemsInRequest,
            @Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize) throws IOException {
        this.resultService = resultService;
        this.storageService = storageService;
        this.pageBuilderService = pageBuilderService;
        this.appInfo = appInfo;
        this.localeResolver = localeResolver;
        this.messageSource = messageSource;
        this.exampleNames = lipidnames.getContentAsString(StandardCharsets.UTF_8);
        this.exampleCount = NameEntries.countNames(exampleNames);
        this.newsPropertyConfig = newsPropertyConfig;
        this.maxItemsInRequest = maxItemsInRequest;
        this.maxTextBytes = Math.min(maxFileSize.toBytes(),
                (long) maxItemsInRequest * (NameEntries.MAX_NAME_LENGTH + 2));
    }

    /** The input page; {@code ?example} fills the text area with the example names, {@code ?lang=de} switches the language. */
    @GetMapping("/")
    public ModelAndView handleHome(HttpServletRequest request, Locale locale) {
        // "?example" has no value, so look for the parameter's presence.
        boolean example = request.getParameterMap().containsKey("example");
        HarmonizeForm form = new HarmonizeForm(example ? exampleNames : "", true);
        return inputPage(form, example ? exampleCount : 0, List.of(), List.of(), locale);
    }

    /** Old bookmarks and reloads of the former result page land on the input page. */
    @GetMapping("/validate")
    public String validateGet() {
        return "redirect:/";
    }

    @PostMapping("/validate")
    public ModelAndView validate(@ModelAttribute("harmonizeForm") HarmonizeForm harmonizeForm, Locale locale) {
        // Posts above server.tomcat.max-http-form-post-size are answered with 413 before they get here.
        return harmonize(harmonizeForm, locale);
    }

    /** Fallback for browsers without JavaScript: the file is posted instead of read into the text area. */
    @PostMapping("/validatefile")
    public ModelAndView validateFile(@RequestParam(value = "file", required = false) MultipartFile file,
            Locale locale) throws IOException {
        String text = file == null ? "" : new String(file.getBytes(), StandardCharsets.UTF_8);
        return harmonize(new HarmonizeForm(text, true), locale);
    }

    private ModelAndView harmonize(HarmonizeForm form, Locale locale) {
        String text = form.getLipidNames();
        if (text != null && text.length() > maxTextBytes) {
            return tooLong(form, locale);
        }
        List<NameEntry> entries;
        try {
            entries = NameEntries.fromText(text, maxItemsInRequest);
        } catch (TooManyNamesException e) {
            return inputPage(form, e.getCount(), List.of(PageMessage.error(
                    message("index.error.tooMany", locale, e.getCount(), e.getMax()))), List.of(), locale);
        } catch (LineTooLongException e) {
            return inputPage(form, NameEntries.countNames(text), List.of(PageMessage.error(
                    message("index.error.lineTooLong", locale, e.getLine(), e.getMax()))), List.of(), locale);
        }
        if (entries.isEmpty()) {
            return inputPage(form, 0, List.of(PageMessage.error(message("index.error.empty", locale))), List.of(), locale);
        }
        if (!form.isSkipInvalid()) {
            List<ResultView.FailedRow> failures = resultService.preview(entries);
            if (!failures.isEmpty()) {
                return inputPage(form, entries.size(), List.of(), failures, locale);
            }
        }
        ResultView view = resultService.harmonize(entries);
        log.info("Harmonized {} names into result {}: {} failed.", view.submitted(), view.id(), view.failed().size());
        RedirectView redirect = new RedirectView("/result/" + view.id(), true);
        redirect.setStatusCode(HttpStatus.SEE_OTHER);
        redirect.setExposeModelAttributes(false);
        return new ModelAndView(redirect);
    }

    /** The text is larger than the upload limit: say so, and do not send it back. */
    private ModelAndView tooLong(HarmonizeForm form, Locale locale) {
        HarmonizeForm empty = new HarmonizeForm("", form.isSkipInvalid());
        return inputPage(empty, 0, List.of(PageMessage.error(
                message("index.error.tooLong", locale, formatSize(maxTextBytes, locale)))), List.of(), locale);
    }

    private ModelAndView inputPage(HarmonizeForm form, int nameCount, List<PageMessage> pageMessages,
            List<ResultView.FailedRow> failures, Locale locale) {
        ModelAndView model = new ModelAndView("index");
        model.addObject("page", pageBuilderService.createPage(message("index.page.title", locale)));
        List<PageMessage> messages = new ArrayList<>();
        if (appInfo.getVersionNumber() != null && appInfo.getVersionNumber().endsWith(SNAPSHOT_SUFFIX)) {
            messages.add(PageMessage.warn(message("index.snapshot", locale)));
        }
        messages.addAll(pageMessages);
        model.addObject("messages", messages);
        model.addObject("harmonizeForm", form);
        model.addObject("failures", failures);
        model.addObject("nameCount", nameCount);
        model.addObject("maxNames", maxItemsInRequest);
        model.addObject("maxTextBytes", maxTextBytes);
        model.addObject("maxTextSize", formatSize(maxTextBytes, locale));
        model.addObject("exampleNames", exampleNames);
        model.addObject("appInfo", appInfo);
        return model;
    }

    /** Binary units, as Spring's DataSize reads spring.servlet.multipart.max-file-size: 512 KB, 1.4 MB, 604 B. */
    static String formatSize(long bytes, Locale locale) {
        if (bytes >= 1024L * 1024L) {
            return String.format(locale, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        if (bytes >= 1024L) {
            return (bytes / 1024L) + " KB";
        }
        return bytes + " B";
    }

    private String message(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }

    @GetMapping("/documentation")
    public ModelAndView handleInfo(@RequestParam(value = "lang", required = false) String language, HttpServletRequest request, HttpServletResponse response, Principal principal) throws IOException {
        if (language != null) {
            localeResolver.setLocale(request, response, Locale.forLanguageTag(language));
        }
        log.debug("Retrieved the following news items: {}", newsPropertyConfig.getNews());
        ModelAndView model = new ModelAndView("documentation");
        model.addObject("page", pageBuilderService.createPage("Goslin Webapp Information"));
        model.addObject("news", newsPropertyConfig.getNews());
        model.addObject("lipidClasses", LipidClasses.getInstance().stream().sorted((t, t1) -> {
            int cmp = t.lipidCategory.name().compareTo(t1.lipidCategory.name());
            if (cmp != 0) {
                return cmp;
            }
            return t.lipidClassName.compareTo(t1.lipidClassName);
        }).collect(Collectors.toList()));
        return model;
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
            StorageServiceSlot slot = GoslinStorageServiceSlots.OUTPUT_TSV_FILE;
            UserSessionFile usf = storageService.load(resultSessionId, slot);
            String ext = Objects.requireNonNullElse(StringUtils.getFilenameExtension(usf.getFilename()), "").toLowerCase();
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

}
