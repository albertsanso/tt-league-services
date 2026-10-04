package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pipeline/pending-triggers")
@Tag(name = "Runs")
@SecurityRequirement(name = "bearer")
class PendingTriggersController {

    private final PendingTriggerRepository pendingTriggers;

    PendingTriggersController(PendingTriggerRepository pendingTriggers) {
        this.pendingTriggers = pendingTriggers;
    }

    @GetMapping
    @Operation(summary = "Triggers waiting for their source's active run to end (queue conflict mode)")
    List<PendingTriggerDto> list() {
        return pendingTriggers.findAll().stream().map(PendingTriggerDto::from).toList();
    }
}
