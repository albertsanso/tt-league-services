package org.cttelsamicsterrassa.data.api.rest.settings;

import io.swagger.v3.oas.annotations.Operation;
import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.settings.create.CreateSettingCommand;
import org.cttelsamicsterrassa.data.core.application.settings.delete.DeleteSettingCommand;
import org.cttelsamicsterrassa.data.core.application.settings.find.FindSettingByCategoryAndNameQuery;
import org.cttelsamicsterrassa.data.core.application.settings.find.FindSettingByIdQuery;
import org.cttelsamicsterrassa.data.core.application.settings.find.FindSettingsByCategoryQuery;
import org.cttelsamicsterrassa.data.core.application.settings.find.FindSettingsQuery;
import org.cttelsamicsterrassa.data.core.application.settings.update.UpdateSettingValueCommand;
import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@SettingsOpenAPIv1Controller
@PreAuthorize("hasRole('ADMIN')")
public class SettingsController {
    private final QueryBus queryBus;
    private final CommandBus commandBus;

    @Autowired
    public SettingsController(QueryBus queryBus, CommandBus commandBus) {
        this.queryBus = queryBus;
        this.commandBus = commandBus;
    }

    @GetMapping
    @Operation(summary = "List settings", description = "Lists all settings, optionally filtered by category")
    public ResponseEntity<?> findSettings(
            @RequestParam(value = "category", required = false) String category) {
        try {
            DomainQueryResponse<List<Setting>> response = category == null
                    ? queryBus.push(new FindSettingsQuery())
                    : queryBus.push(new FindSettingsByCategoryQuery(parseCategory(category).name()));
            return response.isSuccess()
                    ? ResponseEntity.ok(response.getResponse().stream().map(SettingDto::from).toList())
                    : serverError("Settings query failed");
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Find a setting by ID")
    public ResponseEntity<?> findSettingById(@PathVariable("id") UUID id) {
        DomainQueryResponse<Setting> response = queryBus.push(new FindSettingByIdQuery(id));
        return response.isSuccess()
                ? ResponseEntity.ok(SettingDto.from(response.getResponse()))
                : notFound("Setting not found: " + id);
    }

    @GetMapping("/by-category-and-name")
    @Operation(summary = "Find a setting by category and name")
    public ResponseEntity<?> findSettingByCategoryAndName(
            @RequestParam String category,
            @RequestParam String name) {
        if (name == null || name.isBlank()) {
            return badRequest("Setting name must not be blank");
        }
        try {
            DomainQueryResponse<List<Setting>> response = queryBus.push(
                    new FindSettingByCategoryAndNameQuery(parseCategory(category).name(), name.trim()));
            return response.isSuccess()
                    ? ResponseEntity.ok(response.getResponse().stream().map(SettingDto::from).toList())
                    : notFound("Setting not found");
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "Create a setting")
    public ResponseEntity<?> createSetting(@RequestBody CreateSettingRequest request) {
        if (request == null || request.category() == null || request.name() == null
                || request.value() == null || request.name().isBlank()) {
            return badRequest("category, name, and value are required");
        }
        try {
            DomainCommandResponse response = commandBus.push(new CreateSettingCommand(
                    parseCategory(request.category()), request.name().trim(), request.value()));
            return response.isSuccess()
                    ? ResponseEntity.status(HttpStatus.CREATED).body(SettingDto.from((Setting) response.getResponse()))
                    : badRequest(String.valueOf(response.getResponse()));
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a setting value")
    public ResponseEntity<?> updateSetting(
            @PathVariable("id") UUID id,
            @RequestBody UpdateSettingRequest request) {
        if (request == null || request.value() == null) {
            return badRequest("value is required");
        }
        DomainCommandResponse response = commandBus.push(new UpdateSettingValueCommand(id, request.value()));
        return response.isSuccess()
                ? ResponseEntity.ok(SettingDto.from((Setting) response.getResponse()))
                : notFound(String.valueOf(response.getResponse()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a setting")
    public ResponseEntity<?> deleteSetting(@PathVariable("id") UUID id) {
        DomainCommandResponse response = commandBus.push(new DeleteSettingCommand(id));
        return response.isSuccess()
                ? ResponseEntity.noContent().build()
                : notFound(String.valueOf(response.getResponse()));
    }

    private static SettingCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Setting category is required");
        }
        try {
            return SettingCategory.valueOf(category.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown setting category: " + category);
        }
    }

    private static ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    private static ResponseEntity<Map<String, String>> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", message));
    }

    private static ResponseEntity<Map<String, String>> serverError(String message) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", message));
    }
}
