package com.bayflow.notification.domain;

/** What happened, as sent by appointment-service and workshop-service. */
public enum EventType {
    APPOINTMENT_BOOKED,
    APPOINTMENT_RESCHEDULED,
    APPOINTMENT_CANCELLED,
    APPOINTMENT_CONFIRMED,
    VEHICLE_CHECKED_IN,
    ESTIMATE_READY,
    ESTIMATE_DECIDED,
    WORK_COMPLETED,
    /** A vehicle is due for maintenance (workshop-service's daily reminders). */
    SERVICE_DUE,
    INVOICE_ISSUED
}
