package org.lifstools.jgoslin.webapp.domain;

import java.util.List;
import java.util.UUID;

/**
 * A stored result parsed for display: harmonized and failed rows, both in line order.
 * {@code submitted} counts the active (not removed) entries.
 */
public record ResultView(UUID id, int revision, int submitted, List<Row> harmonized, List<FailedRow> failed) {

    public record Row(NameEntry entry, ValidationResult result) {
    }

    public record FailedRow(NameEntry entry, ValidationResult result, FailureExplanation explanation) {
    }
}
