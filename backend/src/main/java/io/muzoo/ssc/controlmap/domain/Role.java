package io.muzoo.ssc.controlmap.domain;

/**
 * Application role (authorization enforced server-side via Spring Security, chunk #7). Three clean
 * lanes: ANALYST authors findings, REVIEWER signs them off, ADMIN administers users (#admin).
 */
public enum Role {
    ANALYST,
    REVIEWER,
    ADMIN
}
