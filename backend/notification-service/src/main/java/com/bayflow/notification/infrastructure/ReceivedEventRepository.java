package com.bayflow.notification.infrastructure;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.notification.domain.ReceivedEvent;

@Transactional(readOnly = true)
public interface ReceivedEventRepository extends JpaRepository<ReceivedEvent, UUID> {
}
