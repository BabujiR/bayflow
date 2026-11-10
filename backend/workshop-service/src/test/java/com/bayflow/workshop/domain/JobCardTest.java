package com.bayflow.workshop.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JobCardTest {

    private static final Instant NOW = Instant.parse("2026-11-16T15:00:00Z");
    private static final BigDecimal RATE = new BigDecimal("95.00");


    /** The expected state machine, written out independently of the production table. */
    private static final Map<JobCardAction, Set<JobCardStatus>> ALLOWED = Map.of(
            JobCardAction.ADD_FINDING, EnumSet.of(JobCardStatus.OPEN, JobCardStatus.INSPECTED),
            JobCardAction.ADD_RECOMMENDATION, EnumSet.of(JobCardStatus.OPEN, JobCardStatus.INSPECTED),
            JobCardAction.SUBMIT_FOR_APPROVAL, EnumSet.of(JobCardStatus.INSPECTED),
            JobCardAction.DECIDE_ITEM, EnumSet.of(JobCardStatus.AWAITING_APPROVAL),
            JobCardAction.START, EnumSet.of(JobCardStatus.APPROVED, JobCardStatus.PARTIALLY_APPROVED),
            JobCardAction.RECORD_WORK, EnumSet.of(JobCardStatus.IN_PROGRESS),
            JobCardAction.COMPLETE, EnumSet.of(JobCardStatus.IN_PROGRESS, JobCardStatus.DECLINED));

    static Stream<Arguments> everyStatusAndAction() {
        return Stream.of(JobCardStatus.values())
                .flatMap(status -> Stream.of(JobCardAction.values()).map(action -> Arguments.of(status, action)));
    }

    @ParameterizedTest(name = "{1} from {0}")
    @MethodSource("everyStatusAndAction")
    void stateMachineAllowsExactlyTheDocumentedTransitions(JobCardStatus from, JobCardAction action) {
        JobCard card = inStatus(from);
        boolean allowed = ALLOWED.get(action).contains(from);
        assertThat(action.isAllowedFrom(from)).isEqualTo(allowed);

        if (allowed) {
            apply(card, action);
            assertThat(card.getStatus()).isEqualTo(expectedAfter(from, action));
        } else {
            assertThatThrownBy(() -> apply(card, action))
                    .isInstanceOf(InvalidTransitionException.class)
                    .hasMessageContaining(from.name());
            assertThat(card.getStatus()).isEqualTo(from);
        }
    }

    @Test
    void completedIsFinal() {
        for (JobCardAction action : JobCardAction.values()) {
            assertThat(action.isAllowedFrom(JobCardStatus.COMPLETED)).isFalse();
        }
    }

    // ---- work and completion ----------------------------------------------------

    @Test
    void completionAddsUpPartsAndLabour() {
        JobCard card = decided(true, false);
        RecommendedWorkItem pads = card.getRecommendations().get(0);
        card.start(NOW);
        card.logWork(pads.getId(), "mechanic1", "Fitted new front pads", NOW);
        PartUsage part = card.addPart(pads.getId(), "BP-220", "Brake pad set", 2, new BigDecimal("45.25"),
                "mechanic1", NOW);
        LabourEntry labour = card.addLabour(pads.getId(), "mechanic1", new BigDecimal("1.25"), RATE, NOW);
        card.addLabour(pads.getId(), "mechanic2", new BigDecimal("0.5"), new BigDecimal("80"), NOW);

        Instant done = NOW.plusSeconds(3600);
        card.complete(done);

        assertThat(part.lineTotal()).isEqualByComparingTo("90.50");
        assertThat(labour.cost()).isEqualByComparingTo("118.75");
        assertThat(card.getPartsTotal()).isEqualByComparingTo("90.50");
        assertThat(card.getLabourTotal()).isEqualByComparingTo("158.75");
        assertThat(card.getGrandTotal()).isEqualByComparingTo("249.25");
        assertThat(card.getStatus()).isEqualTo(JobCardStatus.COMPLETED);
        assertThat(card.getCompletedAt()).isEqualTo(done);
        assertThat(card.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    void workCanOnlyBeRecordedAgainstApprovedItems() {
        JobCard card = decided(true, false);
        UUID declined = card.getRecommendations().get(1).getId();
        card.start(NOW);

        assertThatThrownBy(() -> card.logWork(declined, "mechanic1", "x", NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("item-not-approved"));
        assertThatThrownBy(() -> card.addPart(declined, "P", "Part", 1, BigDecimal.ONE, "m", NOW))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> card.addLabour(UUID.randomUUID(), "m", BigDecimal.ONE, RATE, NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("item-not-found"));
    }

    @Test
    void everyApprovedItemNeedsAWorkLogBeforeCompletion() {
        JobCard card = decided(true, true);
        card.start(NOW);
        card.logWork(card.getRecommendations().get(0).getId(), "mechanic1", "Pads done", NOW);

        assertThatThrownBy(() -> card.complete(NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("work-incomplete");
                    assertThat(e.getMessage()).contains("Replace wipers");
                });
        assertThat(card.getStatus()).isEqualTo(JobCardStatus.IN_PROGRESS);
    }

    @Test
    void declinedCardCompletesAsInspectionOnlyWithZeroTotals() {
        JobCard card = decided(false, false);

        card.complete(NOW);

        assertThat(card.getStatus()).isEqualTo(JobCardStatus.COMPLETED);
        assertThat(card.getGrandTotal()).isEqualByComparingTo("0.00");
        assertThat(ServiceHistoryEntry.record(card, "{}").getTotalCost()).isEqualByComparingTo("0.00");
    }

    @Test
    void historyEntryCopiesTheCompletedCard() {
        JobCard card = inStatus(JobCardStatus.COMPLETED);

        ServiceHistoryEntry entry = ServiceHistoryEntry.record(card, "{\"work\":[]}");

        assertThat(entry.getJobCardId()).isEqualTo(card.getId());
        assertThat(entry.getVehicleId()).isEqualTo(card.getVehicleId());
        assertThat(entry.getMileage()).isEqualTo(12_000);
        assertThat(entry.getServiceDate()).isEqualTo(card.getCompletedAt());
        assertThat(entry.getTotalCost()).isEqualByComparingTo(card.getGrandTotal());
        assertThat(entry.isOwnedBy(card.getCustomerUserId())).isTrue();
        assertThatThrownBy(() -> ServiceHistoryEntry.record(inStatus(JobCardStatus.IN_PROGRESS), "{}"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void partsAndLabourRejectNonsense() {
        JobCard card = inStatus(JobCardStatus.IN_PROGRESS);
        UUID item = card.getRecommendations().get(0).getId();

        assertThatThrownBy(() -> card.addPart(item, "P", "Part", 0, BigDecimal.ONE, "m", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> card.addPart(item, "P", "Part", 1, new BigDecimal("-1"), "m", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> card.addLabour(item, "m", BigDecimal.ZERO, RATE, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> card.addLabour(item, "m", BigDecimal.ONE, new BigDecimal("-5"), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submittingWithRecommendationsWaitsForTheCustomer() {
        JobCard card = inStatus(JobCardStatus.INSPECTED);
        card.addRecommendation("Replace front pads", new BigDecimal("120"), new BigDecimal("1.5"), NOW);

        card.submitForApproval(NOW);

        assertThat(card.getStatus()).isEqualTo(JobCardStatus.AWAITING_APPROVAL);
        assertThat(card.getSubmittedAt()).isEqualTo(NOW);
        assertThat(card.getDecidedAt()).isNull();
    }

    @Test
    void submittingWithoutRecommendationsIsInspectionOnly() {
        JobCard card = inStatus(JobCardStatus.INSPECTED);

        card.submitForApproval(NOW);

        assertThat(card.getStatus()).isEqualTo(JobCardStatus.DECLINED);
        assertThat(card.getDecidedAt()).isEqualTo(NOW);
    }

    @Test
    void outcomeFollowsTheDecisions() {
        assertThat(decided(true, true).getStatus()).isEqualTo(JobCardStatus.APPROVED);
        assertThat(decided(true, false).getStatus()).isEqualTo(JobCardStatus.PARTIALLY_APPROVED);
        assertThat(decided(false, false).getStatus()).isEqualTo(JobCardStatus.DECLINED);
    }

    @Test
    void cardStaysAwaitingUntilEveryItemIsDecided() {
        JobCard card = inStatus(JobCardStatus.AWAITING_APPROVAL);
        RecommendedWorkItem first = card.getRecommendations().get(0);

        RecommendedWorkItem decided = card.decide(first.getId(), true, "customer1", NOW);

        assertThat(decided.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(decided.getDecidedBy()).isEqualTo("customer1");
        assertThat(decided.getDecidedAt()).isEqualTo(NOW);
        assertThat(card.getStatus()).isEqualTo(JobCardStatus.AWAITING_APPROVAL);
        assertThat(card.getDecidedAt()).isNull();
    }

    @Test
    void anItemCanOnlyBeDecidedOnce() {
        JobCard card = inStatus(JobCardStatus.AWAITING_APPROVAL);
        UUID first = card.getRecommendations().get(0).getId();
        card.decide(first, true, "customer1", NOW);

        assertThatThrownBy(() -> card.decide(first, false, "customer1", NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("item-already-decided"));
    }

    @Test
    void unknownItemIsReported() {
        JobCard card = inStatus(JobCardStatus.AWAITING_APPROVAL);

        assertThatThrownBy(() -> card.decide(UUID.randomUUID(), true, "c", NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("item-not-found"));
    }

    @Test
    void estimatesAddPartsAndLabourAtTheHourlyRate() {
        JobCard card = inStatus(JobCardStatus.INSPECTED);
        RecommendedWorkItem pads = card.addRecommendation("Pads", new BigDecimal("120.5"), new BigDecimal("1.5"),
                NOW);
        card.addRecommendation("Wipers", new BigDecimal("40"), new BigDecimal("0.25"), NOW);

        assertThat(pads.getEstimatedPartsCost()).isEqualByComparingTo("120.50");
        assertThat(pads.estimatedLabourCost(RATE)).isEqualByComparingTo("142.50");
        assertThat(pads.estimatedTotal(RATE)).isEqualByComparingTo("263.00");
        assertThat(card.estimatedTotal(RATE)).isEqualByComparingTo("326.75");
        assertThat(card.approvedEstimate(RATE)).isEqualByComparingTo("0.00");

        card.submitForApproval(NOW);
        card.decide(pads.getId(), true, "c", NOW);
        assertThat(card.approvedEstimate(RATE)).isEqualByComparingTo("263.00");
    }

    @Test
    void estimatesCannotBeNegative() {
        JobCard card = inStatus(JobCardStatus.OPEN);

        assertThatThrownBy(() -> card.addRecommendation("x", new BigDecimal("-1"), BigDecimal.ONE, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> card.addRecommendation("x", BigDecimal.ONE, new BigDecimal("-0.5"), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void openingRecordsTheVehicleAndOwner() {
        UUID owner = UUID.randomUUID();
        JobCard card = JobCard.open(UUID.randomUUID(), UUID.randomUUID(), "1HGCM82633A004352", "ABC 123", owner,
                "Jane Doe", 12_480, NOW);

        assertThat(card.getStatus()).isEqualTo(JobCardStatus.OPEN);
        assertThat(card.getMileage()).isEqualTo(12_480);
        assertThat(card.isOwnedBy(owner)).isTrue();
        assertThat(card.isOwnedBy(UUID.randomUUID())).isFalse();
        assertThat(card.getFindings()).isEmpty();
        assertThatThrownBy(() -> JobCard.open(UUID.randomUUID(), UUID.randomUUID(), "V", "R", null, "N", -1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findingsKeepWhoRecordedThem() {
        JobCard card = inStatus(JobCardStatus.OPEN);

        InspectionFinding finding = card.addFinding("  Pads at 2 mm ", Severity.HIGH, "mechanic1", NOW);

        assertThat(finding.getDescription()).isEqualTo("Pads at 2 mm");
        assertThat(finding.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(finding.getRecordedBy()).isEqualTo("mechanic1");
        assertThat(card.getFindings()).containsExactly(finding);
    }

    // ---- helpers -----------------------------------------------------------------

    private static JobCard open() {
        return JobCard.open(UUID.randomUUID(), UUID.randomUUID(), "1HGCM82633A004352", "ABC 123",
                UUID.randomUUID(), "Jane Doe", 12_000, NOW);
    }

    /** Builds a card in the given status by walking the valid path to it. */
    private static JobCard inStatus(JobCardStatus status) {
        JobCard card = open();
        switch (status) {
            case OPEN -> { }
            case INSPECTED -> card.addFinding("Pads worn", Severity.HIGH, "mechanic1", NOW);
            case AWAITING_APPROVAL -> awaiting(card);
            case APPROVED -> decideAll(awaiting(card), true, true);
            case PARTIALLY_APPROVED -> decideAll(awaiting(card), true, false);
            case DECLINED -> decideAll(awaiting(card), false, false);
            case IN_PROGRESS -> inProgress(card);
            case COMPLETED -> inProgress(card).complete(NOW);
        }
        assertThat(card.getStatus()).isEqualTo(status);
        return card;
    }

    private static JobCard awaiting(JobCard card) {
        card.addFinding("Pads worn", Severity.HIGH, "mechanic1", NOW);
        card.addRecommendation("Replace front pads", new BigDecimal("120.00"), new BigDecimal("1.5"), NOW);
        card.addRecommendation("Replace wipers", new BigDecimal("40.00"), new BigDecimal("0.5"), NOW);
        card.submitForApproval(NOW);
        return card;
    }

    private static JobCard decideAll(JobCard card, boolean first, boolean second) {
        List<RecommendedWorkItem> items = card.getRecommendations();
        card.decide(items.get(0).getId(), first, "customer1", NOW);
        card.decide(items.get(1).getId(), second, "customer1", NOW);
        return card;
    }

    /** Both items approved, work started and logged for each, ready to complete. */
    private static JobCard inProgress(JobCard card) {
        decideAll(awaiting(card), true, true).start(NOW);
        for (RecommendedWorkItem item : card.getRecommendations()) {
            card.logWork(item.getId(), "mechanic1", "Done: " + item.getDescription(), NOW);
        }
        return card;
    }

    private static JobCard decided(boolean first, boolean second) {
        return decideAll(awaiting(open()), first, second);
    }

    private static void apply(JobCard card, JobCardAction action) {
        switch (action) {
            case ADD_FINDING -> card.addFinding("Leak", Severity.MEDIUM, "mechanic1", NOW);
            case ADD_RECOMMENDATION -> card.addRecommendation("Fix leak", BigDecimal.TEN, BigDecimal.ONE, NOW);
            case SUBMIT_FOR_APPROVAL -> card.submitForApproval(NOW);
            case DECIDE_ITEM -> {
                UUID itemId = card.getRecommendations().isEmpty()
                        ? UUID.randomUUID()
                        : card.getRecommendations().get(0).getId();
                card.decide(itemId, true, "customer1", NOW);
            }
            case START -> card.start(NOW);
            case RECORD_WORK -> {
                UUID itemId = card.getRecommendations().isEmpty()
                        ? UUID.randomUUID()
                        : card.getRecommendations().get(0).getId();
                card.logWork(itemId, "mechanic1", "More work", NOW);
            }
            case COMPLETE -> card.complete(NOW);
        }
    }

    private static JobCardStatus expectedAfter(JobCardStatus from, JobCardAction action) {
        return switch (action) {
            case ADD_FINDING -> JobCardStatus.INSPECTED;
            case ADD_RECOMMENDATION -> from;
            // the INSPECTED fixture has no recommendations, so submitting is inspection-only
            case SUBMIT_FOR_APPROVAL -> JobCardStatus.DECLINED;
            // one of two items decided: still waiting for the other
            case DECIDE_ITEM -> JobCardStatus.AWAITING_APPROVAL;
            case START, RECORD_WORK -> JobCardStatus.IN_PROGRESS;
            case COMPLETE -> JobCardStatus.COMPLETED;
        };
    }
}
