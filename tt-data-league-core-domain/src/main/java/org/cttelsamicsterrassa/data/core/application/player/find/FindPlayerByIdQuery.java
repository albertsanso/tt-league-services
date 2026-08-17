package org.cttelsamicsterrassa.data.core.application.player.find;
import org.albertsanso.commons.query.DomainQuery; import java.time.ZonedDateTime; import java.util.UUID;
public class FindPlayerByIdQuery extends DomainQuery{private final UUID playerId;public FindPlayerByIdQuery(UUID id){super(ZonedDateTime.now(),UUID.randomUUID().toString());playerId=id;}public UUID getPlayerId(){return playerId;}}
