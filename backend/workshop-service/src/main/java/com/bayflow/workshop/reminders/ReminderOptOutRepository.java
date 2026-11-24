package com.bayflow.workshop.reminders;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface ReminderOptOutRepository extends JpaRepository<ReminderOptOut, ReminderOptOut.Key> {

    /** Of these customers, the ones who turned reminders off. */
    @Query("select o.customerUserId from ReminderOptOut o where o.customerUserId in :customers")
    List<UUID> optedOutAmong(@Param("customers") Collection<UUID> customers);
}
