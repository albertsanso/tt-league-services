package org.cttelsamicsterrassa.data.api.rest.search;

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
@RequestMapping(API_BASE_PATH_V1 + "/search")
@Tag(name = "Global search API", description = "Endpoint for searching players, clubs and matches by free text")
public @interface SearchOpenAPIv1Controller {
}
