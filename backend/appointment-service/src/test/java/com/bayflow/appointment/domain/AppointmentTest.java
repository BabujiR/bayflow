package com.bayflow.appointment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AppointmentTest {

    private static final Instant NOW = Instant.parse("2026-11-16T14:00:00Z");
    private static final Instant SLOT = NOW.plus(Duration.ofDays(1));

    /** The expected state machine, written out independently of the production table. */
    private static final Map<AppointmentAction, Set<AppointmentStatus>> ALLOWED = Map.of(
            AppointmentAction.CONFIRM, EnumSet.of(AppointmentStatus.BOOKED),
            AppointmentAction.ARRIVE, EnumSet.of(AppointmentStatus.CONFIRMED),
            AppointmentAction.CHECK_IN, EnumSet.of(AppointmentStatus.ARRIVED),
            AppointmentAction.CANCEL, EnumSet.of(AppointmentStatus.BOOKED, AppointmentStatus.CONFIRMED),
            AppointmentAction.NO_SHOW, EnumSet.of(AppointmentStatus.BOOKED, AppointmentStatus.CONFIRMED),
            AppointmentAction.RESCHEDULE, EnumSet.of(AppointmentStatus.BOOKED, AppointmentStatus.CONFIRMED));

    private static final Map<AppointmentAction, AppointmentStatus> RESULT = Map.of(
            AppointmentAction.CONFIRM, AppointmentStatus.CONFIRMED,
            AppointmentAction.ARRIVE, AppointmentStatus.ARRIVED,
            AppointmentAction.CHECK_IN, AppointmentStatus.CHECKED_IN,
            AppointmentAction.CANCEL, AppointmentStatus.CANCELLED,
            AppointmentAction.NO_SHOW, AppointmentStatus.NO_SHOW);

    static Stream<Arguments> everyStatusAndAction() {
        return Stream.of(AppointmentStatus.values())
                .flatMap(status -> Stream.of(AppointmentAction.values()).map(action -> Arguments.of(status, action)));
    }

    @ParameterizedTest(name = "{1} from {0}")
    @MethodSource("everyStatusAndAction")
    void stateMachineAllowsExactlyTheDocumentedTransitions(AppointmentStatus from, AppointmentAction action) {
        Appointment appointment = inStatus(from);
        boolean allowed = ALLOWED.get(action).contains(from);
        assertThat(action.isAllowedFrom(from)).isEqualTo(allowed);

        if (allowed) {
            apply(appointment, action);
            AppointmentStatus expected = RESULT.getOrDefault(action, from);
            assertThat(appointment.getStatus()).isEqualTo(expected);
        } else {
            assertThatThrownBy(() -> apply(appointment, action))
                    .isInstanceOf(InvalidTransitionException.class)
                    .hasMessageContaining(from.name());
            assertThat(appointment.getStatus()).isEqualTo(from);
        }
    }

    @Test
    void bookingStartsAsBookedWithComputedEnd() {
        Appointment a = book(SLOT, 90);

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(a.getEndsAt()).isEqualTo(SLOT.plus(Duration.ofMinutes(90)));
        assertThat(a.getDurationMinutes()).isEqualTo(90);
        assertThat(a.getNotes()).isEqualTo("rattle");
        assertThat(a.getCreatedAt()).isEqualTo(NOW);
        assertThat(a.getJobCardId()).isNull();
    }

    @Test
    void durationMustBeBetween15MinutesAnd8Hours() {
        assertThatThrownBy(() -> book(SLOT, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> book(SLOT, 481)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotBookAnotherCustomersVehicle() {
        Customer owner = Customer.create(UUID.randomUUID(), "Owner", "o@example.com", null, NOW);
        Customer other = Customer.create(UUID.randomUUID(), "Other", "x@example.com", null, NOW);
        Vehicle vehicle = vehicle(owner);

        assertThatThrownBy(() -> Appointment.book(other, vehicle, SLOT, 60, ServiceType.OIL_CHANGE, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checkInRecordsMileageAndTime() {
        Appointment a = inStatus(AppointmentStatus.ARRIVED);
        Instant later = NOW.plusSeconds(60);

        a.checkIn(12_500, later);

        assertThat(a.getArrivalMileage()).isEqualTo(12_500);
        assertThat(a.getCheckedInAt()).isEqualTo(later);
        assertThat(a.getVehicle().getMileage()).isEqualTo(12_500);
    }

    @Test
    void checkInRejectsLowerMileage() {
        Appointment a = inStatus(AppointmentStatus.ARRIVED);

        assertThatThrownBy(() -> a.checkIn(11_000, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.ARRIVED);
    }

    @Test
    void noShowOnlyAfterTheScheduledTime() {
        Appointment a = book(SLOT, 60);

        assertThatThrownBy(() -> a.markNoShow(SLOT))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("too-early-for-no-show"));
        a.markNoShow(SLOT.plusSeconds(1));
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
    }

    @Test
    void rescheduleMovesTheSlotAndKeepsTheStatus() {
        Appointment a = inStatus(AppointmentStatus.CONFIRMED);
        Instant newSlot = SLOT.plus(Duration.ofDays(2));

        a.reschedule(newSlot, 30, NOW);

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(a.getScheduledAt()).isEqualTo(newSlot);
        assertThat(a.getEndsAt()).isEqualTo(newSlot.plus(Duration.ofMinutes(30)));
    }

    @Test
    void overlapsUsesHalfOpenIntervals() {
        Appointment a = book(SLOT, 60);

        assertThat(a.overlaps(SLOT.plusSeconds(1800), SLOT.plusSeconds(5400))).isTrue();
        assertThat(a.overlaps(SLOT.minusSeconds(1800), SLOT.plusSeconds(1))).isTrue();
        assertThat(a.overlaps(SLOT.plusSeconds(3600), SLOT.plusSeconds(7200))).isFalse();
        assertThat(a.overlaps(SLOT.minusSeconds(3600), SLOT)).isFalse();
    }

    @Test
    void jobCardCanOnlyBeLinkedAfterCheckIn() {
        UUID jobCard = UUID.randomUUID();
        assertThatThrownBy(() -> inStatus(AppointmentStatus.ARRIVED).linkJobCard(jobCard, NOW))
                .isInstanceOf(InvalidTransitionException.class);

        Appointment checkedIn = inStatus(AppointmentStatus.CHECKED_IN);
        checkedIn.linkJobCard(jobCard, NOW);
        assertThat(checkedIn.getJobCardId()).isEqualTo(jobCard);
    }

    @Test
    void activeAndFinalStatuses() {
        assertThat(AppointmentStatus.ACTIVE).containsExactlyInAnyOrder(
                AppointmentStatus.BOOKED, AppointmentStatus.CONFIRMED, AppointmentStatus.ARRIVED);
        assertThat(AppointmentStatus.CHECKED_IN.isFinal()).isTrue();
        assertThat(AppointmentStatus.BOOKED.isFinal()).isFalse();
        assertThat(AppointmentStatus.ARRIVED.isActive()).isTrue();
        assertThat(AppointmentStatus.CANCELLED.isActive()).isFalse();
    }

    // ---- helpers ---------------------------------------------------------------

    private static Vehicle vehicle(Customer owner) {
        return Vehicle.register(owner, "1HGCM82633A004352", "abc 123", "Honda", "Accord", 2019, 12_000, NOW);
    }

    private static Appointment book(Instant slot, int minutes) {
        Customer customer = Customer.create(UUID.randomUUID(), "Jane Doe", "Jane@Example.com", "555-0100", NOW);
        return Appointment.book(customer, vehicle(customer), slot, minutes, ServiceType.ROUTINE_SERVICE, " rattle ",
                NOW);
    }

    /** Builds an appointment in the given status by walking the valid path to it. */
    private static Appointment inStatus(AppointmentStatus status) {
        Appointment a = book(SLOT, 60);
        switch (status) {
            case BOOKED -> { }
            case CONFIRMED -> a.confirm(NOW);
            case ARRIVED -> {
                a.confirm(NOW);
                a.arrive(NOW);
            }
            case CHECKED_IN -> {
                a.confirm(NOW);
                a.arrive(NOW);
                a.checkIn(12_100, NOW);
            }
            case CANCELLED -> a.cancel(NOW);
            case NO_SHOW -> a.markNoShow(SLOT.plusSeconds(60));
        }
        assertThat(a.getStatus()).isEqualTo(status);
        return a;
    }

    private static void apply(Appointment a, AppointmentAction action) {
        Instant afterSlot = SLOT.plusSeconds(3600);
        switch (action) {
            case CONFIRM -> a.confirm(NOW);
            case ARRIVE -> a.arrive(NOW);
            case CHECK_IN -> a.checkIn(20_000, NOW);
            case CANCEL -> a.cancel(NOW);
            case NO_SHOW -> a.markNoShow(afterSlot);
            case RESCHEDULE -> a.reschedule(SLOT.plus(Duration.ofDays(3)), 60, NOW);
        }
    }
}
