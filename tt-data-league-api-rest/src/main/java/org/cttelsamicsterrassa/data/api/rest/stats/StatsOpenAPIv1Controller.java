package org.cttelsamicsterrassa.data.api.rest.stats;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.cttelsamicsterrassa.data.api.rest.ControllerConfig.API_BASE_PATH_V1;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PARAMETER})
@RestController
@RequestMapping(API_BASE_PATH_V1 + "/stats")
@Tag(name = "Stats API", description = "Endpoints for community-wide statistics overviews")
public @interface StatsOpenAPIv1Controller {
}
