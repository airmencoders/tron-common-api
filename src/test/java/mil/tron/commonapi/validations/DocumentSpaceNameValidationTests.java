package mil.tron.commonapi.validations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DocumentSpaceNameValidationTests {
    private DocumentSpaceNameValidator validator = new DocumentSpaceNameValidator();

    @Test
    public void testInvalids() {
        assertFalse(validator.isValid("Test", null));
        assertFalse(validator.isValid("Test_Folder", null));
        assertFalse(validator.isValid("", null));
        assertFalse(validator.isValid(null, null));
        assertFalse(validator.isValid("test/folder", null));
        assertFalse(validator.isValid(".metadata", null));
    }

    @Test
    public void testValids() {
        assertTrue(validator.isValid("test", null));
        assertTrue(validator.isValid("test-folder", null));
        assertTrue(validator.isValid("test-folder123.tron", null));
        assertTrue(validator.isValid("89.metadata", null));
    }

}
