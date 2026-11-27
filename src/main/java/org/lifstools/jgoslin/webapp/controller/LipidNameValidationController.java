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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.lifstools.jgoslin.domain.LipidCategory;
import org.lifstools.jgoslin.domain.LipidClasses;
import org.lifstools.jgoslin.webapp.config.DatabaseProperties;
import org.lifstools.jgoslin.webapp.config.NewsPropertyConfig;
import org.lifstools.jgoslin.webapp.config.ParserProperties;
import org.lifstools.jgoslin.webapp.domain.AppInfo;
import org.lifstools.jgoslin.webapp.domain.CategoryCoverage;
import org.lifstools.jgoslin.webapp.domain.DatabaseCoverage;
import org.lifstools.jgoslin.webapp.domain.GoslinStorageServiceSlots;
import org.lifstools.jgoslin.webapp.domain.HarmonizeForm;
import org.lifstools.jgoslin.webapp.domain.LineTooLongException;
import org.lifstools.jgoslin.webapp.domain.NameEntries;
import org.lifstools.jgoslin.webapp.domain.NameEntry;
import org.lifstools.jgoslin.webapp.domain.RerunForm;
import org.lifstools.jgoslin.webapp.domain.RerunOutcome;
import org.lifstools.jgoslin.webapp.domain.ResultTableRow;
import org.lifstools.jgoslin.webapp.domain.ResultView;
import org.lifstools.jgoslin.webapp.domain.TooManyNamesException;
import org.lifstools.jgoslin.webapp.services.ExternalDatabaseMappingLoader;
import org.lifstools.jgoslin.webapp.services.LipidNameValidationService;
import org.lifstools.jgoslin.webapp.services.PageBuilderService;
import org.lifstools.jgoslin.webapp.services.ResultService;
import org.lifstools.jgoslin.webapp.services.UsageCounter;
import org.lifstools.lifs.webapps.common.domain.PageMessage;
import org.lifstools.lifs.webapps.common.domain.StorageServiceSlot;
import org.lifstools.lifs.webapps.common.domain.UserSessionFile;
import org.lifstools.lifs.webapps.common.service.StorageService;
import org.lifstools.lifs.webapps.common.service.storage.StorageFileNotFoundException;
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
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
 * The input page and its form posts, the result page with re-run and remove, the documentation page and the
 * TSV download.
 *
 * @author Nils Hoffmann &lt;nils.hoffmann@isas.de&gt;
 */
@Controller
public class LipidNameValidationController {

    private static final Logger log = LoggerFactory.getLogger(LipidNameValidationController.class);
    private static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";
    private static final Pattern PAIR = Pattern.compile("(\\d{1,6}),(\\d{1,6})");
    private static final MediaType TSV = new MediaType("text", "tab-separated-values", StandardCharsets.UTF_8);

    private final AppInfo appInfo;
    private final StorageService storageService;
    private final ResultService resultService;
    private final UsageCounter usageCounter;
    private final PageBuilderService pageBuilderService;
    private final LocaleResolver localeResolver;
    private final MessageSource messageSource;
    private final String exampleNames;
    private final int exampleCount;
    private final NewsPropertyConfig newsPropertyConfig;
    private final int maxItemsInRequest;
    private final ExternalDatabaseMappingLoader databaseLoader;
    private final DatabaseProperties databaseProperties;
    /** Days a result is kept; the same property the clean-up of old results reads. */
    private final int retentionDays;
    /** Largest text accepted, pasted or read from a file: the upload limit, or less if no valid list can be longer. */
    private final long maxTextBytes;
    /** The parse time limit per name in seconds, for the hint on names that took longer. */
    private final java.math.BigDecimal timeLimitSeconds;

    @Autowired
    public LipidNameValidationController(ResultService resultService, UsageCounter usageCounter,
            StorageService storageService,
            PageBuilderService pageBuilderService, AppInfo appInfo, LocaleResolver localeResolver,
            MessageSource messageSource, @Value("classpath:lipidnames.txt") Resource lipidnames,
            NewsPropertyConfig newsPropertyConfig, @Value("${jgoslin.maxItemsInRequest}") int maxItemsInRequest,
            @Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize,
            @Value("${minCleanupAge}") int retentionDays, ParserProperties parserProperties,
            ExternalDatabaseMappingLoader databaseLoader, DatabaseProperties databaseProperties) throws IOException {
        this.databaseLoader = databaseLoader;
        this.databaseProperties = databaseProperties;
        this.resultService = resultService;
        this.usageCounter = usageCounter;
        this.storageService = storageService;
        this.pageBuilderService = pageBuilderService;
        this.appInfo = appInfo;
        this.localeResolver = localeResolver;
        this.messageSource = messageSource;
        this.exampleNames = lipidnames.getContentAsString(StandardCharsets.UTF_8);
        this.exampleCount = NameEntries.countNames(exampleNames);
        this.newsPropertyConfig = newsPropertyConfig;
        this.maxItemsInRequest = maxItemsInRequest;
        this.retentionDays = retentionDays;
        this.maxTextBytes = Math.min(maxFileSize.toBytes(),
                (long) maxItemsInRequest * (NameEntries.MAX_NAME_LENGTH + 2));
        this.timeLimitSeconds = new java.math.BigDecimal(LipidNameValidationService.seconds(parserProperties.timeLimit()));
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
        String text = file == null ? "" : decode(file.getBytes());
        return harmonize(new HarmonizeForm(text, true), locale);
    }

    /**
     * The text of an uploaded file: UTF-16 if it starts with a UTF-16 byte-order mark (Excel's "Unicode text"),
     * otherwise UTF-8. The mark itself is dropped; a UTF-8 one is dropped when the lines are read.
     */
    static String decode(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        return new String(bytes, StandardCharsets.UTF_8);
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
        // One count per submitted list, also when the check below sends it back.
        usageCounter.countValidation();
        if (!form.isSkipInvalid()) {
            List<ResultView.FailedRow> failures = resultService.preview(entries);
            if (!failures.isEmpty()) {
                return inputPage(form, entries.size(), List.of(), failures, locale);
            }
        }
        ResultView view = resultService.harmonize(entries);
        log.info("Harmonized {} names into result {}: {} failed.", view.submitted(), view.id(), view.failed().size());
        return seeOther("/result/" + view.id());
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
        List<PageMessage> messages = baseMessages(locale);
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
        model.addObject("timeLimitSeconds", timeLimitSeconds);
        return model;
    }

    /** The messages every page starts with: the warning on development versions. */
    private List<PageMessage> baseMessages(Locale locale) {
        List<PageMessage> messages = new ArrayList<>();
        if (appInfo.getVersionNumber() != null && appInfo.getVersionNumber().endsWith(SNAPSHOT_SUFFIX)) {
            messages.add(PageMessage.warn(message("index.snapshot", locale)));
        }
        return messages;
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

    /** About Goslin: citation, news, contributing, acknowledgements, privacy and version. */
    @GetMapping("/about")
    public ModelAndView about(@RequestParam(value = "lang", required = false) String language, HttpServletRequest request, HttpServletResponse response) {
        if (language != null) {
            localeResolver.setLocale(request, response, Locale.forLanguageTag(language));
        }
        ModelAndView model = new ModelAndView("about");
        model.addObject("page", pageBuilderService.createPage("About Goslin"));
        model.addObject("news", newsPropertyConfig.getNews());
        model.addObject("retentionDays", retentionDays);
        return model;
    }

    @GetMapping("/documentation")
    public ModelAndView handleInfo(@RequestParam(value = "lang", required = false) String language, HttpServletRequest request, HttpServletResponse response, Principal principal) throws IOException {
        if (language != null) {
            localeResolver.setLocale(request, response, Locale.forLanguageTag(language));
        }
        log.debug("Retrieved the following news items: {}", newsPropertyConfig.getNews());
        ModelAndView model = new ModelAndView("documentation");
        model.addObject("page", pageBuilderService.createPage("Goslin Webapp Information"));
        model.addObject("lipidClasses", LipidClasses.getInstance().stream().sorted((t, t1) -> {
            int cmp = t.lipidCategory.name().compareTo(t1.lipidCategory.name());
            if (cmp != 0) {
                return cmp;
            }
            return t.lipidClassName.compareTo(t1.lipidClassName);
        }).collect(Collectors.toList()));
        List<DatabaseCoverage> coverage = databaseLoader.getCoverage();
        Map<DatabaseCoverage.Database, String> versions = new EnumMap<>(DatabaseCoverage.Database.class);
        for (DatabaseCoverage.Database database : DatabaseCoverage.Database.values()) {
            versions.put(database, databaseProperties.versionOf(database));
        }
        model.addObject("databases", coverage);
        model.addObject("databaseVersions", versions);
        model.addObject("coverageCategories", CategoryCoverage.of(coverage));
        return model;
    }

    // ---------- result page ----------

    /**
     * The result page: names that failed first, then the harmonized table. {@code rerun=h,f} shows the outcome
     * of a re-run, {@code tooLong=n,line} the corrections that were too long to re-run.
     */
    @GetMapping("/result/{id}")
    public ModelAndView result(@PathVariable String id, @RequestParam(value = "rerun", required = false) String rerun,
            @RequestParam(value = "tooLong", required = false) String tooLong, Locale locale) {
        ResultView view = resultService.view(resultId(id)).orElseThrow(ResultNotFoundException::new);
        List<PageMessage> messages = baseMessages(locale);
        int[] outcome = parsePair(rerun);
        String outcomeText = null;
        if (outcome != null) {
            outcomeText = outcome[0] == 0 && outcome[1] == 0
                    ? message("result.rerun.nothing", locale)
                    : message("result.rerun.outcome", locale, outcome[0], outcome[1]);
            messages.add(outcome[0] > 0 ? PageMessage.ok(outcomeText) : PageMessage.info(outcomeText));
        }
        int[] skipped = parsePair(tooLong);
        if (skipped != null && skipped[0] > 0) {
            messages.add(PageMessage.error(message("result.error.tooLong", locale, skipped[0], skipped[1],
                    NameEntries.MAX_NAME_LENGTH)));
        }
        List<ResultTableRow> rows = view.harmonized().stream().map(ResultTableRow::of).toList();
        ModelAndView model = new ModelAndView("result");
        model.addObject("page", pageBuilderService.createPage(message("result.page.title", locale)));
        model.addObject("messages", messages);
        model.addObject("result", view);
        model.addObject("rows", rows);
        model.addObject("levels", rows.stream().sorted(Comparator.comparingInt(ResultTableRow::levelOrder))
                .map(ResultTableRow::level).distinct().toList());
        Map<String, String> categories = new LinkedHashMap<>();
        rows.stream().sorted(Comparator.comparingInt(r -> LipidCategory.valueOf(r.category()).ordinal()))
                .forEach(r -> categories.putIfAbsent(r.category(), r.categoryName()));
        model.addObject("categories", categories);
        model.addObject("maxNameLength", NameEntries.MAX_NAME_LENGTH);
        model.addObject("timeLimitSeconds", timeLimitSeconds);
        model.addObject("notFound", false);
        // goslin.js moves the focus to this callout after a re-run, so that screen readers announce it.
        model.addObject("outcome", outcomeText);
        return model;
    }

    /** Re-runs the corrected names (inputs named {@code edit[line]}) and shows the result with the outcome. */
    @PostMapping("/result/{id}/rerun")
    public ModelAndView rerun(@PathVariable String id, @ModelAttribute RerunForm rerunForm) {
        UUID uuid = resultId(id);
        Corrections corrections = Corrections.of(rerunForm.getEdit());
        RerunOutcome outcome = resultService.rerun(uuid, corrections.accepted()).orElseThrow(ResultNotFoundException::new);
        log.info("Re-ran {} corrections in result {}: {} harmonized, {} still failing.",
                corrections.accepted().size(), uuid, outcome.harmonized(), outcome.stillFailing());
        return seeOther("/result/" + uuid + "?rerun=" + outcome.harmonized() + "," + outcome.stillFailing()
                + corrections.tooLongParam("&"));
    }

    /**
     * Removes one line. The "Remove" buttons sit inside the re-run form, so the corrections typed into the other
     * rows are posted too; they are re-run first so that they are not lost.
     */
    @PostMapping("/result/{id}/remove")
    public ModelAndView remove(@PathVariable String id, @RequestParam("line") int line,
            @ModelAttribute RerunForm rerunForm) {
        UUID uuid = resultId(id);
        Corrections corrections = Corrections.of(rerunForm.getEdit());
        Map<Integer, String> edits = corrections.acceptedWithout(line);
        int harmonized = 0;
        if (!edits.isEmpty()) {
            harmonized = resultService.rerun(uuid, edits).orElseThrow(ResultNotFoundException::new).harmonized();
        }
        ResultView view = resultService.remove(uuid, line).orElseThrow(ResultNotFoundException::new);
        log.info("Removed line {} from result {}.", line, uuid);
        String query = harmonized > 0 ? "?rerun=" + harmonized + "," + view.failed().size() : "";
        query += corrections.tooLongParam(query.isEmpty() ? "?" : "&");
        return seeOther("/result/" + uuid + query);
    }

    /** Unknown, expired or malformed result ids: the result page says so, with status 404. */
    @ExceptionHandler(ResultNotFoundException.class)
    public ModelAndView resultNotFound(Locale locale) {
        ModelAndView model = new ModelAndView("result");
        model.setStatus(HttpStatus.NOT_FOUND);
        model.addObject("page", pageBuilderService.createPage(message("result.notFound.title", locale)));
        model.addObject("messages", baseMessages(locale));
        model.addObject("notFound", true);
        model.addObject("retentionDays", retentionDays);
        return model;
    }

    /** Thrown for an unknown, expired or malformed result id. */
    static class ResultNotFoundException extends RuntimeException {

        ResultNotFoundException() {
            super(null, null, false, false);
        }
    }

    /**
     * Posted corrections, split into those that can be re-run and those longer than
     * {@link NameEntries#MAX_NAME_LENGTH} after normalising, which are neither saved nor parsed.
     */
    record Corrections(Map<Integer, String> accepted, List<Integer> tooLong) {

        Corrections {
            accepted = Map.copyOf(accepted);
            tooLong = List.copyOf(tooLong);
        }

        /** The accepted corrections without the one for the given line. */
        Map<Integer, String> acceptedWithout(int line) {
            Map<Integer, String> edits = new HashMap<>(accepted);
            edits.remove(line);
            return edits;
        }

        static Corrections of(Map<Integer, String> edits) {
            Map<Integer, String> accepted = new HashMap<>();
            List<Integer> tooLong = new ArrayList<>();
            edits.forEach((line, text) -> {
                if (line == null || text == null) {
                    return;
                }
                if (NameEntries.normalize(text).length() > NameEntries.MAX_NAME_LENGTH) {
                    tooLong.add(line);
                } else {
                    accepted.put(line, text);
                }
            });
            Collections.sort(tooLong);
            return new Corrections(accepted, tooLong);
        }

        /** {@code tooLong=<count>,<first line>}, or nothing; bounded in length whatever was posted. */
        String tooLongParam(String separator) {
            return tooLong.isEmpty() ? "" : separator + "tooLong=" + tooLong.size() + "," + tooLong.get(0);
        }
    }

    private static UUID resultId(String id) {
        try {
            UUID uuid = UUID.fromString(id);
            if (uuid.toString().equals(id.toLowerCase(Locale.ROOT))) {
                return uuid;
            }
        } catch (IllegalArgumentException e) {
            // not a UUID
        }
        throw new ResultNotFoundException();
    }

    /** Two non-negative numbers, "12,3"; null for anything else. */
    private static int[] parsePair(String value) {
        if (value == null) {
            return null;
        }
        Matcher m = PAIR.matcher(value);
        if (!m.matches()) {
            return null;
        }
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
    }

    private static ModelAndView seeOther(String url) {
        RedirectView redirect = new RedirectView(url, true);
        redirect.setStatusCode(HttpStatus.SEE_OTHER);
        redirect.setExposeModelAttributes(false);
        return new ModelAndView(redirect);
    }

    /** The table of a result as TSV, named goslin-result-&lt;first 8 characters of the id&gt;.tsv. */
    @GetMapping("/download/{sessionId}/{storageServiceSlot}")
    @ResponseBody
    public ResponseEntity<Resource> getResults(@PathVariable String sessionId, @PathVariable String storageServiceSlot) {
        UUID id = resultId(sessionId);
        if (!"OUTPUT_TSV_FILE".equals(storageServiceSlot)) {
            throw new ResultNotFoundException();
        }
        StorageServiceSlot slot = GoslinStorageServiceSlots.OUTPUT_TSV_FILE;
        Resource file;
        try {
            UserSessionFile usf = storageService.load(id, slot);
            // load falls back to another slot's file (the stored entries) when the TSV is missing
            if (usf == null || !(slot.getName() + slot.getExtension()).equals(usf.getFilename())) {
                throw new ResultNotFoundException();
            }
            file = storageService.loadAsResource(usf, slot);
        } catch (StorageFileNotFoundException e) {
            throw new ResultNotFoundException();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"goslin-result-" + id.toString().substring(0, 8) + ".tsv\"")
                .contentType(TSV)
                .body(file);
    }

}
