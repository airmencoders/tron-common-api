package mil.tron.commonapi.service.fieldauth;

import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;

import org.springframework.security.core.Authentication;

public interface EntityFieldAuthService {
    EntityFieldAuthResponse<Person> adjudicatePersonFields(Person incomingPerson, Authentication requester);
    EntityFieldAuthResponse<Organization> adjudicateOrganizationFields(Organization incomingOrg, Authentication requester);
    boolean userHasAuthorizationToField(Authentication requester, EntityFieldAuthType type, String fieldName);
}
