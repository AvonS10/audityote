package io.muzoo.ssc.controlmap.domain;

/**
 * Finding severity. Stored as the enum name; the API/UI use lowercase (the DTO/Mapper owns casing).
 * When a CVSS score is present, severity must equal its CVSS 3.x band (enforced in a later chunk).
 */
public enum Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}
