package com.bayflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class CurrentUserTest {

    @Test
    void readsSubjectUsernameAndRolesFromJwtAuthentication() {
        var auth = new RolesClaimAuthenticationConverter()
                .convert(TestJwts.userJwt("user-7", "customer1", List.of("CUSTOMER")));

        CurrentUser user = CurrentUser.from(auth);

        assertThat(user.subject()).isEqualTo("user-7");
        assertThat(user.username()).isEqualTo("customer1");
        assertThat(user.roles()).containsExactly("CUSTOMER");
        assertThat(user.isCustomer()).isTrue();
        assertThat(user.isServiceAdvisor()).isFalse();
        assertThat(user.isMechanic()).isFalse();
        assertThat(user.isSystem()).isFalse();
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThatThrownBy(() -> CurrentUser.from(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnauthenticatedToken() {
        var auth = new UsernamePasswordAuthenticationToken("x", "y");
        assertThatThrownBy(() -> CurrentUser.from(auth)).isInstanceOf(IllegalStateException.class);
    }
}
