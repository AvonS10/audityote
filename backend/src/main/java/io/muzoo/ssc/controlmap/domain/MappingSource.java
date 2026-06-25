package io.muzoo.ssc.controlmap.domain;

/**
 * Provenance of a finding↔control mapping. Defaults to MANUAL; set to AI_SUGGESTED only when an
 * analyst accepts an AI suggestion (stretch feature, PLAN §3/§4) — AI never auto-applies mappings.
 */
public enum MappingSource {
    MANUAL,
    AI_SUGGESTED
}
