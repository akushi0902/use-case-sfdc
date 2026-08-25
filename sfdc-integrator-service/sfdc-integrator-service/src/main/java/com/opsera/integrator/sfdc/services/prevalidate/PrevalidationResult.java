package com.opsera.integrator.sfdc.services.prevalidate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregated result of a package XML or component prevalidation pass.
 *
 * <p>Callers should check {@link #hasHardFailures()} before accepting a release submission.
 * Hard failures must block submission; warnings may be forwarded to the submitter as advisory
 * information without blocking.
 *
 * <p>Use {@link #builder()} to construct instances.
 */
public final class PrevalidationResult {

    private final List<PrevalidationFinding> findings;

    private PrevalidationResult(List<PrevalidationFinding> findings) {
        this.findings = Collections.unmodifiableList(new ArrayList<>(findings));
    }

    /** Returns all findings (warnings and hard failures). */
    public List<PrevalidationFinding> getFindings() {
        return findings;
    }

    /** Returns true if any finding has severity {@link FindingSeverity#HARD_FAILURE}. */
    public boolean hasHardFailures() {
        return findings.stream().anyMatch(PrevalidationFinding::isHardFailure);
    }

    /** Returns only {@link FindingSeverity#HARD_FAILURE} findings. */
    public List<PrevalidationFinding> hardFailures() {
        return findings.stream()
                .filter(PrevalidationFinding::isHardFailure)
                .collect(Collectors.toList());
    }

    /** Returns only {@link FindingSeverity#WARNING} findings. */
    public List<PrevalidationFinding> warnings() {
        return findings.stream()
                .filter(f -> FindingSeverity.WARNING.equals(f.getSeverity()))
                .collect(Collectors.toList());
    }

    /** Returns true if there are no findings of any severity. */
    public boolean isPassed() {
        return findings.isEmpty();
    }

    @Override
    public String toString() {
        return "PrevalidationResult{findings=" + findings.size()
                + ", hasHardFailures=" + hasHardFailures() + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<PrevalidationFinding> findings = new ArrayList<>();

        private Builder() {}

        public Builder addFinding(PrevalidationFinding finding) {
            if (finding != null) {
                findings.add(finding);
            }
            return this;
        }

        public Builder addFindings(List<PrevalidationFinding> list) {
            if (list != null) {
                list.forEach(this::addFinding);
            }
            return this;
        }

        public PrevalidationResult build() {
            return new PrevalidationResult(findings);
        }
    }
}
