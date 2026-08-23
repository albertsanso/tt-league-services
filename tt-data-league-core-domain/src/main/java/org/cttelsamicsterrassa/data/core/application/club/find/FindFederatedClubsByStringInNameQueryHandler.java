package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Comparator;
import java.util.List;

@Named
public class FindFederatedClubsByStringInNameQueryHandler extends DomainQueryHandler<FindFederatedClubsByStringInNameQuery, List<FederatedClub>> {

    private final FederatedClubRepository clubRepository;

    @Inject
    public FindFederatedClubsByStringInNameQueryHandler(FederatedClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    @Override
    public DomainQueryResponse<List<FederatedClub>> handle(FindFederatedClubsByStringInNameQuery findClubsByStringInNameQuery) {
        String search = findClubsByStringInNameQuery.getStringToSearch();
        if (search == null || search.trim().length() < 2) {
            return DomainQueryResponse.failResponse(List.of());
        }

        List<String> fragments = List.of(search.trim().split("\\s+"));
        ImportSource source = findClubsByStringInNameQuery.getSource();
        List<FederatedClub> clubs = source == null
                ? clubRepository.findAllFederatedClubsByFragmentsInName(fragments)
                : clubRepository.findAllFederatedClubsBySourceAndFragmentsInName(source, fragments);
        List<FederatedClub> orderedClubs = clubs.stream()
                .sorted(Comparator.comparing(
                                FederatedClub::getName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(FederatedClub::getName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(club -> club.getSource() == null ? null : club.getSource().name(),
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(FederatedClub::getId))
                .toList();
        return DomainQueryResponse.sucessResponse(orderedClubs);

    }
}
