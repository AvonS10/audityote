package io.muzoo.ssc.controlmap.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Stable paged-response envelope for list endpoints — a deliberate DTO rather than serializing
 * Spring Data's {@code Page} directly (whose JSON shape is not a guaranteed contract).
 */
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PagedResponse<T> of(Page<?> page, List<T> content) {
        return new PagedResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
