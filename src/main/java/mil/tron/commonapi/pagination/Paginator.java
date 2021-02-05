package mil.tron.commonapi.pagination;

import com.google.common.collect.Lists;
import com.google.common.math.LongMath;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import mil.tron.commonapi.exception.InvalidFieldValueException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@NoArgsConstructor
@EqualsAndHashCode
@Component
public class Paginator {

    public <T> List<T> paginate(Iterable<T> items, Long pageNumber, Long pageSize) {

        // check null condition
        if (items == null) return new ArrayList<>();

        // check for null vals given from the controller
        if (pageNumber == null) pageNumber = 1L;
        if (pageSize == null) pageSize = Long.MAX_VALUE;

        // test pageNumber for = 0 or < 0, coerce to 1
        if (pageNumber == 0 || pageNumber < 0) pageNumber = 1L;

        // test pageSize for = 0 or < 0, coerce to 1
        if (pageSize == 0 || pageSize < 0) pageSize = 1L;

        // test for integer overflow
        long skipVal = 0L;
        try {
            skipVal = LongMath.checkedMultiply((pageNumber - 1L), pageSize);
        }
        catch (ArithmeticException e) {
            throw new InvalidFieldValueException("Page Number and/or Page Limit exceeds size limits");
        }

        return Lists.newArrayList(items).stream().skip(skipVal).limit(pageSize).collect(Collectors.toList());
    }
}
