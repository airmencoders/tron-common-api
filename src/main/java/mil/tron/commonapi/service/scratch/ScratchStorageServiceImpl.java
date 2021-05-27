package mil.tron.commonapi.service.scratch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import mil.tron.commonapi.dto.ScratchStorageAppRegistryDto;
import mil.tron.commonapi.dto.ScratchStorageAppUserPrivDto;
import mil.tron.commonapi.dto.ScratchStorageEntryDto;
import mil.tron.commonapi.dto.ScratchStorageUserDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppRegistryEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppUserPriv;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageUser;
import mil.tron.commonapi.exception.*;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.scratch.ScratchStorageAppRegistryEntryRepository;
import mil.tron.commonapi.repository.scratch.ScratchStorageAppUserPrivRepository;
import mil.tron.commonapi.repository.scratch.ScratchStorageRepository;
import mil.tron.commonapi.repository.scratch.ScratchStorageUserRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class ScratchStorageServiceImpl implements ScratchStorageService {
    private static final String SCRATCH_WRITE_PRIV = "SCRATCH_WRITE";
    private static final String SCRATCH_READ_PRIV = "SCRATCH_READ";
    private static final String SCRATCH_ADMIN_PRIV = "SCRATCH_ADMIN";
    private static final String ACL_LIST_NAME_APPENDIX = "_acl";
    private static final String ACL_ACCESS_FIELD = "access";
    private static final String ACL_IMPLICIT_READ_FIELD = "implicitRead";
    private static final String WRITE = "KEY_WRITE"; //write role for acl-controlled keys
    private static final String READ = "KEY_READ"; // read role for acl-controlled keys
    private static final String ADMIN = "KEY_ADMIN";  // admin role for acl-controlled keys
    private static final String JSON_DB_KEY_TABLE_ERROR = "Cant find key/table with that name";
    private static final String JSON_TABLE_PARSE_ERROR = "Can't parse JSON in the table - %s";
    private static final String JSON_DB_SERIALIZATION_ERROR = "Error serializing table contents";
    private ScratchStorageRepository repository;
    private ScratchStorageAppRegistryEntryRepository appRegistryRepo;
    private ScratchStorageUserRepository scratchUserRepo;
    private ScratchStorageAppUserPrivRepository appPrivRepo;
    private PrivilegeRepository privRepo;
    private DtoMapper dtoMapper;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Configuration configuration = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .build();

    public ScratchStorageServiceImpl(ScratchStorageRepository repository,
                                     ScratchStorageAppRegistryEntryRepository appRegistryRepo,
                                     ScratchStorageUserRepository scratchUserRepo,
                                     ScratchStorageAppUserPrivRepository appPrivRepo,
                                     PrivilegeRepository privRepo) {
        this.repository = repository;
        this.appRegistryRepo = appRegistryRepo;
        this.scratchUserRepo = scratchUserRepo;
        this.appPrivRepo = appPrivRepo;
        this.privRepo = privRepo;
        this.dtoMapper = new DtoMapper();
    }

    /**
     * Private helper to validate an app exists by Id
     *
     * @param appId UUID of application
     * @returns the scratch storage app record
     */
    private ScratchStorageAppRegistryEntry validateAppId(UUID appId) {
        return appRegistryRepo.findById(appId).orElseThrow(() ->
            new RecordNotFoundException("No application with ID: " + appId + " was found"));
    }

    @Override
    public Iterable<ScratchStorageEntryDto> getAllEntries() {
        return Lists.newArrayList(repository.findAll())
                .stream()
                .map(item -> dtoMapper.map(item, ScratchStorageEntryDto.class))
                .collect(Collectors.toList());
    }

    @Override
    public Iterable<ScratchStorageEntryDto> getAllEntriesByApp(UUID appId) {
        validateAppId(appId);
        return Lists.newArrayList(repository.findAllByAppId(appId))
                .stream()
                .map(item -> dtoMapper.map(item, ScratchStorageEntryDto.class))
                .collect(Collectors.toList());
    }

    @Override
    public Iterable<String> getAllKeysForAppId(UUID appId) {
        validateAppId(appId);
        return repository.findAllKeysForAppId(appId);
    }

    @Override
    public ScratchStorageEntryDto getEntryById(UUID id) {
        return dtoMapper.map(repository
                .findById(id)
                .orElseThrow(() -> new RecordNotFoundException("Cannot find record with that UUID")), ScratchStorageEntryDto.class);
    }

    @Override
    public ScratchStorageEntryDto getKeyValueEntryByAppId(UUID appId, String keyName) {
        validateAppId(appId);
        return dtoMapper.map(repository
                .findByAppIdAndKey(appId, keyName)
                .orElseThrow(() -> new RecordNotFoundException("Cannot find record with that AppId/Key Name")), ScratchStorageEntryDto.class);
    }

    /**
     * Mimics adding/setting values in a HashMap, if key/value doesn't exist then its added, otherwise updated
     *
     * @param appId the UUID of the application which this key value pair is for
     * @param key   the String key of the key value pair
     * @param value the String value
     * @return the persisted/updated ScratchStorageEntry entity
     */
    @Override
    public ScratchStorageEntryDto setKeyValuePair(UUID appId, String key, String value) {

        validateAppId(appId);
        Optional<ScratchStorageEntry> entry = repository.findByAppIdAndKey(appId, key);

        if (entry.isPresent()) {
            // update
            ScratchStorageEntry existingEntry = entry.get();
            existingEntry.setValue(value);
            return dtoMapper.map(repository.save(existingEntry), ScratchStorageEntryDto.class);
        } else {
            // create new
            ScratchStorageEntry newEntry = ScratchStorageEntry
                    .builder()
                    .id(UUID.randomUUID())
                    .appId(appId)
                    .key(key)
                    .value(value)
                    .build();

            return dtoMapper.map(repository.save(newEntry), ScratchStorageEntryDto.class);
        }

    }

    @Override
    public ScratchStorageEntryDto deleteKeyValuePair(UUID appId, String key) {

        ScratchStorageAppRegistryEntry appEntry = validateAppId(appId);

        if (appEntry.isAclMode()) {
            // look for _acl appended key to delete too
            repository.deleteByAppIdAndKey(appId, key + ACL_LIST_NAME_APPENDIX);
        }

        ScratchStorageEntry entry = repository.findByAppIdAndKey(appId, key).orElseThrow(() ->
                new RecordNotFoundException("Cannot delete specified record, record not found"));

        repository.deleteByAppIdAndKey(appId, key);

        // return the entity deleted
        return dtoMapper.map(entry, ScratchStorageEntryDto.class);
    }

    @Override
    public Iterable<ScratchStorageEntryDto> deleteAllKeyValuePairsForAppId(UUID appId) {

        validateAppId(appId);

        List<ScratchStorageEntryDto> deletedEntries = new ArrayList<>();
        List<ScratchStorageEntry> entries = Lists.newArrayList(repository.findAllByAppId(appId));
        for (ScratchStorageEntry entry : entries) {
            repository.deleteById(entry.getId());
            deletedEntries.add(dtoMapper.map(entry, ScratchStorageEntryDto.class));
        }

        // return all key-values deleted
        return deletedEntries;
    }

    // ***************************************** //
    // Scratch storage app management functions  //
    // ***************************************** //

    @Override
    public Iterable<ScratchStorageAppRegistryDto> getAllRegisteredScratchApps() {
        return StreamSupport
                .stream(appRegistryRepo.findAll().spliterator(), false)
                .map(item -> dtoMapper.map(item, ScratchStorageAppRegistryDto.class))
                .collect(Collectors.toList());
    }

    @Override
    public Iterable<ScratchStorageAppRegistryDto> getAllScratchAppsContainingUser(String userEmail) {
        return StreamSupport.stream(appRegistryRepo.findAllAppsWithUserEmail(userEmail).spliterator(), false)
                .map(item -> {
                    // Strip out all other user privs that do not belong to the authorized user
                    for (Iterator<ScratchStorageAppUserPriv> privsIter = item.getUserPrivs().iterator(); privsIter.hasNext(); ) {
                        ScratchStorageAppUserPriv next = privsIter.next();
                        if (!next.getUser().getEmail().equals(userEmail)) {
                            privsIter.remove();
                        }
                    }

                    return dtoMapper.map(item, ScratchStorageAppRegistryDto.class);
                })
                .collect(Collectors.toList());
    }

    @Override
    public ScratchStorageAppRegistryDto getRegisteredScratchApp(UUID appId) {
        ScratchStorageAppRegistryEntry entry = appRegistryRepo.findById(appId).orElseThrow(() ->
                new RecordNotFoundException("App with ID " + appId + " not found"));

        return dtoMapper.map(entry, ScratchStorageAppRegistryDto.class);
    }

    @Override
    public ScratchStorageAppRegistryDto addNewScratchAppName(ScratchStorageAppRegistryDto entry) {
        if (entry.getId() == null) {
            entry.setId(UUID.randomUUID());
        }

        // check here for dups - even though at the db layer it will be inhibited -- albeit with a nasty 500 error there
        if (appRegistryRepo.existsById(entry.getId()) || appRegistryRepo.existsByAppNameIgnoreCase(entry.getAppName().trim())) {
            throw new ResourceAlreadyExistsException("Scratch Space app by that UUID or AppName already exists");
        }

        return dtoMapper.map(appRegistryRepo
                .save(dtoMapper.map(entry, ScratchStorageAppRegistryEntry.class)), ScratchStorageAppRegistryDto.class);
    }

    @Override
    public ScratchStorageAppRegistryDto editExistingScratchAppEntry(UUID id, ScratchStorageAppRegistryDto entry) {
        if (!id.equals(entry.getId()))
            throw new InvalidRecordUpdateRequest(String.format("ID: %s does not match the resource ID: %s", id, entry.getId()));

        // check here for dups - even though at the db layer it will be inhibited -- albeit with a nasty 500 error there
        ScratchStorageAppRegistryEntry dbAppRegistry = appRegistryRepo
                .findById(id)
                .orElseThrow(() -> new RecordNotFoundException("Scratch Space app by that UUID does not exist"));

        if (!dbAppRegistry.getAppName().equalsIgnoreCase(entry.getAppName()) &&
                appRegistryRepo.existsByAppNameIgnoreCase(entry.getAppName().trim())) {
            throw new ResourceAlreadyExistsException("Scratch space application with that name already exists");
        }

        return dtoMapper.map(appRegistryRepo
                .save(dtoMapper.map(entry, ScratchStorageAppRegistryEntry.class)), ScratchStorageAppRegistryDto.class);
    }

    @Override
    public ScratchStorageAppRegistryDto deleteScratchStorageApp(UUID id) {

        ScratchStorageAppRegistryEntry app = appRegistryRepo.findById(id)
                .orElseThrow(() -> new RecordNotFoundException("Cannot delete non-existent app with UUID: " + id));

        // delete all its priv pairs too
        //  have to remake a new hash set each iteration otherwise get a ConcurrentModificationException
        for (ScratchStorageAppUserPriv item : new HashSet<>(app.getUserPrivs())) {
            if (appPrivRepo.existsById(item.getId())) {
                app.removeUserAndPriv(item);  // remove the user-priv from the app
                appPrivRepo.deleteById(item.getId()); // delete the user-priv in the db
            }
        }

        // delete all its key-value pairs
        deleteAllKeyValuePairsForAppId(app.getId());

        appRegistryRepo.deleteById(id);

        return dtoMapper.map(app, ScratchStorageAppRegistryDto.class);
    }

    @Override
    public ScratchStorageAppRegistryDto addUserPrivToApp(UUID appId, ScratchStorageAppUserPrivDto priv) {

        ScratchStorageAppRegistryEntry app = appRegistryRepo.findById(appId)
                .orElseThrow(() -> new RecordNotFoundException("Cannot find app with UUID: " + appId));

        for (ScratchStorageAppUserPriv item : new HashSet<>(app.getUserPrivs())) {
            // make sure this unique user-priv set doesn't already exist
            if (item.getPrivilege().getId().equals(priv.getPrivilegeId()) && item.getUser().getEmail().equalsIgnoreCase(priv.getEmail())) {
                throw new ResourceAlreadyExistsException("A User-Privilege entry already exists for given App Id");
            }

            // see if this priv id and the user id are the same -- if they are then we're UPDATING a privilege
            //  delete the existing priv so that it'll be added again below (effectively overwriting) - otherwise you
            //  get a unique key exception
            if (item.getUser().getEmail().equalsIgnoreCase(priv.getEmail()) && item.getId().equals(priv.getId())) {
                app.removeUserAndPriv(item);
                appPrivRepo.deleteById(item.getId()); // delete the user-priv in the db
            }
        }

        ScratchStorageAppUserPriv entity = this.mapUserPrivDtoToEntity(priv);

        // save the app user priv entity to db
        appPrivRepo.save(entity);
        app.addUserAndPriv(entity);

        return dtoMapper.map(appRegistryRepo.save(app), ScratchStorageAppRegistryDto.class);
    }

    @Override
    public ScratchStorageAppRegistryDto removeUserPrivFromApp(UUID appId, UUID appPrivIdEntry) {

        ScratchStorageAppRegistryEntry app = appRegistryRepo.findById(appId)
                .orElseThrow(() -> new RecordNotFoundException("Cannot find app with UUID: " + appId));

        ScratchStorageAppUserPriv priv = appPrivRepo.findById(appPrivIdEntry)
                .orElseThrow(() -> new RecordNotFoundException("Cannot find that app-user priv entry with ID: " + appPrivIdEntry));

        app.removeUserAndPriv(priv);

        // delete the priv combo
        appPrivRepo.deleteById(appPrivIdEntry);

        return dtoMapper.map(appRegistryRepo.save(app), ScratchStorageAppRegistryDto.class);
    }

    /**
     * Private helper to unroll a ScratchStorageAppUserPrivDto into an entity.
     * In these DTO types, the user and priv come in as a UUID
     *
     * @param dto
     * @return the full blown entity of type ScratchStorageAppUserPriv
     */
    private ScratchStorageAppUserPriv mapUserPrivDtoToEntity(ScratchStorageAppUserPrivDto dto) {

        Optional<ScratchStorageUser> user = scratchUserRepo.findByEmailIgnoreCase(dto.getEmail());
        ScratchStorageUserDto appUser;
        if (user.isEmpty()) {
            // user didn't exist, create them in the scratch space universe, and attach only to
            //  this current app
            appUser = this.addNewScratchUser(ScratchStorageUserDto.
                    builder()
                    .id(UUID.randomUUID())
                    .email(dto.getEmail())
                    .build());

        } else {
            // user existed already, no worries, just use the return from the db
            appUser = dtoMapper.map(user.get(), ScratchStorageUserDto.class);
        }


        Privilege priv = privRepo.findById(dto.getPrivilegeId())
                .orElseThrow(() -> new RecordNotFoundException("Could not find privilege with ID: " + dto.getPrivilegeId()));

        return ScratchStorageAppUserPriv
                .builder()
                .id(dto.getId())
                .user(dtoMapper.map(appUser, ScratchStorageUser.class))
                .privilege(priv)
                .build();

    }

    // ******************************************* //
    // Scratch Storage user management functions   //
    // ******************************************  //


    @Override
    public Iterable<ScratchStorageUserDto> getAllScratchUsers() {
        return Lists.newArrayList(scratchUserRepo.findAll())
                .stream()
                .map(item -> dtoMapper.map(item, ScratchStorageUserDto.class))
                .collect(Collectors.toList());
    }

    @Override
    public ScratchStorageUserDto addNewScratchUser(ScratchStorageUserDto newUser) {

        ScratchStorageUser user = dtoMapper.map(newUser, ScratchStorageUser.class);

        if (user.getId() == null) {
            user.setId(UUID.randomUUID());
        }

        // check here for dups - even though at the db layer it will be inhibited -- albeit with a nasty 500 error there
        if (scratchUserRepo.existsById(user.getId()) || scratchUserRepo.existsByEmailIgnoreCase(user.getEmail())) {
            throw new ResourceAlreadyExistsException("Scratch Space user with that UUID or email already exists");
        }

        return dtoMapper.map(scratchUserRepo.save(user), ScratchStorageUserDto.class);
    }

    @Override
    public ScratchStorageUserDto editScratchUser(UUID id, ScratchStorageUserDto existingUser) {

        ScratchStorageUser user = dtoMapper.map(existingUser, ScratchStorageUser.class);

        if (!id.equals(user.getId()))
            throw new InvalidRecordUpdateRequest(String.format("ID: %s does not match the resource ID: %s", id, user.getId()));

        if (!scratchUserRepo.existsById(user.getId())) {
            throw new RecordNotFoundException("Scratch User with ID: " + user.getId() + " does not exist");
        }

        // check here for dups - even though at the db layer it will be inhibited -- albeit with a nasty 500 error there
        if (scratchUserRepo.existsByEmailIgnoreCase(user.getEmail())) {
            throw new ResourceAlreadyExistsException("Scratch Space user already exists with that email address");
        }

        return dtoMapper.map(scratchUserRepo.save(user), ScratchStorageUserDto.class);
    }

    @Override
    public ScratchStorageUserDto deleteScratchUser(UUID id) {
        ScratchStorageUser user = scratchUserRepo.findById(id)
                .orElseThrow(() -> new RecordNotFoundException("Cannot delete non-existent scratch user with UUID: " + id));

        // must go thru the app universe and delete all privs associated with this user first
        List<ScratchStorageAppRegistryEntry> allUserApps = Lists.newArrayList(appRegistryRepo.findAll());
        for (ScratchStorageAppRegistryEntry app : allUserApps) {
            for (ScratchStorageAppUserPriv item : new HashSet<>(app.getUserPrivs())) {
                if (item.getUser().getId().equals(id)) {
                    app.removeUserAndPriv(item);
                    appPrivRepo.deleteById(item.getId());
                }
            }
        }

        scratchUserRepo.deleteById(id);

        return dtoMapper.map(user, ScratchStorageUserDto.class);
    }

    /**
     * Private helper to validate a given appId is real and registered as a scratch app
     *
     * @param appId UUID of the app to check
     * @return the app's record or throws a RecordNotFoundException
     */
    private ScratchStorageAppRegistryEntry validateAppIsRealAndRegistered(UUID appId) {

        // get the appId after validating its a real app ID that's registered
        return appRegistryRepo.findById(appId)
                .orElseThrow(() -> new RecordNotFoundException("Application with ID " + appId + " doesn't exist"));

    }

    /**
     * Utility function used by the controller to check if a given user email has
     * write access to the given appId's space
     *
     * @param appId the appId to check against
     * @param email the email to check for write access
     * @param keyName key name to adjudicate for acl based access
     * @return true if user has write access else false
     */
    @Override
    public boolean userCanWriteToAppId(UUID appId, String email, String keyName) {

        ScratchStorageAppRegistryEntry appEntry = this.validateAppIsRealAndRegistered(appId);

        // respect aclMode first if its enabled
        if (appEntry.isAclMode()) {

            // if we're in ACL mode and were going to mutate an ACL itself, we have to be a KEY_ADMIN to do it
            if (keyName.endsWith(ACL_LIST_NAME_APPENDIX)) {
                return aclLookup(appEntry, email, keyName.split("_")[0], ADMIN);
            }
            else {
                // otherwise we just need write permissions on the key to mutate it
                return aclLookup(appEntry, email, keyName, WRITE);
            }
        }

        for (ScratchStorageAppUserPriv priv : appEntry.getUserPrivs()) {
            if (priv.getUser().getEmail().equalsIgnoreCase(email)
                    // check for WRITE or ADMIN access...
                    && (priv.getPrivilege().getName().equals(SCRATCH_WRITE_PRIV)
                    || priv.getPrivilege().getName().equals(SCRATCH_ADMIN_PRIV)))
                return true;
        }

        return false;
    }

    /**
     * Utility function to set/un-set the implicit read property for a given app
     *
     * @param appId        UUID of app to modify
     * @param implicitRead value to set the implicit read field to
     * @return the modified App record or throws exception if appId wasn't valid
     */
    @Override
    public ScratchStorageAppRegistryDto setImplicitReadForApp(UUID appId, boolean implicitRead) {
        ScratchStorageAppRegistryEntry appEntry = this.validateAppIsRealAndRegistered(appId);
        appEntry.setAppHasImplicitRead(implicitRead);
        return dtoMapper.map(appRegistryRepo.save(appEntry), ScratchStorageAppRegistryDto.class);
    }

    /**
     * Utility function to set/un-set the ACL mode for the given app.
     *
     * @param appId         UUID of app to modify
     * @param aclMode       value to set the aclMode field to
     * @return the modified App record or throws exception if appId wasn't valid
     */
    @Override
    public ScratchStorageAppRegistryDto setAclModeForApp(UUID appId, boolean aclMode) {
        ScratchStorageAppRegistryEntry appEntry = this.validateAppIsRealAndRegistered(appId);
        appEntry.setAclMode(aclMode);
        return dtoMapper.map(appRegistryRepo.save(appEntry), ScratchStorageAppRegistryDto.class);
    }

    /**
     * For an appId that's in Acl Mode, this method looks up the ACL ("acl") and makes sure
     * that the given email has desiredRole (or higher) for given keyName.  If the acl does not exist
     * or its json structure is not valid, we throw appropriate error.
     * @param appEntry the app were on
     * @param email the email we're validating for
     * @param keyName the key name
     * @param desiredRole the role that the email should at least have
     * @return true if access criteria is met otherwise false
     */
    @Override
    public boolean aclLookup(ScratchStorageAppRegistryEntry appEntry,
                             String email,
                             String keyName,
                             String desiredRole) {

        if (!appEntry.isAclMode()) throw new InvalidScratchSpacePermissions("Given app expected ACL Mode");

        // skip all checks if the requester is a SCRATCH_ADMIN, they can do anything within the app data
        //  that way an admin can fix json problems/corruption that may occur
        if (userHasAdminWithAppId(appEntry.getId(), email)) return true;

        // the lookup function will throw if the associated "acl" key does not exist
        String aclValue = this.getKeyValueEntryByAppId(appEntry.getId(), keyName + ACL_LIST_NAME_APPENDIX).getValue();

        JsonNode aclNodes;
        try {
            aclNodes = MAPPER.readTree(aclValue);

            // must have an implicitRead member - that is boolean
            if (!aclNodes.has(ACL_IMPLICIT_READ_FIELD) || !aclNodes.get(ACL_IMPLICIT_READ_FIELD).isBoolean()) {
                throw new InvalidFieldValueException(String.format("ACL for keyName %s missing implicitRead field or is not boolean", keyName));
            }

            // acl must have access field that is an array
            if (!aclNodes.has(ACL_ACCESS_FIELD) || !aclNodes.get(ACL_ACCESS_FIELD).isObject()) {
                throw new InvalidFieldValueException(String.format("ACL for keyName %s missing access field or is not an object", keyName));
            }

            // if accessing an ACL and requester is not a KEY_ADMIN for it, then deny even reading it
            if (keyName.endsWith(ACL_LIST_NAME_APPENDIX)
                    && !aclNodes.get(ACL_ACCESS_FIELD).get(email).textValue().equals("ADMIN")) {

                return false;
            }

            switch (desiredRole) {
                case READ:
                    if (aclNodes.get(ACL_IMPLICIT_READ_FIELD).booleanValue()) return true;  // implicit read for this key for all
                    if (aclNodes.get(ACL_ACCESS_FIELD).has(email.toLowerCase())
                            && (aclNodes.get(ACL_ACCESS_FIELD).get(email).textValue().equals(READ)
                                || aclNodes.get(ACL_ACCESS_FIELD).get(email).textValue().equals(WRITE)
                                || aclNodes.get(ACL_ACCESS_FIELD).get(email).textValue().equals(ADMIN))) {
                            return true;
                     }
                    break;
                case WRITE:
                    if (aclNodes.get(ACL_ACCESS_FIELD).has(email.toLowerCase())
                            && (aclNodes.get(ACL_ACCESS_FIELD).get(email).textValue().equals(WRITE)
                                || aclNodes.get(ACL_ACCESS_FIELD).get(email).textValue().equals(ADMIN))) {
                        return true;
                    }
                    break;
                case ADMIN:
                    if (aclNodes.get(ACL_ACCESS_FIELD).has(email.toLowerCase())
                            && aclNodes.get(ACL_ACCESS_FIELD).get(email).textValue().equals(ADMIN)) {
                        return true;
                    }
                    break;
                default:
                    throw new InvalidFieldValueException(String.format("ACL %s_acl has unknown permission in it", keyName));
            }
        }
        catch (JsonProcessingException e) {
            throw new InvalidFieldValueException(String.format("Could not parse the ACL json for keyName - %s", keyName));
        }

        return false;
    }

    /**
     * Utility function used by the controller to check if a given user email has
     * delete (destructive) rights on a particular key(s) in the given appId's space
     *
     * @param appId the appId to check against
     * @param email the email to check for read access
     * @param keyName key name to adjudicate for acl based access
     * @return true if user has read access else false
     */
    @Override
    public boolean userCanDeleteKeyForAppId(UUID appId, String email, String keyName) {

        ScratchStorageAppRegistryEntry appEntry = this.validateAppIsRealAndRegistered(appId);

        // respect aclMode first if its enabled
        if (appEntry.isAclMode()) {
            return aclLookup(appEntry, email, keyName, ADMIN);
        }

        // if we get here, not in aclMode must be a SCRATCH_ADMIN to delete
        for (ScratchStorageAppUserPriv priv : appEntry.getUserPrivs()) {
            if (priv.getUser().getEmail().equalsIgnoreCase(email)
                    && (priv.getPrivilege().getName().equals(SCRATCH_ADMIN_PRIV)))
                return true;
        }

        return false;
    }


    /**
     * Utility function used by the controller to check if a given user email has
     * read access to the given appId's space
     *
     * @param appId the appId to check against
     * @param email the email to check for read access
     * @param keyName key name to adjudicate for acl based access
     * @return true if user has read access else false
     */
    @Override
    public boolean userCanReadFromAppId(UUID appId, String email, String keyName) {

        ScratchStorageAppRegistryEntry appEntry = this.validateAppIsRealAndRegistered(appId);

        // respect aclMode first if its enabled
        if (appEntry.isAclMode()) {

            // restrict ADMINs of the KEYs to be able to read ACLs
            if (keyName.endsWith(ACL_LIST_NAME_APPENDIX)) {
                return aclLookup(appEntry, email, keyName.split("_")[0], ADMIN);
            }
            else {
                return aclLookup(appEntry, email, keyName, READ);
            }
        }

        // if this app has implicit read set to True, then we're done here...
        if (appEntry.isAppHasImplicitRead()) return true;

        // if we get here, not in aclMode and app doesn't have implicitRead so analyze user's perms for adjudication
        for (ScratchStorageAppUserPriv priv : appEntry.getUserPrivs()) {
            if (priv.getUser().getEmail().equalsIgnoreCase(email)
                    && (priv.getPrivilege().getName().equals(SCRATCH_READ_PRIV)
                    || priv.getPrivilege().getName().equals(SCRATCH_WRITE_PRIV)
                    || priv.getPrivilege().getName().equals(SCRATCH_ADMIN_PRIV)))
                return true;
        }

        return false;
    }

    /**
     * Utility function to check if given email is registered as a SCRATCH_ADMIN
     * to given appId's space
     *
     * @param appId the appId to check against
     * @param email the email to check for ADMIN status
     * @return true if admin else false
     */
    @Override
    public boolean userHasAdminWithAppId(UUID appId, String email) {

        ScratchStorageAppRegistryEntry appEntry = this.validateAppIsRealAndRegistered(appId);

        // we dont care about aclMode here, if they're a SCRATCH_ADMIN they can bypass acls.

        for (ScratchStorageAppUserPriv priv : appEntry.getUserPrivs()) {
            if (priv.getUser().getEmail().equalsIgnoreCase(email)
                    && priv.getPrivilege().getName().equals(SCRATCH_ADMIN_PRIV))
                return true;
        }

        return false;
    }

    /**
     * Gets an existing key value-pair by appId and key name.  And then applies given JsonPath specification/query
     * to it and returns the result (if any) in Json format.
     *
     * @param appId        scratch app UUID
     * @param keyName      key name
     * @param jsonPathSpec the JayWay JsonPath specification string
     * @return the result of the JsonPath query
     */
    @Override
    public String getKeyValueJson(UUID appId, String keyName, String jsonPathSpec) {
        ScratchStorageEntryDto value = this.getKeyValueEntryByAppId(appId, keyName);

        try {
            Object results = JsonPath.parse(value.getValue()).read(jsonPathSpec);
            return new ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(results);
        } catch (PathNotFoundException e) {
            throw new RecordNotFoundException("Json Path not found");
        } catch (JsonProcessingException e) {
            throw new InvalidFieldValueException("Return value Could not be serialized to Json");
        } catch (InvalidJsonException e) {
            throw new InvalidFieldValueException("Source value not valid Json");
        }
    }

    /**
     * Updates a portion of a Json structure given a JsonPath and value
     *
     * @param appId        the UUID of the scratch app
     * @param keyName      the key name of the existing key-value pair
     * @param value        the value to set in the Json
     * @param jsonPathSpec the Jayway JsonPath specification string
     */
    @Override
    public void patchKeyValueJson(UUID appId, String keyName, String value, String jsonPathSpec) {

        // must already exist as a key-value pair to modify...
        ScratchStorageEntryDto existingJsonValue = this.getKeyValueEntryByAppId(appId, keyName);

        try {
            // parse it to Json
            DocumentContext results = JsonPath.parse(existingJsonValue.getValue()).set(jsonPathSpec, value);

            // write the modified json structure back to the db as a string
            this.setKeyValuePair(appId, keyName, results.jsonString());
        } catch (InvalidJsonException e) {
            throw new InvalidFieldValueException("Source Value Not Valid Json");
        }
    }
}
