package org.cttelsamicsterrassa.data.core.repository.jpa.load.impl;

import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobStatus;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.load.model.ImportJobJPA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportJobRepositoryHelper extends JpaRepository<ImportJobJPA, UUID>,
        JpaSpecificationExecutor<ImportJobJPA> {

    Optional<ImportJobJPA> findFirstBySourceAndContentSha256AndStatusInOrderByCreatedAtDesc(
            Source source, String contentSha256, Collection<ImportJobStatus> statuses);

    List<ImportJobJPA> findByStatusInOrderByCreatedAtAsc(Collection<ImportJobStatus> statuses);
}
