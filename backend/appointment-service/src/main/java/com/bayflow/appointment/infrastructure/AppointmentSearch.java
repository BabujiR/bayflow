package com.bayflow.appointment.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.bayflow.appointment.domain.Appointment;

/** Filtered appointment listing, ordered by scheduled time. */
public interface AppointmentSearch {

    Page<Appointment> search(AppointmentFilter filter, Pageable pageable);
}
