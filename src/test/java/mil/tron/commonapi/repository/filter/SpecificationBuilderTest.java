package mil.tron.commonapi.repository.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
public class SpecificationBuilderTest {

    @Test
    void checkOperatorSupportsInputTest() {
        List<FilterCriteria> filter = List.of(
                new FilterCriteria(RelationType.OR, "testField",
                        "with", List.of(
                                new FilterCondition(QueryOperator.EQUALS,
                                        "value",
                                        null)
                ))
        );
        Specification<Object> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
        assertThat(specification).isNotNull();
    }

}
