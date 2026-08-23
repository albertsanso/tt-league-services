package org.cttelsamicsterrassa.data.core.application.player.find;
import org.albertsanso.commons.query.DomainQuery; import java.time.ZonedDateTime; import java.util.UUID;
public class FindFederatedPlayerByIdQuery extends DomainQuery{private final UUID federatedPlayerId;public FindFederatedPlayerByIdQuery(UUID id){super(ZonedDateTime.now(),UUID.randomUUID().toString());federatedPlayerId=id;}public UUID getFederatedPlayerId(){return federatedPlayerId;}}
