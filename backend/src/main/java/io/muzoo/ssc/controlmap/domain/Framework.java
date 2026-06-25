package io.muzoo.ssc.controlmap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A compliance framework, e.g. ("ISO/IEC 27001", "2022"). Owns its {@link Control}s. */
@Entity
@Table(name = "framework", uniqueConstraints = @UniqueConstraint(columnNames = {"name", "version"}))
public class Framework {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable key used by the API/UI (e.g. {@code iso27001}), distinct from the display name. */
    @Column(nullable = false, unique = true, length = 50)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String version;

    protected Framework() {
        // JPA
    }

    public Framework(String slug, String name, String version) {
        this.slug = slug;
        this.name = name;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
