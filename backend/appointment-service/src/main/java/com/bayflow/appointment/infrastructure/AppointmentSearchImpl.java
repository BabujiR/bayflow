package com.bayflow.appointment.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
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
                .orderBy(order(cb, appointment, filter));
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

    /** The order of {@code filter.sort()}; the id last, so pages never repeat or skip a row. */
    private static List<Order> order(CriteriaBuilder cb, Root<Appointment> root, AppointmentFilter filter) {
        Path<Instant> at = root.get("scheduledAt");
        return switch (filter.sort()) {
            case EARLIEST -> List.of(cb.asc(at), cb.asc(root.get("id")));
            case LATEST -> List.of(cb.desc(at), cb.desc(root.get("id")));
            case RECENT -> List.of(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
            case UPCOMING -> {
                // Not finished yet (ends after now) first, soonest first; then the past, latest first.
                Instant now = filter.now() != null ? filter.now() : Instant.now();
                Predicate ahead = cb.greaterThan(root.get("endsAt"), now);
                Expression<Integer> bucket = cb.<Integer>selectCase().when(ahead, 0).otherwise(1);
                Expression<Instant> future = cb.<Instant>selectCase().when(ahead, at)
                        .otherwise(cb.nullLiteral(Instant.class));
                Expression<Instant> past = cb.<Instant>selectCase().when(ahead, cb.nullLiteral(Instant.class))
                        .otherwise(at);
                yield List.of(cb.asc(bucket), cb.asc(future), cb.desc(past), cb.asc(root.get("id")));
            }
        };
    }

    private static Predicate[] predicates(CriteriaBuilder cb, Root<Appointment> root, AppointmentFilter filter) {
        List<Predicate> predicates = new ArrayList<>();
        if (filter.customerId() != null) {
            predicates.add(cb.equal(root.get("customer").<UUID>get("id"), filter.customerId()));
        }
        if (filter.vehicleId() != null) {
            predicates.add(cb.equal(root.get("vehicle").<UUID>get("id"), filter.vehicleId()));
        }
        if (filter.locationId() != null) {
            predicates.add(cb.equal(root.<UUID>get("locationId"), filter.locationId()));
        }
        if (filter.status() != null) {
            predicates.add(cb.equal(root.<AppointmentStatus>get("status"), filter.status()));
        } else {
            // Quick estimates have no time yet: they are listed only when asked for (status=ESTIMATE).
            predicates.add(cb.notEqual(root.<AppointmentStatus>get("status"), AppointmentStatus.ESTIMATE));
        }
        if (filter.from() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledAt"), filter.from()));
        }
        if (filter.to() != null) {
            predicates.add(cb.lessThan(root.get("scheduledAt"), filter.to()));
        }
        if (!filter.days().isEmpty()) {
            // Each appointment's day in its own location's time zone.
            predicates.add(cb.or(filter.days().stream()
                    .map(day -> cb.and(
                            cb.equal(root.<UUID>get("locationId"), day.locationId()),
                            cb.greaterThanOrEqualTo(root.get("scheduledAt"), day.from()),
                            cb.lessThan(root.get("scheduledAt"), day.to())))
                    .toArray(Predicate[]::new)));
        }
        return predicates.toArray(Predicate[]::new);
    }
}
