package com.bayflow.workshop.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * The workshop's record for one checked-in vehicle. It is created from an appointment
 * (exactly one job card per appointment) and moves through inspection and customer
 * approval; the methods below enforce the state machine in {@link JobCardAction}.
 */
@Entity
@Table(name = "ws_job_cards")
public class JobCard {

    private static final String ENTITY = "job card";

    @Id
    private UUID id;

    @Column(name = "appointment_id", nullable = false, unique = true)
    private UUID appointmentId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(nullable = false, length = 17)
    private String vin;

    @Column(nullable = false, length = 20)
    private String registration;

    /** The customer's login (JWT "sub"); empty for customers without an account. */
    @Column(name = "customer_user_id")
    private UUID customerUserId;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    /** Odometer reading at check-in. */
    @Column(nullable = false)
    private int mileage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobCardStatus status;

    @OneToMany(mappedBy = "jobCard", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<InspectionFinding> findings = new ArrayList<>();

    @OneToMany(mappedBy = "jobCard", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<RecommendedWorkItem> recommendations = new ArrayList<>();

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected JobCard() {
        // for JPA
    }

    public static JobCard open(UUID appointmentId, UUID vehicleId, String vin, String registration,
            UUID customerUserId, String customerName, int mileage, Instant now) {
        if (mileage < 0) {
            throw new IllegalArgumentException("Mileage cannot be negative");
        }
        JobCard card = new JobCard();
        card.id = UUID.randomUUID();
        card.appointmentId = appointmentId;
        card.vehicleId = vehicleId;
        card.vin = vin;
        card.registration = registration;
        card.customerUserId = customerUserId;
        card.customerName = customerName;
        card.mileage = mileage;
        card.status = JobCardStatus.OPEN;
        card.createdAt = now;
        card.updatedAt = now;
        return card;
    }

    // ---- inspection --------------------------------------------------------------

    /** Records a finding; the first one moves the card from OPEN to INSPECTED. */
    public InspectionFinding addFinding(String description, Severity severity, String recordedBy, Instant now) {
        require(JobCardAction.ADD_FINDING);
        InspectionFinding finding = new InspectionFinding(this, description, severity, recordedBy, now);
        findings.add(finding);
        status = JobCardStatus.INSPECTED;
        updatedAt = now;
        return finding;
    }

    public RecommendedWorkItem addRecommendation(String description, BigDecimal estimatedPartsCost,
            BigDecimal estimatedLabourHours, Instant now) {
        require(JobCardAction.ADD_RECOMMENDATION);
        RecommendedWorkItem item = new RecommendedWorkItem(this, description, estimatedPartsCost,
                estimatedLabourHours, now);
        recommendations.add(item);
        updatedAt = now;
        return item;
    }

    // ---- approval ------------------------------------------------------------------

    /**
     * Sends the recommendations to the customer. With nothing recommended the card goes
     * straight to DECLINED (inspection only).
     */
    public void submitForApproval(Instant now) {
        require(JobCardAction.SUBMIT_FOR_APPROVAL);
        submittedAt = now;
        updatedAt = now;
        if (recommendations.isEmpty()) {
            status = JobCardStatus.DECLINED;
            decidedAt = now;
        } else {
            status = JobCardStatus.AWAITING_APPROVAL;
        }
    }

    /**
     * Approves or declines one recommendation. Once every item is decided, the card's
     * status becomes APPROVED, PARTIALLY_APPROVED or DECLINED.
     */
    public RecommendedWorkItem decide(UUID itemId, boolean approve, String decidedBy, Instant now) {
        require(JobCardAction.DECIDE_ITEM);
        RecommendedWorkItem item = findRecommendation(itemId)
                .orElseThrow(() -> new BusinessRuleException("item-not-found",
                        "This job card has no recommendation " + itemId + "."));
        item.decide(approve, decidedBy, now);
        updatedAt = now;
        if (recommendations.stream().noneMatch(RecommendedWorkItem::isPending)) {
            status = outcome();
            decidedAt = now;
        }
        return item;
    }

    private JobCardStatus outcome() {
        long approved = recommendations.stream().filter(RecommendedWorkItem::isApproved).count();
        if (approved == recommendations.size()) {
            return JobCardStatus.APPROVED;
        }
        return approved == 0 ? JobCardStatus.DECLINED : JobCardStatus.PARTIALLY_APPROVED;
    }

    // ---- queries -------------------------------------------------------------------------

    public Optional<RecommendedWorkItem> findRecommendation(UUID itemId) {
        return recommendations.stream().filter(i -> i.getId().equals(itemId)).findFirst();
    }

    public boolean isOwnedBy(UUID userId) {
        return customerUserId != null && customerUserId.equals(userId);
    }

    /** Sum of all recommendation estimates. */
    public BigDecimal estimatedTotal(BigDecimal hourlyRate) {
        return recommendations.stream().map(i -> i.estimatedTotal(hourlyRate))
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
    }

    /** Sum of the approved recommendation estimates. */
    public BigDecimal approvedEstimate(BigDecimal hourlyRate) {
        return recommendations.stream().filter(RecommendedWorkItem::isApproved)
                .map(i -> i.estimatedTotal(hourlyRate))
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
    }

    private void require(JobCardAction action) {
        if (!action.isAllowedFrom(status)) {
            throw new InvalidTransitionException(ENTITY, status, action.description());
        }
    }

    // ---- getters ------------------------------------------------------------------------

    public UUID getId() { return id; }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID getVehicleId() { return vehicleId; }
    public String getVin() { return vin; }
    public String getRegistration() { return registration; }
    public UUID getCustomerUserId() { return customerUserId; }
    public String getCustomerName() { return customerName; }
    public int getMileage() { return mileage; }
    public JobCardStatus getStatus() { return status; }
    public List<InspectionFinding> getFindings() { return Collections.unmodifiableList(findings); }
    public List<RecommendedWorkItem> getRecommendations() { return Collections.unmodifiableList(recommendations); }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
