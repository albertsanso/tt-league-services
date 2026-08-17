package org.cttelsamicsterrassa.data.core.application.player.create;

import org.albertsanso.commons.command.DomainCommand;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.shared.model.*;
import java.time.ZonedDateTime;
import java.util.UUID;

public class CreatePlayerSeasonCommand extends DomainCommand {
    private final ImportSource source; private final String name, license; private final Player player; private final Season season;
    public CreatePlayerSeasonCommand(ImportSource source, String name, String license, Player player, Season season) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.source=source; this.name=name; this.license=license; this.player=player; this.season=season;
    }
    public ImportSource getSource(){return source;} public String getName(){return name;} public String getLicense(){return license;}
    public Player getPlayer(){return player;} public Season getSeason(){return season;}
}
