package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Comparator;
import java.util.List;

@Named
public class FindClubsByStringInNameQueryHandler extends DomainQueryHandler<FindClubsByStringInNameQuery, List<Club>> {

    private final ClubRepository clubRepository;

    @Inject
    public FindClubsByStringInNameQueryHandler(ClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    @Override
    public DomainQueryResponse<List<Club>> handle(FindClubsByStringInNameQuery findClubsByStringInNameQuery) {
        String search = findClubsByStringInNameQuery.getStringToSearch();
        if (search == null || search.trim().length() < 2) {
            return DomainQueryResponse.failResponse(List.of());
        }

        List<String> fragments = List.of(search.trim().split("\\s+"));
        ImportSource source = findClubsByStringInNameQuery.getSource();
        List<Club> clubs = source == null
                ? clubRepository.findAllClubsByFragmentsInName(fragments)
                : clubRepository.findAllClubsBySourceAndFragmentsInName(source, fragments);
        List<Club> orderedClubs = clubs.stream()
                .sorted(Comparator.comparing(
                                Club::getName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(Club::getName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(club -> club.getSource() == null ? null : club.getSource().name(),
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(Club::getId))
                .toList();
        return DomainQueryResponse.sucessResponse(orderedClubs);

    }
}
