package com.bayflow.auth.infrastructure;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bayflow.auth.domain.DemoRequest;

public interface DemoRequestRepository extends JpaRepository<DemoRequest, UUID> {

    /** Newest first; status null = all; q in name, email, phone or shop. */
    @Query("""
            select d from DemoRequest d
            where (:status is null or d.status = :status)
              and (:q is null
                   or lower(d.shopName) like :q or lower(d.email) like :q or d.phone like :q
                   or lower(concat(d.firstName, ' ', d.lastName)) like :q)
            order by d.createdAt desc, d.id""")
    Page<DemoRequest> search(@Param("status") DemoRequest.Status status, @Param("q") String q, Pageable page);
}
