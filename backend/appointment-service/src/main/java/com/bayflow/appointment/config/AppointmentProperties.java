package com.bayflow.appointment.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code bayflow.appointment}. */
@ConfigurationProperties("bayflow.appointment")
public class AppointmentProperties {

    /** Time zone the workshop operates in; defines "today" for date filters. */
    private ZoneId workshopZone = ZoneId.of("America/New_York");

    /** Slot length when a booking does not specify one. */
    private int defaultDurationMinutes = 60;

    public ZoneId getWorkshopZone() { return workshopZone; }
    public void setWorkshopZone(ZoneId workshopZone) { this.workshopZone = workshopZone; }
    public int getDefaultDurationMinutes() { return defaultDurationMinutes; }
    public void setDefaultDurationMinutes(int defaultDurationMinutes) {
        this.defaultDurationMinutes = defaultDurationMinutes;
    }
}
