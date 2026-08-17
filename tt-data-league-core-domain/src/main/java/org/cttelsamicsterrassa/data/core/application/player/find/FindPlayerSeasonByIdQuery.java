package org.cttelsamicsterrassa.data.core.application.player.find;
import org.albertsanso.commons.query.DomainQuery; import java.time.ZonedDateTime; import java.util.UUID;
public class FindPlayerSeasonByIdQuery extends DomainQuery{private final UUID id;public FindPlayerSeasonByIdQuery(UUID i){super(ZonedDateTime.now(),UUID.randomUUID().toString());id=i;}public UUID getPlayerSeasonId(){return id;}}
