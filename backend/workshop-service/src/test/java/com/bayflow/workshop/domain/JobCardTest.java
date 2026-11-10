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

    /** Statuses reachable in milestone 4 (IN_PROGRESS and COMPLETED arrive with milestone 5). */
    private static final List<JobCardStatus> REACHABLE = List.of(JobCardStatus.OPEN, JobCardStatus.INSPECTED,
            JobCardStatus.AWAITING_APPROVAL, JobCardStatus.APPROVED, JobCardStatus.PARTIALLY_APPROVED,
            JobCardStatus.DECLINED);

    /** The expected state machine, written out independently of the production table. */
    private static final Map<JobCardAction, Set<JobCardStatus>> ALLOWED = Map.of(
            JobCardAction.ADD_FINDING, EnumSet.of(JobCardStatus.OPEN, JobCardStatus.INSPECTED),
            JobCardAction.ADD_RECOMMENDATION, EnumSet.of(JobCardStatus.OPEN, JobCardStatus.INSPECTED),
            JobCardAction.SUBMIT_FOR_APPROVAL, EnumSet.of(JobCardStatus.INSPECTED),
            JobCardAction.DECIDE_ITEM, EnumSet.of(JobCardStatus.AWAITING_APPROVAL));

    static Stream<Arguments> everyReachableStatusAndAction() {
        return REACHABLE.stream()
                .flatMap(status -> Stream.of(JobCardAction.values()).map(action -> Arguments.of(status, action)));
    }

    @ParameterizedTest(name = "{1} from {0}")
    @MethodSource("everyReachableStatusAndAction")
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
    void neitherWorkStatusAllowsAnyMilestone4Action() {
        for (JobCardAction action : JobCardAction.values()) {
            assertThat(action.isAllowedFrom(JobCardStatus.IN_PROGRESS)).isFalse();
            assertThat(action.isAllowedFrom(JobCardStatus.COMPLETED)).isFalse();
        }
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
            default -> throw new IllegalArgumentException("Not reachable in milestone 4: " + status);
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
        };
    }
}
