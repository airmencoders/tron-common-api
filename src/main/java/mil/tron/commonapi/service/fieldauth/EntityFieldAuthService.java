package mil.tron.commonapi.service.fieldauth;

import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import org.springframework.security.core.Authentication;

public interface EntityFieldAuthService {
    Person adjudicatePersonFields(Person incomingPerson, Authentication requester);
    Organization adjudicateOrganizationFields(Organization incomingOrg, Authentication requester);
}
