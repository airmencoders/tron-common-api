package mil.tron.commonapi.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DashboardUserTest {
    DashboardUser dashboardUser;

    @BeforeEach
    void setup() {
        dashboardUser = new DashboardUser();
    }

    @Test
    void testEmail_sanitization() {
        dashboardUser.setEmail(" test@email.com ");
        dashboardUser.sanitize();

        assertThat(dashboardUser.getEmail()).isEqualTo("test@email.com");
    }

}
