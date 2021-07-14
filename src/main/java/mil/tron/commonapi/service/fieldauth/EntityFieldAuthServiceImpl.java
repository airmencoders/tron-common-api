package mil.tron.commonapi.service.fieldauth;

import mil.tron.commonapi.annotation.efa.ProtectedField;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.OrganizationRepository;
import mil.tron.commonapi.repository.PersonRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.service.PrivilegeService;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.Transactional;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EntityFieldAuthServiceImpl implements EntityFieldAuthService {

    private final PrivilegeRepository privilegeRepository;
    private final OrganizationRepository organizationRepository;
    private final PersonRepository personRepository;
    private final PrivilegeService privilegeService;
    private static final String PERSON_PREFIX = "Person-";
    private static final String ORG_PREFIX = "Organization-";
    private static final String DENIED_FIELDS_HEADER = "x-denied-entity-fields";
    private final List<Field> personFields = FieldUtils.getFieldsListWithAnnotation(Person.class, ProtectedField.class);
    private final List<Field> orgFields = FieldUtils.getFieldsListWithAnnotation(Organization.class, ProtectedField.class);

    @Value("${efa-enabled}")
    private boolean efaEnabled;

    public EntityFieldAuthServiceImpl(PrivilegeRepository privilegeRepository,
                                      OrganizationRepository organizationRepository,
                                      PersonRepository personRepository,
                                      PrivilegeService privilegeService) {
        this.privilegeRepository = privilegeRepository;
        this.organizationRepository = organizationRepository;
        this.personRepository = personRepository;
        this.privilegeService = privilegeService;
    }

    @PostConstruct
    public void init() {
        buildEntityPrivileges();
    }

    /**
     * Builds out the entity field authorization privileges for the fields
     * in the Person/Organization entity that are marked @ProtectedField.  It also
     * prunes out any fields that have privileges in the db now, but for some reason
     * aren't marked as @ProtectedField anymore...
     */
    @Transactional
    public void buildEntityPrivileges() {

        for (Field f : personFields) {
            String privName = PERSON_PREFIX + f.getName();
            Optional<Privilege> p = privilegeRepository.findByName(privName);
            if (p.isEmpty()) {
                Privilege r = new Privilege();
                r.setName(PERSON_PREFIX + f.getName());
                privilegeRepository.save(r);
            }
        }

        // get list of all the Person- field privileges we have now
        List<Privilege> personPrivs = privilegeRepository.findAll()
                .stream()
                .filter(item -> item.getName().startsWith(PERSON_PREFIX))
                .collect(Collectors.toList());

        // see if we have any orphaned ones we dont need anymore, and delete them
        for (Privilege priv : personPrivs) {
            if (!personFields
                    .stream()
                    .map(Field::getName)
                    .collect(Collectors.toList())
                    .contains(priv.getName().replaceFirst(PERSON_PREFIX, ""))) {

                privilegeService.deletePrivilege(priv);
            }
        }

        for (Field f : orgFields) {
            String privName = ORG_PREFIX + f.getName();
            Optional<Privilege> p = privilegeRepository.findByName(privName);
            if (p.isEmpty()) {
                Privilege r = new Privilege();
                r.setName(ORG_PREFIX + f.getName());
                privilegeRepository.save(r);
            }
        }

        // get list of all the Organization- field privileges we have now
        List<Privilege> orgPrivs = privilegeRepository.findAll()
                .stream()
                .filter(item -> item.getName().startsWith(ORG_PREFIX))
                .collect(Collectors.toList());

        // see if we have any orphaned ones we dont need anymore, and delete them
        for (Privilege priv : orgPrivs) {
            if (!orgFields
                    .stream()
                    .map(Field::getName)
                    .collect(Collectors.toList())
                    .contains(priv.getName().replaceFirst(ORG_PREFIX, ""))) {

                privilegeService.deletePrivilege(priv);
            }
        }
    }

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

        List<String> deniedFields = new ArrayList<>();
        HttpServletResponse response = getResponseObject();

        Person existingPerson = personRepository.findById(incomingPerson.getId())
                .orElseThrow(() -> new RecordNotFoundException("Person not found with id: " + incomingPerson.getId()));

        // if we can't get requester information, then don't let any change through, return the existing one.
        if (requester == null) {
            return existingPerson;
        }

        // if we're a DASHBOARD_ADMIN, then accept full incoming object
        if (requester.getAuthorities().contains(new SimpleGrantedAuthority("DASHBOARD_ADMIN")))
            return incomingPerson;
        
        // Must have EDIT privilege by this point to proceed
        if (!requester.getAuthorities().contains(new SimpleGrantedAuthority("PERSON_EDIT"))) {
        	return existingPerson;
        }
        
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

                    deniedFields.add(f.getName());
                }
                catch (IllegalAccessException e) {
                    throw new InvalidRecordUpdateRequest("Tried to access a Person field with bad permissions or a field that does not exist");
                }
            }
        }

        if (!deniedFields.isEmpty()) {
            response.addHeader(DENIED_FIELDS_HEADER, String.join(",", deniedFields));
        }

        // return the (possible modified) entity to the service
        return incomingPerson;
    }

    private HttpServletResponse getResponseObject() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            throw new BadRequestException("Unable to get request attributes for entity field auth");
        }
        HttpServletResponse response = ((ServletRequestAttributes)requestAttributes).getResponse();
        if (response == null) {
            throw new BadRequestException("Unable to get http response instance for entity field auth");
        }

        return response;
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

        List<String> deniedFields = new ArrayList<>();
        HttpServletResponse response = getResponseObject();

        Organization existingOrg = organizationRepository.findById(incomingOrg.getId())
                .orElseThrow(() -> new RecordNotFoundException("Existing org not found with id: " + incomingOrg.getId()));

        // if we can't get requester information, then don't let any change through, return the existing one.
        if (requester == null) {
            return existingOrg;
        }

        // if we're a DASHBOARD_ADMIN, then accept full incoming object
        if (requester.getAuthorities().contains(new SimpleGrantedAuthority("DASHBOARD_ADMIN")))
            return incomingOrg;
        
        // Must have EDIT privilege by this point to proceed
        if (!requester.getAuthorities().contains(new SimpleGrantedAuthority("ORGANIZATION_EDIT"))) {
        	return existingOrg;
        }

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

                    deniedFields.add(f.getName());
                }
                catch (IllegalAccessException e) {
                    throw new InvalidRecordUpdateRequest("Tried to access an Org field with bad permissions or a field that does not exist");
                }
            }
        }

        if (!deniedFields.isEmpty()) {
            response.addHeader(DENIED_FIELDS_HEADER, String.join(",", deniedFields));
        }

        // return the (possible modified) entity to the service
        return incomingOrg;
    }

}
