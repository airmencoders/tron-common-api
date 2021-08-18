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
import mil.tron.commonapi.service.PrivilegeService;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EntityFieldAuthServiceImpl implements EntityFieldAuthService {
	private static final SimpleGrantedAuthority DASHBOARD_ADMIN_AUTHORITY = new SimpleGrantedAuthority("DASHBOARD_ADMIN");
	
    private final PrivilegeRepository privilegeRepository;
    private final OrganizationRepository organizationRepository;
    private final PersonRepository personRepository;
    private final PrivilegeService privilegeService;
    
    private static final String PERSON_PREFIX = "Person-";
    private static final String ORG_PREFIX = "Organization-";
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
    
    private SimpleGrantedAuthority generateAuthorityFromFieldName(EntityFieldAuthType type, String fieldName) {
    	switch (type) {
	    	case ORGANIZATION: 
	    		return new SimpleGrantedAuthority(String.format("%s%s", ORG_PREFIX, fieldName));
	    	
	    	case PERSON: 
	    		return new SimpleGrantedAuthority(String.format("%s%s", PERSON_PREFIX, fieldName));
	    	
	    	default:
	    		throw new UnsupportedOperationException(String.format("%s is not a supported Entity Field Auth object type", type));
    	}
    }

    /**
     * Determines which data "gets let through" on a person update/patch
     * @param incomingPerson the incoming data POJO from the request
     * @param requester the requester's Authentication object
     * @return the entity containing allowed modifications and a list containing any denied fields
     * 
     */
    @Override
    public EntityFieldAuthResponse<Person> adjudicatePersonFields(Person incomingPerson, Authentication requester) {

        // if EFA isn't even enabled, just return the new entity
        if (!efaEnabled) {
        	return EntityFieldAuthResponse.<Person>builder()
        		.modifiedEntity(incomingPerson)
        		.build();
        }

        List<String> deniedFields = new ArrayList<>();

        Person existingPerson = personRepository.findById(incomingPerson.getId())
                .orElseThrow(() -> new RecordNotFoundException("Person not found with id: " + incomingPerson.getId()));

        // if we can't get requester information, then don't let any change through, return the existing one.
        if (requester == null) {
        	return EntityFieldAuthResponse.<Person>builder()
    			.modifiedEntity(existingPerson)
    			.build();
        }

        // if we're a DASHBOARD_ADMIN, then accept full incoming object
        if (requester.getAuthorities().contains(DASHBOARD_ADMIN_AUTHORITY)) {
            return EntityFieldAuthResponse.<Person>builder()
        			.modifiedEntity(incomingPerson)
        			.build();
        }
        
        boolean isOwnUser = existingPerson.getEmail().equalsIgnoreCase(requester.getName());
        
        // Must have EDIT privilege by this point to proceed
        // Or the authenticated user must be editing their own record
        if (!requester.getAuthorities().contains(new SimpleGrantedAuthority("PERSON_EDIT")) && !isOwnUser) {
        	return EntityFieldAuthResponse.<Person>builder()
        			.modifiedEntity(existingPerson)
        			.build();
        }
        
        /**
         * for each protected field we need to decide whether to use the incoming value or leave the existing
         * based on the privs of the app client.
         * 
        */
        for (Field f : personFields) {
            if (requesterHasPrivsOrIsOwner(requester, isOwnUser, f)) {
                try {

                    // if the incoming value is equal to the existing value for this field, then
                    //  it doesn't count as an attempt to change, so go to next field
                    if (Objects.equals(FieldUtils.readField(existingPerson, f.getName(), true),
                            FieldUtils.readField(incomingPerson, f.getName(), true))) {
                        continue;
                    }

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

        // return the (possible modified) entity to the service
        return EntityFieldAuthResponse.<Person>builder()
    			.modifiedEntity(incomingPerson)
    			.deniedFields(deniedFields)
    			.build();
    }

    /**
     * Checks if requester has rights to change applicable person fields OR they are a user editing their own data,
     *  in which case - allow them to edit everything except DODID and email
     * @param requester the Authentication object of the request
     * @param isOwnUser if requester is owner (the person) of representing by the Person object
     * @param f field that is up for a possible change
     * @return true if the field is allowed to be changed
     */
    private boolean requesterHasPrivsOrIsOwner(Authentication requester, boolean isOwnUser, Field f) {
        return (!userHasAuthorizationToField(requester, EntityFieldAuthType.PERSON, f.getName()) && !isOwnUser) ||
                (isOwnUser && (f.getName().equalsIgnoreCase(Person.DODID_FIELD) || f.getName().equalsIgnoreCase(Person.EMAIL_FIELD)));
    }
    
    /**
     * Determines which data "gets let through" on a organization update/patch
     * @param incomingOrg the incoming data POJO from the request
     * @param requester the requester's Authentication object
     * 
     * @return the entity containing allowed modifications and a list containing any denied fields
     */
    @Override
    @Transactional(value=TxType.REQUIRES_NEW)
    public EntityFieldAuthResponse<Organization> adjudicateOrganizationFields(Organization incomingOrg, Authentication requester) {

        // if EFA isn't even enabled, just return the new entity
        if (!efaEnabled) {
        	return EntityFieldAuthResponse.<Organization>builder()
        			.modifiedEntity(incomingOrg)
        			.build();
        }

        List<String> deniedFields = new ArrayList<>();

        Organization existingOrg = organizationRepository.findById(incomingOrg.getId())
                .orElseThrow(() -> new RecordNotFoundException("Existing org not found with id: " + incomingOrg.getId()));
        

        // if we can't get requester information, then don't let any change through, return the existing one.
        if (requester == null) {
        	return EntityFieldAuthResponse.<Organization>builder()
        			.modifiedEntity(existingOrg)
        			.build();
        }

        // if we're a DASHBOARD_ADMIN, then accept full incoming object
        if (requester.getAuthorities().contains(DASHBOARD_ADMIN_AUTHORITY)) {
        	return EntityFieldAuthResponse.<Organization>builder()
        			.modifiedEntity(incomingOrg)
        			.build();        
    	}
        
        // Must have EDIT privilege by this point to proceed
        if (!requester.getAuthorities().contains(new SimpleGrantedAuthority("ORGANIZATION_EDIT"))) {
        	return EntityFieldAuthResponse.<Organization>builder()
        			.modifiedEntity(existingOrg)
        			.build();
        }

        // for each protected field we need to decide whether to use the incoming value or leave the existing
        //  based on the privs of the app client
        for (Field f : orgFields) {
            if (!userHasAuthorizationToField(requester, EntityFieldAuthType.ORGANIZATION, f.getName())) {

                try {

                    // if the incoming value is equal to the existing value for this field, then
                    //  it doesn't count as an attempt to change, so go to next field
                    if (Objects.equals(FieldUtils.readField(existingOrg, f.getName(), true),
                            FieldUtils.readField(incomingOrg, f.getName(), true))) {
                        continue;
                    }

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

        // return the (possible modified) entity to the service
        return EntityFieldAuthResponse.<Organization>builder()
    			.modifiedEntity(incomingOrg)
    			.deniedFields(deniedFields)
    			.build();
    }

    /**
     * Checks if the requesting user has necessary privileges to a field.
     * 
     * @param requester the requesting user to check for necessary privileges
     * @param type the type of Entity to check
     * @param fieldName the name of the field
     * @returns true if EFA is disabled, user is DASHBOARD_ADMIN, or user has the privilege associated with {@link fieldName}.
     */
	@Override
	public boolean userHasAuthorizationToField(Authentication requester, EntityFieldAuthType type, String fieldName) {
		Set<SimpleGrantedAuthority> allowedAuthorities = Set.of(DASHBOARD_ADMIN_AUTHORITY, generateAuthorityFromFieldName(type, fieldName));
		return !efaEnabled || requester.getAuthorities().stream().anyMatch(allowedAuthorities::contains);
	}

}
