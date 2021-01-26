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
    }
}
