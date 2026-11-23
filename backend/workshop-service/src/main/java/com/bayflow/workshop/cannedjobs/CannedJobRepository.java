package com.bayflow.workshop.cannedjobs;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Row-level security shows the organization's jobs for every location and for the caller's. */
public interface CannedJobRepository extends JpaRepository<CannedJob, UUID>, JpaSpecificationExecutor<CannedJob> {
}
