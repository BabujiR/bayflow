package com.bayflow.appointment.application;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.bayflow.appointment.config.AppointmentProperties;
import com.bayflow.appointment.integration.WorkshopSettingsClient;
import com.bayflow.security.TenantContext;

/**
 * The time zone of the current workshop, which defines its "today". Comes from the workshop's
 * own settings; if auth-service can't be reached, the configured fallback keeps lists working.
 */
@Component
public class WorkshopZones {

    private static final Logger log = LoggerFactory.getLogger(WorkshopZones.class);

    private final WorkshopSettingsClient settings;
    private final AppointmentProperties properties;

    public WorkshopZones(WorkshopSettingsClient settings, AppointmentProperties properties) {
        this.settings = settings;
        this.properties = properties;
    }

    public ZoneId current() {
        UUID tenant = TenantContext.require();
        try {
            return ZoneId.of(settings.get(tenant).timeZone());
        } catch (RuntimeException e) {
            if (e instanceof DateTimeException) {
                log.warn("Workshop {} has an invalid time zone; using {}", tenant, properties.getWorkshopZone());
            } else {
                log.warn("Could not read workshop {} settings ({}); using {}", tenant, e.getMessage(),
                        properties.getWorkshopZone());
            }
            return properties.getWorkshopZone();
        }
    }
}
