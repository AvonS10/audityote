package io.muzoo.ssc.controlmap.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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

/** Report export — CSV body/headers, framework validation, unsupported-format and auth handling. */
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
                .andExpect(content().string(containsString("Reference,Title,Severity,CVSS,Status,Owner,Asset,Mapped controls,Updated")))
                .andExpect(content().string(containsString("CM-RPT-0001")))
                // comma in the title forces quoting; the mapped control is joined as framework:code.
                .andExpect(content().string(containsString("\"Injection, stored in the customer statement export view\"")))
                .andExpect(content().string(containsString("owasp:A05")));
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
                .andExpect(content().string(containsString("Timestamp,Finding,Title,Actor,Action,From,To,Detail")))
                .andExpect(content().string(containsString(reference)))
                .andExpect(content().string(containsString("created")));
    }

    @Test
    @WithMockUser(username = ANALYST)
    void auditLogPdfDownloads() throws Exception {
        mockMvc.perform(get("/api/reports/audit").param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("audit-log-")));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/reports/findings").param("format", "csv"))
                .andExpect(status().isUnauthorized());
    }
}
