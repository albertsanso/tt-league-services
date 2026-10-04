package org.cttelsamicsterrassa.data.core.repository.jpa.load.impl;

import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJob;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobRepository;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.load.mapper.ImportJobJPAToImportJobMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.load.mapper.ImportJobToImportJobJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.load.model.ImportJobJPA;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Transactional
@Component
@AllArgsConstructor
public class ImportJobRepositoryJpa implements ImportJobRepository {
    private static final Set<ImportJobStatus> DEDUPLICATING = Set.of(ImportJobStatus.QUEUED, ImportJobStatus.STORING,
            ImportJobStatus.IMPORTING, ImportJobStatus.SUCCEEDED, ImportJobStatus.PARTIAL);

    private final ImportJobRepositoryHelper helper;
    private final ImportJobJPAToImportJobMapper toDomain;
    private final ImportJobToImportJobJPAMapper toJpa;

    @Override
    public void save(ImportJob job) {
        helper.save(toJpa.apply(job));
    }

    @Override
    public Optional<ImportJob> findById(UUID id) {
        return helper.findById(id).map(toDomain);
    }

    @Override
    public Optional<ImportJob> findActiveOrSucceededBySourceAndContentSha256(ImportSource source,
                                                                             String contentSha256) {
        return helper.findFirstBySourceAndContentSha256AndStatusInOrderByCreatedAtDesc(
                        Source.valueOf(source.name()), contentSha256, DEDUPLICATING)
                .map(toDomain);
    }

    @Override
    public List<ImportJob> find(Optional<ImportSource> source, Optional<ZonedDateTime> createdFrom,
                                Optional<ZonedDateTime> createdBefore, int limit) {
        Specification<ImportJobJPA> filters = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            source.ifPresent(value ->
                    predicates.add(criteriaBuilder.equal(root.get("source"), Source.valueOf(value.name()))));
            createdFrom.ifPresent(from ->
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), from)));
            createdBefore.ifPresent(before ->
                    predicates.add(criteriaBuilder.lessThan(root.get("createdAt"), before)));
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
        return helper.findAll(filters, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .stream()
                .map(toDomain)
                .toList();
    }

    @Override
    public List<ImportJob> findByStatusIn(Set<ImportJobStatus> statuses) {
        return helper.findByStatusInOrderByCreatedAtAsc(statuses).stream()
                .map(toDomain)
                .toList();
    }
}
