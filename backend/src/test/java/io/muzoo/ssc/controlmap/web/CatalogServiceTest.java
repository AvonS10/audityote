package io.muzoo.ssc.controlmap.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit test for the natural control-code ordering used by the catalog and coverage screens. */
class CatalogServiceTest {

    @Test
    void sortsControlCodesInNaturalNumericOrder() {
        List<String> codes = new java.util.ArrayList<>(List.of(
                "A.5.10", "A.5.2", "A.5.1", "A.8.34", "A.8.5", "A.6.3", "A.7.14", "A.7.2"));
        codes.sort(CatalogService::compareNatural);
        assertThat(codes).containsExactly(
                "A.5.1", "A.5.2", "A.5.10", "A.6.3", "A.7.2", "A.7.14", "A.8.5", "A.8.34");
    }

    @Test
    void sortsZeroPaddedCodesByValueNotString() {
        List<String> codes = new java.util.ArrayList<>(List.of("A10", "A01", "A02", "A09"));
        codes.sort(CatalogService::compareNatural);
        assertThat(codes).containsExactly("A01", "A02", "A09", "A10");
    }

    @Test
    void keepsEachFrameworkPrefixGroupedForNistStyleCodes() {
        List<String> codes = new java.util.ArrayList<>(List.of("GV.RM", "DE.CM", "GV.OC", "ID.AM"));
        codes.sort(CatalogService::compareNatural);
        // Alphabetical by prefix; all codes of one prefix stay contiguous.
        assertThat(codes).containsExactly("DE.CM", "GV.OC", "GV.RM", "ID.AM");
    }
}
