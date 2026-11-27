-- VIN decode fills in a car's trim and engine as well (both optional, shown on the vehicle).
ALTER TABLE apt_vehicles
    ADD COLUMN trim   varchar(60),
    ADD COLUMN engine varchar(80);

-- Quick estimates: an appointment in status ESTIMATE has a job card (the estimate) but no time
-- yet. It holds no slot (the overlap rule covers BOOKED, CONFIRMED and ARRIVED only) and stays
-- off the day board. "Schedule the work" turns it into a BOOKED visit; closing it cancels it.
ALTER TABLE apt_appointments DROP CONSTRAINT ck_apt_appointments_status;
ALTER TABLE apt_appointments ADD CONSTRAINT ck_apt_appointments_status
    CHECK (status IN ('ESTIMATE', 'BOOKED', 'CONFIRMED', 'ARRIVED', 'CHECKED_IN', 'CANCELLED', 'NO_SHOW'));
