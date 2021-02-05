package mil.tron.commonapi.pagination;

import mil.tron.commonapi.exception.InvalidFieldValueException;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PaginatorTests {

    @Test
    void testPaginator() {
        Paginator pager = new Paginator();
        List<String> stuff = Lists.newArrayList("This", "is", "a", "very", "long", "list", "of", "stuff", "to", "show", "to", "the", "user");
        assertEquals(4, pager.paginate(stuff, 1L, 4L).size());
        assertEquals(4, pager.paginate(stuff, 2L, 4L).size());
        assertEquals(Lists.newArrayList("long", "list", "of", "stuff"), pager.paginate(stuff, 2L, 4L));
        assertEquals(1, pager.paginate(stuff, 4L, 4L).size());
    }

    @Test
    void testPaginatorEdgeCases() {
        Paginator pager = new Paginator();
        List<String> stuff = Lists.newArrayList("This", "is", "a", "very", "long", "list", "of", "stuff", "to", "show", "to", "the", "user");

        // coerces pageNumber/pageSize of 0 and less than 0 => 1
        assertEquals(4, pager.paginate(stuff, 0L, 4L).size());
        assertEquals(Lists.newArrayList("This"), pager.paginate(stuff, 0L, 0L));
        assertEquals(Lists.newArrayList("This"), pager.paginate(stuff, -1L, -1L));

        // test huge number is handled for pageNumber
        assertEquals(0, pager.paginate(stuff, Long.MAX_VALUE, -1L).size());

        // test huge number for page size is handled
        assertEquals(stuff.size(), pager.paginate(stuff, 1L, Long.MAX_VALUE).size());

        // test that overflow is caught/handled
        assertThrows(InvalidFieldValueException.class, () -> pager.paginate(stuff, Long.MAX_VALUE, Long.MAX_VALUE));

        assertEquals(0, pager.paginate(stuff, Long.MAX_VALUE, Long.MIN_VALUE).size());
        assertEquals(1, pager.paginate(stuff, Long.MIN_VALUE, Long.MIN_VALUE).size());

    }

    @Test
    void testPaginatorNullData() {
        Paginator pager = new Paginator();
        List<String> stuff = Lists.newArrayList("This", "is", "a", "very", "long", "list", "of", "stuff", "to", "show", "to", "the", "user");

        assertNull(pager.paginate(null, 0L, 4L));
        assertEquals(stuff.size(), pager.paginate(stuff, null, null).size());
    }
}
