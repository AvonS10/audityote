package io.muzoo.ssc.controlmap.risk;

import java.util.Locale;

/** Which strategy produced a finding's risk score — surfaced to the client so a derived score is marked. */
public enum RiskSource {
    /** The score is the finding's own CVSS base score. */
    CVSS,
    /** No CVSS present — the score was derived from the severity band. */
    SEVERITY;

    /** Lowercase wire form for the API DTO ({@code cvss} / {@code severity}). */
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
