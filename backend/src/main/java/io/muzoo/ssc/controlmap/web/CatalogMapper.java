package io.muzoo.ssc.controlmap.web;

import io.muzoo.ssc.controlmap.domain.Control;
import io.muzoo.ssc.controlmap.domain.Framework;
import io.muzoo.ssc.controlmap.web.dto.ControlResponse;
import io.muzoo.ssc.controlmap.web.dto.FrameworkResponse;
import org.springframework.stereotype.Component;

/**
 * Maps catalog entities to their API DTOs (the DTO + Mapper pattern). Keeps JPA entities off the
 * wire and translates the entity shape to the API shape (e.g. a control exposes its framework slug,
 * not the nested entity).
 */
@Component
public class CatalogMapper {

    public FrameworkResponse toFrameworkResponse(Framework framework) {
        return new FrameworkResponse(framework.getId(), framework.getSlug(), framework.getName(), framework.getVersion());
    }

    public ControlResponse toControlResponse(Control control) {
        return new ControlResponse(
                control.getId(),
                control.getFramework().getSlug(),
                control.getCode(),
                control.getTitle(),
                control.getDescription(),
                control.getCategory());
    }
}
