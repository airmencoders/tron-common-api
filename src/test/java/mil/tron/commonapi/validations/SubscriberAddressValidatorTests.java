package mil.tron.commonapi.validations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubscriberAddressValidatorTests {

    private SubscriberAddressValidator validator = new SubscriberAddressValidator();

    @Test
    void shouldReturnTrueOnValidUrls() {
        assertTrue(validator.isValid("http://tron-something.something.svc.cluster.local/v1/endpoint", null));
        assertTrue(validator.isValid("http://localhost:8080/changed", null));
    }

    @Test
    void shouldReturnFalseOnInvalidUrls() {
        assertFalse(validator.isValid("http://www.some-outside-url.com/", null));

        // disallow common api's own address
        assertFalse(validator.isValid("http://tron-common-api.tron-common-api.svc.cluster.local/", null));

        // disallow https since we're exclusively in-cluster addresses only - which are http
        assertFalse(validator.isValid("https://tempest.tempest.svc.cluster.local/", null));
    }
}
