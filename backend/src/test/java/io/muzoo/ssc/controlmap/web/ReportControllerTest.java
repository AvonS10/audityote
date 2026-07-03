package io.muzoo.ssc.controlmap.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.muzoo.ssc.controlmap.domain.Asset;
import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Finding;
import io.muzoo.ssc.controlmap.domain.FindingControlMapping;
import io.muzoo.ssc.controlmap.domain.FindingStatus;
import io.muzoo.ssc.controlmap.domain.Role;
import io.muzoo.ssc.controlmap.domain.Severity;
import io.muzoo.ssc.controlmap.domain.User;
import io.muzoo.ssc.controlmap.repository.ControlRepository;
import io.muzoo.ssc.controlmap.repository.FindingControlMappingRepository;
import io.muzoo.ssc.controlmap.repository.FindingRepository;
import io.muzoo.ssc.controlmap.repository.FrameworkRepository;
import io.muzoo.ssc.controlmap.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Report export — CSV body/headers (a clean table, no meta block), the PDF rendering (provenance +
 * footer + word-wrap), the risk-cell convention, framework validation, unsupported-format and auth
 * handling.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportControllerTest {

    private static final String ANALYST = "analyst@report.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private FindingRepository findings;
    @Autowired private FrameworkRepository frameworks;
    @Autowired private ControlRepository controls;
    @Autowired private FindingControlMappingRepository mappings;

    @BeforeEach
    void setUp() {
        User owner = users.save(new User(ANALYST, "Report Analyst", "x", Role.ANALYST));
        // A long title with a comma exercises CSV quoting *and* the PDF word-wrap (no truncation).
        Finding f = findings.save(new Finding("CM-RPT-0001", "Injection, stored in the customer statement export view", "d",
                Severity.HIGH, new BigDecimal("7.5"), owner, new Asset("Edge", null, null, null)));
        f.setStatus(FindingStatus.OPEN);
        findings.save(f);
        // No CVSS → the register's Risk column falls back to the severity-derived score, marked "(der)".
        findings.save(new Finding("CM-RPT-0002", "Weak TLS configuration on legacy endpoint", "d",
                Severity.MEDIUM, null, owner, new Asset("Edge", null, null, null)));
        Long owaspId = frameworks.findBySlug("owasp").orElseThrow().getId();
        Control a05 = controls.findByFramework_IdAndCode(owaspId, "A05").orElseThrow();
        mappings.save(new FindingControlMapping(f, a05));
    }

    @Test
    @WithMockUser(username = ANALYST)
    void findingsCsvDownloadsWithHeadersAndEscaping() throws Exception {
        mockMvc.perform(get("/api/reports/findings").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("findings-")))
                .andExpect(content().string(containsString("Reference,Title,Severity,CVSS,Risk,Status,Owner,Asset,Mapped controls,Updated")))
                .andExpect(content().string(containsString("CM-RPT-0001")))
                // comma in the title forces quoting; the mapped control is joined as framework:code.
                .andExpect(content().string(containsString("\"Injection, stored in the customer statement export view\"")))
                .andExpect(content().string(containsString("owasp:A05")))
                // CVSS-backed risk repeats the base score; without CVSS the derived score carries "(der)".
                .andExpect(content().string(containsString("high,7.5,7.5,open")))
                .andExpect(content().string(containsString("medium,,5.0 (der),open")))
                // the CSV stays a clean header+rows table — the provenance block is PDF-only.
                .andExpect(content().string(not(containsString("Generated by"))));
    }

    @Test
    @WithMockUser(username = ANALYST)
    void findingsDefaultsToCsvWhenFormatOmitted() throws Exception {
        mockMvc.perform(get("/api/reports/findings"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    @WithMockUser(username = ANALYST)
    void coverageCsvDownloadsForFramework() throws Exception {
        mockMvc.perform(get("/api/reports/coverage").param("framework", "owasp").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("coverage-owasp-")))
                .andExpect(content().string(containsString("Code,Control,Findings,Highest severity,At risk")))
                .andExpect(content().string(containsString("A05")));
    }

    @Test
    @WithMockUser(username = ANALYST)
    void findingsPdfDownloadsAsValidPdf() throws Exception {
        byte[] body = mockMvc.perform(get("/api/reports/findings").param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("findings-")))
                .andExpect(header().string("Content-Disposition", containsString(".pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        // %PDF- magic number proves a real PDF document was produced.
        assertThat(body).isNotEmpty();
        assertThat(new String(body, 0, Math.min(5, body.length), StandardCharsets.US_ASCII)).startsWith("%PDF-");
        // Parse it back and confirm the table rendered the header, the finding, the summary line, and
        // — crucially — the *tail* of the long title, proving cells word-wrap instead of truncating.
        try (PDDocument doc = Loader.loadPDF(body)) {
            String text = new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
            assertThat(text).contains("Reference").contains("Severity").contains("CM-RPT-0001");
            assertThat(text).contains("findings"); // the summary line ("N findings · …")
            assertThat(text).contains("statement export"); // title words 6-7 — present only if it wrapped vs truncated
            assertThat(text).contains("5.0 (der)"); // severity-derived risk score is marked
            assertThat(text).contains("Generated by Report Analyst (Analyst)"); // provenance meta block
            assertThat(text).contains("AuditYote - Findings register").contains("Page 1 of"); // footer pass
        }
    }

    @Test
    @WithMockUser(username = ANALYST)
    void coveragePdfDownloadsForFramework() throws Exception {
        mockMvc.perform(get("/api/reports/coverage").param("framework", "owasp").param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("coverage-owasp-")));
    }

    @Test
    @WithMockUser(username = ANALYST)
    void unsupportedFormatIs400() throws Exception {
        mockMvc.perform(get("/api/reports/findings").param("format", "xml"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = ANALYST)
    void unknownFrameworkIs404() throws Exception {
        mockMvc.perform(get("/api/reports/coverage").param("framework", "nope").param("format", "csv"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = ANALYST)
    void auditLogCsvIncludesEvents() throws Exception {
        // Create via the API so an audit "created" event is recorded for the new finding.
        String created = mockMvc.perform(post("/api/findings").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Audit export probe\",\"severity\":\"high\",\"asset\":{\"name\":\"x\"}}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(created, "$.reference");

        mockMvc.perform(get("/api/reports/audit").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("audit-log-")))
                .andExpect(content().string(containsString("Timestamp,Finding,Title,Actor,Actor email,Action,From,To,Detail")))
                .andExpect(content().string(containsString(reference)))
                // the actor's immutable email pins identity next to the (editable) display name.
                .andExpect(content().string(containsString("Report Analyst," + ANALYST + ",created")))
                // fixed-precision UTC timestamps — sortable, seconds kept (ordering evidence).
                .andExpect(content().string(matchesRegex("(?s).*\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} UTC,.*")));
    }

    @Test
    @WithMockUser(username = ANALYST)
    void auditLogPdfCarriesProvenanceAndFooter() throws Exception {
        byte[] body = mockMvc.perform(get("/api/reports/audit").param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("audit-log-")))
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = Loader.loadPDF(body)) {
            String text = new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
            assertThat(text).contains("Audit log").contains("Actor email");
            assertThat(text).contains("Generated by Report Analyst (Analyst)");
            assertThat(text).contains("AuditYote - Audit log").contains("Page 1 of");
        }
    }

    @Test
    void riskCellMarksDerivedScoresOnly() {
        assertThat(FindingMapper.riskToReportCell(new BigDecimal("7.5"), "cvss")).isEqualTo("7.5");
        assertThat(FindingMapper.riskToReportCell(new BigDecimal("5.0"), "severity")).isEqualTo("5.0 (der)");
        assertThat(FindingMapper.riskToReportCell(null, "severity")).isEmpty();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/reports/findings").param("format", "csv"))
                .andExpect(status().isUnauthorized());
    }
}
