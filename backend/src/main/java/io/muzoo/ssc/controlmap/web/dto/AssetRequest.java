package io.muzoo.ssc.controlmap.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The affected asset on a create/edit request: a required name plus optional context. */
public record AssetRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String env,
        @Size(max = 255) String component,
        @Size(max = 255) String url) {
}
