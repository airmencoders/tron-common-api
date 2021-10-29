package mil.tron.commonapi.validations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FolderFileNameValidatorTests {
    private DocSpaceFolderOrFilenameValidator validator = new DocSpaceFolderOrFilenameValidator();

    @Test
    public void shouldReturnTrueOnValidFolderFileName() {
        assertTrue(validator.isValid("123456789", null));
        assertTrue(validator.isValid("This is a file with space in it.txt", null));
        assertTrue(validator.isValid("This-is_a-weird--__name.html", null));
    }

    @Test
    public void shouldReturnFalseOnInvalidFolderFileName(){
        assertFalse(validator.isValid("1234...", null));
        assertFalse(validator.isValid(null, null));
        assertFalse(validator.isValid("", null));
        assertFalse(validator.isValid(" ", null));
        assertFalse(validator.isValid("notes .txt", null));
        assertFalse(validator.isValid("notes. txt", null));
        assertFalse(validator.isValid("some & file.txt", null));
        assertFalse(validator.isValid("some.file.txt", null));

    }

}
