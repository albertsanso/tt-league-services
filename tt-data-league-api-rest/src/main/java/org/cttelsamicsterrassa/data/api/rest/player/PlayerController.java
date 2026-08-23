package org.cttelsamicsterrassa.data.api.rest.player;

import io.swagger.v3.oas.annotations.Operation;
import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.player.find.FindFederatedPlayersByStringInNameQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@PlayerOpenAPIv1Controller
public class PlayerController {

    @Autowired
    private QueryBus queryBus;

    @Autowired
    private CommandBus commandBus;

    @GetMapping("/search_in_name")
    @Operation(summary = "Search players by string in name", description = "Returns a list of players whose names contain the specified search string")
    public ResponseEntity<List<PlayerDto>> findPlayersByStringInName(@RequestParam("name") String searchString) {
        FindFederatedPlayersByStringInNameQuery query = new FindFederatedPlayersByStringInNameQuery(searchString);
        var queryResponse = queryBus.push(query);
        if (queryResponse.isSuccess()) {
            List<PlayerDto> playerDtos = ((List<?>) queryResponse.getResponse()).stream()
                    .map(player -> PlayerDto.fromObject((org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer) player))
                    .toList();
            return ResponseEntity.ok(playerDtos);
        } else {
            return ResponseEntity.status(500).build();
        }
    }
}
