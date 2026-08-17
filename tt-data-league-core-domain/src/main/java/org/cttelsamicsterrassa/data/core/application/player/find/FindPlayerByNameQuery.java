package org.cttelsamicsterrassa.data.core.application.player.find;
import org.albertsanso.commons.query.DomainQuery; import java.time.ZonedDateTime; import java.util.UUID;
public class FindPlayerByNameQuery extends DomainQuery{private final String name;public FindPlayerByNameQuery(String n){super(ZonedDateTime.now(),UUID.randomUUID().toString());name=n;}public String getName(){return name;}}
