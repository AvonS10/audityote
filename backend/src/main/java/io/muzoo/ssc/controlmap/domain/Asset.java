package io.muzoo.ssc.controlmap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * The asset a finding concerns: a required {@code name} plus optional environment, component, and URL.
 * Embedded into {@link Finding}; columns are prefixed {@code asset_} to keep the finding table clear.
 */
@Embeddable
public class Asset {

    @Column(name = "asset_name", nullable = false)
    private String name;

    @Column(name = "asset_env")
    private String env;

    @Column(name = "asset_component")
    private String component;

    @Column(name = "asset_url")
    private String url;

    protected Asset() {
        // JPA
    }

    public Asset(String name, String env, String component, String url) {
        this.name = name;
        this.env = env;
        this.component = component;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
