package mil.tron.commonapi.dto.mixins;

import com.fasterxml.jackson.annotation.JsonFilter;

/**
 * This is just a mixin class we use for customizing a person type entity return
 */
@JsonFilter("personFilter")
public class CustomPersonDtoMixin {
}
