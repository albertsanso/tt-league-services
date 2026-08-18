package org.cttelsamicsterrassa.data.api.rest.club;

import io.swagger.v3.oas.annotations.Operation;
import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.club.find.FindClubByIdQuery;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@ClubOpenAPIv1Controller
public class ClubController {

    @Autowired
    private QueryBus queryBus;

    @Autowired
    private CommandBus commandBus;

    @GetMapping("/find_by_id")
    @Operation(summary = "Find club by id", description = "Returns a club by its UUID")
    public ResponseEntity<ClubDto> findClubById(@RequestParam("id") UUID id) {
        FindClubByIdQuery findClubByIdQuery = new FindClubByIdQuery(id);
        DomainQueryResponse<Club> queryResponse = queryBus.push(findClubByIdQuery);
        return queryResponse.isSuccess() ?
                ResponseEntity.ok(ClubDto.fromObject(queryResponse.getResponse())) :
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
