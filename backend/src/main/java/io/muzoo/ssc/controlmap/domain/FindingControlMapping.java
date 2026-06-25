package io.muzoo.ssc.controlmap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Association entity joining a {@link Finding} to a {@link Control} — the many-to-many heart of the
 * app, modelled explicitly so it can carry provenance. {@code source} defaults to MANUAL; the
 * {@code ai*} fields are populated only when an analyst accepts an AI suggestion (PLAN §3/§4).
 */
@Entity
@Table(name = "finding_control_mapping",
        uniqueConstraints = @UniqueConstraint(columnNames = {"finding_id", "control_id"}))
public class FindingControlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finding_id", nullable = false)
    private Finding finding;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "control_id", nullable = false)
    private Control control;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MappingSource source = MappingSource.MANUAL;

    @Column(name = "ai_confidence")
    private Double aiConfidence;

    @Column(name = "ai_rationale", columnDefinition = "text")
    private String aiRationale;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    protected FindingControlMapping() {
        // JPA
    }

    /** Manual mapping (the default path). */
    public FindingControlMapping(Finding finding, Control control) {
        this.finding = finding;
        this.control = control;
        this.source = MappingSource.MANUAL;
    }

    public Long getId() {
        return id;
    }

    public Finding getFinding() {
        return finding;
    }

    public void setFinding(Finding finding) {
        this.finding = finding;
    }

    public Control getControl() {
        return control;
    }

    public void setControl(Control control) {
        this.control = control;
    }

    public MappingSource getSource() {
        return source;
    }

    public void setSource(MappingSource source) {
        this.source = source;
    }

    public Double getAiConfidence() {
        return aiConfidence;
    }

    public void setAiConfidence(Double aiConfidence) {
        this.aiConfidence = aiConfidence;
    }

    public String getAiRationale() {
        return aiRationale;
    }

    public void setAiRationale(String aiRationale) {
        this.aiRationale = aiRationale;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }
}
