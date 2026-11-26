package org.lifstools.jgoslin.webapp.domain;

/**
 * @param harmonized edited lines that now parse
 * @param stillFailing size of the failed list after the re-run
 */
public record RerunOutcome(int harmonized, int stillFailing) {
}
