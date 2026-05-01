package com.cms.model.enums;

public enum BloodGroup {
    A_POS("A+"), A_NEG("A-"),
    B_POS("B+"), B_NEG("B-"),
    O_POS("O+"), O_NEG("O-"),
    AB_POS("AB+"), AB_NEG("AB-"),
    UNKNOWN("Unknown");

    private final String label;

    BloodGroup(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
