package mil.tron.commonapi.validations;

import mil.tron.commonapi.service.documentspace.DocumentSpaceFileSystemServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;

import static org.junit.jupiter.api.Assertions.*;

public class PathUtilityTests {

    @Test
    void testJoinPathParts() {

        String[] path = new String[] { "some", "path/segment//with///dupes/", "test.txt" };
        String result = DocumentSpaceFileSystemServiceImpl.joinPathParts(path);

        assertFalse(result.endsWith(DocumentSpaceFileSystemServiceImpl.PATH_SEP));
        assertTrue(result.startsWith(DocumentSpaceFileSystemServiceImpl.PATH_SEP));
        assertTrue(result.startsWith(DocumentSpaceFileSystemServiceImpl.PATH_SEP));
        assertEquals(6, StringUtils.countOccurrencesOf(result, DocumentSpaceFileSystemServiceImpl.PATH_SEP));
    }

    @Test
    void testCountPathDepth() {
        assertEquals(7, DocumentSpaceFileSystemServiceImpl.countPathDepth("/some/path/thats/really/long/and/stuff"));
        assertEquals(2, DocumentSpaceFileSystemServiceImpl.countPathDepth("/some/path/"));
        assertEquals(2, DocumentSpaceFileSystemServiceImpl.countPathDepth("///some///path///"));
        assertEquals(0, DocumentSpaceFileSystemServiceImpl.countPathDepth(""));
        assertEquals(0, DocumentSpaceFileSystemServiceImpl.countPathDepth(null));
    }

    @Test
    void testSlashRemoved() {
        // since SQ throws a fit for end of string anchored regex's we have our own bit-banged utility

        assertEquals("", DocumentSpaceFileSystemServiceImpl.removeTrailingSlashes(null));
        assertEquals("", DocumentSpaceFileSystemServiceImpl.removeTrailingSlashes(""));
        assertEquals(" ", DocumentSpaceFileSystemServiceImpl.removeTrailingSlashes(" "));
        assertEquals("/some/string", DocumentSpaceFileSystemServiceImpl.removeTrailingSlashes("/some/string"));
        assertEquals("/some/string", DocumentSpaceFileSystemServiceImpl.removeTrailingSlashes("/some/string////"));
        assertEquals("", DocumentSpaceFileSystemServiceImpl.removeTrailingSlashes("/"));
    }
}
