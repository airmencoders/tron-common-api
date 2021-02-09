package mil.tron.commonapi.dto.mixins;

import com.fasterxml.jackson.annotation.JsonFilter;

/**
 * This class is just a mixin used by Jackson for customizing org-type entity return
 */
@JsonFilter("orgFilter")
public class CustomOrganizationDtoMixin {
}
