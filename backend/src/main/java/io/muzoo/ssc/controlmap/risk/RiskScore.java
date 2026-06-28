package io.muzoo.ssc.controlmap.risk;

import java.math.BigDecimal;

/** A finding's computed risk score (0.0–10.0, one decimal) and the source that produced it. */
public record RiskScore(BigDecimal value, RiskSource source) {
}
