package com.cms.model.enums;

/**
 * State machine for CaseFiles.
 * Separated from IncidentStatus to satisfy W-32 and avoid cross-domain state contamination.
 */
public enum CaseStatus {
    OPEN,
    UNDER_INVESTIGATION,
    ARRESTED,
    CHARGED,
    IN_TRIAL,
    CLOSED_CONVICTED,
    CLOSED_ACQUITTED,
    CLOSED_UNSOLVED,
    REOPENED
}
