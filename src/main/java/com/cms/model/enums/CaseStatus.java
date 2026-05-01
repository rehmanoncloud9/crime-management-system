package com.cms.model.enums;

/**
 * State machine for CaseFiles.
 * Separated from IncidentStatus to satisfy W-32 and avoid cross-domain state contamination.
 */
public enum CaseStatus {
    OPEN("Open"),
    UNDER_INVESTIGATION("Under Investigation"),
    ARRESTED("Arrested"),
    CHARGED("Charged"),
    IN_TRIAL("In Trial"),
    CLOSED_CONVICTED("Closed (Convicted)"),
    CLOSED_ACQUITTED("Closed (Acquitted)"),
    CLOSED_UNSOLVED("Closed (Unsolved)"),
    REOPENED("Reopened");

    private final String label;

    CaseStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
