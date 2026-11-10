package com.bayflow.auth.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bayflow.auth.domain.ServiceClient;

public interface ServiceClientRepository extends JpaRepository<ServiceClient, String> {
}
