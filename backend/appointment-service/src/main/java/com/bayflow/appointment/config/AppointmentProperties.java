package com.bayflow.appointment.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code bayflow.appointment}. */
@ConfigurationProperties("bayflow.appointment")
public class AppointmentProperties {

    /**
     * Fallback time zone, used only when a workshop's own setting (from auth-service) can't be
     * read. Each workshop sets its own in the app.
     */
    private ZoneId workshopZone = ZoneId.of("America/New_York");

    /** Slot length when a booking does not specify one. */
    private int defaultDurationMinutes = 60;

    /**
     * The public site's address (scheme, host, port); a workshop's own address is
     * {@code <slug>.<host>}. Links in e-mails (an online booking's "change or cancel") use it.
     */
    private String siteUrl = "http://localhost:3000";

    public String getSiteUrl() { return siteUrl; }
    public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl; }
    public ZoneId getWorkshopZone() { return workshopZone; }
    public void setWorkshopZone(ZoneId workshopZone) { this.workshopZone = workshopZone; }
    public int getDefaultDurationMinutes() { return defaultDurationMinutes; }
    public void setDefaultDurationMinutes(int defaultDurationMinutes) {
        this.defaultDurationMinutes = defaultDurationMinutes;
    }
}
