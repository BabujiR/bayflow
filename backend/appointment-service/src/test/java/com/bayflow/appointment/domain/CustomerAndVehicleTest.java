package com.bayflow.appointment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CustomerAndVehicleTest {

    private static final Instant NOW = Instant.parse("2026-11-16T14:00:00Z");

    @Test
    void customerNormalizesContactDetailsAndKnowsItsOwner() {
        UUID user = UUID.randomUUID();
        Customer c = Customer.create(user, "  Jane Doe ", " Jane@Example.COM ", "  ", NOW);

        assertThat(c.getName()).isEqualTo("Jane Doe");
        assertThat(c.getEmail()).isEqualTo("jane@example.com");
        assertThat(c.getPhone()).isNull();
        assertThat(c.isOwnedBy(user)).isTrue();
        assertThat(c.isOwnedBy(UUID.randomUUID())).isFalse();
        assertThat(Customer.create(null, "Walk-in", "w@example.com", "555", NOW).isOwnedBy(user)).isFalse();
    }

    @Test
    void vehicleNormalizesVinAndRegistration() {
        Customer owner = Customer.create(null, "Jane", "j@example.com", null, NOW);
        Vehicle v = Vehicle.register(owner, " 1hgcm82633a004352 ", " abc 123 ", " Honda ", " Accord ", 2019, 100, NOW);

        assertThat(v.getVin()).isEqualTo("1HGCM82633A004352");
        assertThat(v.getRegistration()).isEqualTo("ABC 123");
        assertThat(v.getMake()).isEqualTo("Honda");
        assertThat(v.getOwner()).isSameAs(owner);
    }

    @Test
    void vehicleRejectsInvalidVinAndNegativeMileage() {
        Customer owner = Customer.create(null, "Jane", "j@example.com", null, NOW);

        assertThatThrownBy(() -> Vehicle.register(owner, "IOQ12345678901234", "R", "M", "M", 2020, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Vehicle.register(owner, "SHORT", "R", "M", "M", 2020, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Vehicle.register(owner, "1HGCM82633A004352", "R", "M", "M", 2020, -1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mileageNeverGoesDown() {
        Customer owner = Customer.create(null, "Jane", "j@example.com", null, NOW);
        Vehicle v = Vehicle.register(owner, "1HGCM82633A004352", "R", "M", "M", 2020, 5_000, NOW);

        v.recordMileage(5_000, NOW);
        v.recordMileage(6_000, NOW);
        assertThat(v.getMileage()).isEqualTo(6_000);
        assertThatThrownBy(() -> v.recordMileage(5_999, NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
