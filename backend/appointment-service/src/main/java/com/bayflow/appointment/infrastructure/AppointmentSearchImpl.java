package com.bayflow.appointment.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.bayflow.appointment.domain.Appointment;
import com.bayflow.appointment.domain.AppointmentStatus;

/** Criteria-based implementation of {@link AppointmentSearch} (a Spring Data repository fragment). */
class AppointmentSearchImpl implements AppointmentSearch {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Appointment> search(AppointmentFilter filter, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Appointment> query = cb.createQuery(Appointment.class);
        Root<Appointment> appointment = query.from(Appointment.class);
        query.select(appointment)
                .where(predicates(cb, appointment, filter))
                .orderBy(cb.asc(appointment.get("scheduledAt")), cb.asc(appointment.get("id")));
        List<Appointment> content = entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Appointment> counted = countQuery.from(Appointment.class);
        countQuery.select(cb.count(counted)).where(predicates(cb, counted, filter));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    private static Predicate[] predicates(CriteriaBuilder cb, Root<Appointment> root, AppointmentFilter filter) {
        List<Predicate> predicates = new ArrayList<>();
        if (filter.customerId() != null) {
            predicates.add(cb.equal(root.get("customer").<UUID>get("id"), filter.customerId()));
        }
        if (filter.vehicleId() != null) {
            predicates.add(cb.equal(root.get("vehicle").<UUID>get("id"), filter.vehicleId()));
        }
        if (filter.status() != null) {
            predicates.add(cb.equal(root.<AppointmentStatus>get("status"), filter.status()));
        }
        if (filter.from() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("scheduledAt"), filter.from()));
        }
        if (filter.to() != null) {
            predicates.add(cb.lessThan(root.<Instant>get("scheduledAt"), filter.to()));
        }
        return predicates.toArray(Predicate[]::new);
    }
}
