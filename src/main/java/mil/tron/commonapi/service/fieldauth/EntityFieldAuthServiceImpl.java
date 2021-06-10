package mil.tron.commonapi.service.fieldauth;

import mil.tron.commonapi.annotation.efa.ProtectedField;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.OrganizationRepository;
import mil.tron.commonapi.repository.PersonRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EntityFieldAuthServiceImpl implements EntityFieldAuthService {

    private final PrivilegeRepository privilegeRepository;
    private final OrganizationRepository organizationRepository;
    private final PersonRepository personRepository;
    private static final String PERSON_PREFIX = "Person-";
    private static final String ORG_PREFIX = "Organization-";
    private final List<Field> personFields = FieldUtils.getFieldsListWithAnnotation(Person.class, ProtectedField.class);
    private final List<Field> orgFields = FieldUtils.getFieldsListWithAnnotation(Organization.class, ProtectedField.class);
    private static final ModelMapper MAPPER = new ModelMapper();

    @Value("${efa-enabled}")
    private boolean efaEnabled;

    public EntityFieldAuthServiceImpl(PrivilegeRepository privilegeRepository,
                                      OrganizationRepository organizationRepository,
                                      PersonRepository personRepository) {
        this.privilegeRepository = privilegeRepository;
        this.organizationRepository = organizationRepository;
        this.personRepository = personRepository;
    }

    /**
     * Builds out the entity field authorization privileges for the fields
     * in the Person/Organization entity that are marked @ProtectedField
     */

    /**
     * Determines which data "gets let through" on a person update/patch
     * @param incomingPerson the incoming data POJO from the request
     * @param requester the requester's Authentication object
     * @return the (possibly) adjusted POJO for the fields that were changed and allowed to change
     */
    @Override
    public Person adjudicatePersonFields(Person incomingPerson, Authentication requester) {

        // if EFA isn't even enabled, just return the new entity
        if (!efaEnabled) return incomingPerson;

        Person existingPerson = personRepository.findById(incomingPerson.getId())
                .orElseThrow(() -> new RecordNotFoundException("Person not found with id: " + incomingPerson.getId()));

        // if we can't get requester information, then don't let any change through, return the existing one.
        if (requester == null) {
            return existingPerson;
        }

        // if we're a DASHBOARD_ADMIN, then accept full incoming object
        if (requester.getAuthorities().contains(new SimpleGrantedAuthority("DASHBOARD_ADMIN")))
            return incomingPerson;

        // or if we're an entity with the PERSON_CREATE, then accept full incoming object
        if (requester.getAuthorities().contains(new SimpleGrantedAuthority("PERSON_CREATE")))
            return incomingPerson;

        // for each protected field we need to decide whether to use the incoming value or leave the existing
        //  based on the privs of the app client
        for (Field f : personFields) {
            if (!requester.getAuthorities().contains(new SimpleGrantedAuthority(PERSON_PREFIX + f.getName()))) {

                try {
                    // requester did not have the rights to this field, negate its value by
                    //  overwriting from existing object
                    FieldUtils.writeField(incomingPerson,
                            f.getName(),
                            FieldUtils.readField(existingPerson, f.getName(), true),
                            true);
                }
                catch (IllegalAccessException e) {
                    throw new InvalidRecordUpdateRequest("Tried to access a Person field with bad permissions or a field that does not exist");
                }
            }
        }

        // return the (possible modified) entity to the service
        return incomingPerson;
    }

    /**
     * Determines which data "gets let through" on a organization update/patch
     * @param incomingOrg the incoming data POJO from the request
     * @param requester the requester's Authentication object
     * @return the (possibly) adjusted POJO for the fields that were changed and allowed to change
     */
    @Override
    public Organization adjudicateOrganizationFields(Organization incomingOrg, Authentication requester) {

        // if EFA isn't even enabled, just return the new entity
        if (!efaEnabled) return incomingOrg;

        Organization existingOrg = organizationRepository.findById(incomingOrg.getId())
                .orElseThrow(() -> new RecordNotFoundException("Existing org not found with id: " + incomingOrg.getId()));

        // if we can't get requester information, then don't let any change through, return the existing one.
        if (requester == null) {
            return existingOrg;
        }

        // if we're a DASHBOARD_ADMIN, then accept full incoming object
        if (requester.getAuthorities().contains(new SimpleGrantedAuthority("DASHBOARD_ADMIN")))
            return incomingOrg;

        // or if we're an entity with the ORGANIZATION_CREATE, then accept full incoming object
        if (requester.getAuthorities().contains(new SimpleGrantedAuthority("ORGANIZATION_CREATE")))
            return incomingOrg;

        // for each protected field we need to decide whether to use the incoming value or leave the existing
        //  based on the privs of the app client
        for (Field f : orgFields) {
            if (!requester.getAuthorities().contains(new SimpleGrantedAuthority(ORG_PREFIX + f.getName()))) {

                try {
                    // requester did not have the rights to this field, negate its value by
                    //  overwriting from existing object
                    FieldUtils.writeField(incomingOrg,
                            f.getName(),
                            FieldUtils.readField(existingOrg, f.getName(), true),
                            true);
                }
                catch (IllegalAccessException e) {
                    throw new InvalidRecordUpdateRequest("Tried to access an Org field with bad permissions or a field that does not exist");
                }
            }
        }

        // return the (possible modified) entity to the service
        return incomingOrg;
    }

}
