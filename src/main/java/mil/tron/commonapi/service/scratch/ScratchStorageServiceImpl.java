package mil.tron.commonapi.service.scratch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import mil.tron.commonapi.dto.ScratchStorageAppRegistryDto;
import mil.tron.commonapi.dto.ScratchStorageAppUserPrivDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppRegistryEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppUserPriv;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageUser;
import mil.tron.commonapi.exception.InvalidFieldValueException;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
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
    private ScratchStorageRepository repository;
    private ScratchStorageAppRegistryEntryRepository appRegistryRepo;
    private ScratchStorageUserRepository scratchUserRepo;
    private ScratchStorageAppUserPrivRepository appPrivRepo;
    private PrivilegeRepository privRepo;
    private DtoMapper dtoMapper;

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
     * @param appId UUID of application
     */
    private void validateAppId(UUID appId) {
        if (!appRegistryRepo.existsById(appId)) {
            throw new RecordNotFoundException("No application with ID: " + appId + " was found");
        }
    }

    @Override
    public Iterable<ScratchStorageEntry> getAllEntries() {
        return repository.findAll();
    }

    @Override
    public Iterable<ScratchStorageEntry> getAllEntriesByApp(UUID appId) {
        validateAppId(appId);
        return repository.findAllByAppId(appId);
    }

    @Override
    public Iterable<String> getAllKeysForAppId(UUID appId) {
        validateAppId(appId);
        return repository.findAllKeysForAppId(appId);
    }

    @Override
    public ScratchStorageEntry getEntryById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new RecordNotFoundException("Cannot find record with that UUID"));
    }

    @Override
    public ScratchStorageEntry getKeyValueEntryByAppId(UUID appId, String keyName) {
        validateAppId(appId);
        return repository.findByAppIdAndKey(appId, keyName).orElseThrow(() -> new RecordNotFoundException("Cannot find record with that AppId/Key Name"));
    }

    /**
     * Mimics adding/setting values in a HashMap, if key/value doesn't exist then its added, otherwise updated
     * @param appId the UUID of the application which this key value pair is for
     * @param key the String key of the key value pair
     * @param value the String value
     * @return the persisted/updated ScratchStorageEntry entity
     */
    @Override
    public ScratchStorageEntry setKeyValuePair(UUID appId, String key, String value) {

        validateAppId(appId);
        Optional<ScratchStorageEntry> entry = repository.findByAppIdAndKey(appId, key);

        if (entry.isPresent()) {
            // update
            ScratchStorageEntry existingEntry = entry.get();
            existingEntry.setValue(value);
            return repository.save(existingEntry);
        } else {
            // create new
            ScratchStorageEntry newEntry = ScratchStorageEntry
                    .builder()
                    .id(UUID.randomUUID())
                    .appId(appId)
                    .key(key)
                    .value(value)
                    .build();

            return repository.save(newEntry);
        }

    }

    @Override
    public ScratchStorageEntry deleteKeyValuePair(UUID appId, String key) {

        validateAppId(appId);

        ScratchStorageEntry entry = repository.findByAppIdAndKey(appId, key).orElseThrow(() ->
                new RecordNotFoundException("Cannot delete specified record, record not found"));

        repository.deleteByAppIdAndKey(appId, key);

        // return the entity deleted
        return entry;
    }

    @Override
    public Iterable<ScratchStorageEntry> deleteAllKeyValuePairsForAppId(UUID appId) {

        validateAppId(appId);

        List<ScratchStorageEntry> deletedEntries = new ArrayList<>();
        List<ScratchStorageEntry> entries = Lists.newArrayList(repository.findAllByAppId(appId));
        for (ScratchStorageEntry entry : entries) {
            repository.deleteById(entry.getId());
            deletedEntries.add(entry);
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
        			for (Iterator<ScratchStorageAppUserPriv> privsIter = item.getUserPrivs().iterator(); privsIter.hasNext();) {
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
    public ScratchStorageAppRegistryEntry addNewScratchAppName(ScratchStorageAppRegistryEntry entry) {
        if (entry.getId() == null) {
            entry.setId(UUID.randomUUID());
        }

        // check here for dups - even though at the db layer it will be inhibited -- albeit with a nasty 500 error there
        if (appRegistryRepo.existsById(entry.getId()) || appRegistryRepo.existsByAppNameIgnoreCase(entry.getAppName().trim())) {
            throw new ResourceAlreadyExistsException("Scratch Space app by that UUID or AppName already exists");
        }

        return appRegistryRepo.save(entry);
    }

    @Override
    public ScratchStorageAppRegistryEntry editExistingScratchAppEntry(UUID id, ScratchStorageAppRegistryEntry entry) {
        if (!id.equals(entry.getId()))
            throw new InvalidRecordUpdateRequest(String.format("ID: %s does not match the resource ID: %s", id, entry.getId()));

        if (!appRegistryRepo.existsById(entry.getId())) {
            throw new RecordNotFoundException("Scratch Space app by that UUID does not exist");
        }

        // check here for dups - even though at the db layer it will be inhibited -- albeit with a nasty 500 error there
        Optional<ScratchStorageAppRegistryEntry> dbAppRegistry = appRegistryRepo.findById(id);

        if (!dbAppRegistry.get().getAppName().equalsIgnoreCase(entry.getAppName()) &&
            appRegistryRepo.existsByAppNameIgnoreCase(entry.getAppName().trim())) {
            throw new ResourceAlreadyExistsException("Scratch space application with that name already exists");
        }

        return appRegistryRepo.save(entry);
    }

    @Override
    public ScratchStorageAppRegistryEntry deleteScratchStorageApp(UUID id) {

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

        return app;
    }

    @Override
    public ScratchStorageAppRegistryEntry addUserPrivToApp(UUID appId, ScratchStorageAppUserPrivDto priv) {

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

        ScratchStorageAppUserPriv entity =  this.mapUserPrivDtoToEntity(priv);

        // save the app user priv entity to db
        appPrivRepo.save(entity);

        app.addUserAndPriv(entity);
        return appRegistryRepo.save(app);
    }

    @Override
    public ScratchStorageAppRegistryEntry removeUserPrivFromApp(UUID appId, UUID appPrivIdEntry) {

        ScratchStorageAppRegistryEntry app = appRegistryRepo.findById(appId)
                .orElseThrow(() -> new RecordNotFoundException("Cannot find app with UUID: " + appId));

        ScratchStorageAppUserPriv priv = appPrivRepo.findById(appPrivIdEntry)
                .orElseThrow(() -> new RecordNotFoundException("Cannot find that app-user priv entry with ID: " + appPrivIdEntry));

        app.removeUserAndPriv(priv);

        // delete the priv combo
        appPrivRepo.deleteById(appPrivIdEntry);

        return appRegistryRepo.save(app);
    }

    /**
     * Private helper to unroll a ScratchStorageAppUserPrivDto into an entity.
     * In these DTO types, the user and priv come in as a UUID
     * @param dto
     * @return the full blown entity of type ScratchStorageAppUserPriv
     */
    private ScratchStorageAppUserPriv mapUserPrivDtoToEntity(ScratchStorageAppUserPrivDto dto) {

        Optional<ScratchStorageUser> user = scratchUserRepo.findByEmailIgnoreCase(dto.getEmail());
        ScratchStorageUser appUser;
        if (user.isEmpty()) {
            // user didn't exist, create them in the scratch space universe, and attach only to
            //  this current app
            appUser = this.addNewScratchUser(ScratchStorageUser.
                    builder()
                    .id(UUID.randomUUID())
                    .email(dto.getEmail())
                    .build());

        }
        else {
            // user existed already, no worries, just use the return from the db
            appUser = user.get();
        }


        Privilege priv = privRepo.findById(dto.getPrivilegeId())
                .orElseThrow(() -> new RecordNotFoundException("Could not find privilege with ID: " + dto.getPrivilegeId()));

        return ScratchStorageAppUserPriv
                .builder()
                .id(dto.getId())
                .user(appUser)
                .privilege(priv)
                .build();

    }

    // ******************************************* //
    // Scratch Storage user management functions   //
    // ******************************************  //


    @Override
    public Iterable<ScratchStorageUser> getAllScratchUsers() {
        return scratchUserRepo.findAll();
    }

    @Override
    public ScratchStorageUser addNewScratchUser(ScratchStorageUser user) {
        if (user.getId() == null) {
            user.setId(UUID.randomUUID());
        }

        // check here for dups - even though at the db layer it will be inhibited -- albeit with a nasty 500 error there
        if (scratchUserRepo.existsById(user.getId()) || scratchUserRepo.existsByEmailIgnoreCase(user.getEmail())) {
            throw new ResourceAlreadyExistsException("Scratch Space user with that UUID or email already exists");
        }

        return scratchUserRepo.save(user);
    }

    @Override
    public ScratchStorageUser editScratchUser(UUID id, ScratchStorageUser user) {
        if (!id.equals(user.getId()))
            throw new InvalidRecordUpdateRequest(String.format("ID: %s does not match the resource ID: %s", id, user.getId()));

        if (!scratchUserRepo.existsById(user.getId())) {
            throw new RecordNotFoundException("Scratch User with ID: " + user.getId() + " does not exist");
        }

        // check here for dups - even though at the db layer it will be inhibited -- albeit with a nasty 500 error there
        if (scratchUserRepo.existsByEmailIgnoreCase(user.getEmail())) {
            throw new ResourceAlreadyExistsException("Scratch Space user already exists with that email address");
        }

        return scratchUserRepo.save(user);
    }

    @Override
    public ScratchStorageUser deleteScratchUser(UUID id) {
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
        return user;
    }

    /**
     * Private helper to validate a given appId is real and registered as a scratch app
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
     * @param appId the appId to check against
     * @param email the email to check for write access
     * @return true if user has write access else false
     */
    @Override
    public boolean userCanWriteToAppId(UUID appId, String email) {

        ScratchStorageAppRegistryEntry appEntry = this.validateAppIsRealAndRegistered(appId);

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
     * @param appId UUID of app to modify
     * @param implicitRead value to set the implicit read field to
     * @return the modified App record or throws exception if appId wasn't valid
     */
    @Override
    public ScratchStorageAppRegistryEntry setImplicitReadForApp(UUID appId, boolean implicitRead) {
        ScratchStorageAppRegistryEntry appEntry = this.validateAppIsRealAndRegistered(appId);
        appEntry.setAppHasImplicitRead(implicitRead);
        return appRegistryRepo.save(appEntry);
    }

    /**
     * Utility function used by the controller to check if a given user email has
     * read access to the given appId's space
     * @param appId the appId to check against
     * @param email the email to check for read access
     * @return true if user has read access else false
     */
    @Override
    public boolean userCanReadFromAppId(UUID appId, String email) {

        ScratchStorageAppRegistryEntry appEntry = this.validateAppIsRealAndRegistered(appId);

        // if this app has implicit read set to True, then we're done here...
        if (appEntry.isAppHasImplicitRead()) return true;

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
     * @param appId the appId to check against
     * @param email the email to check for ADMIN status
     * @return true if admin else false
     */
    @Override
    public boolean userHasAdminWithAppId(UUID appId, String email) {

        ScratchStorageAppRegistryEntry appEntry = this.validateAppIsRealAndRegistered(appId);

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
     * @param appId scratch app UUID
     * @param keyName key name
     * @param jsonPathSpec the JayWay JsonPath specification string
     * @return the result of the JsonPath query
     */
    @Override
    public String getKeyValueJson(UUID appId, String keyName, String jsonPathSpec) {
        ScratchStorageEntry value = this.getKeyValueEntryByAppId(appId, keyName);

        try {
            Object results = JsonPath.parse(value.getValue()).read(jsonPathSpec);
            return new ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(results);
        }
        catch (PathNotFoundException e) {
            throw new RecordNotFoundException("Json Path not found");
        }
        catch (JsonProcessingException e) {
            throw new InvalidFieldValueException("Return value Could not be serialized to Json");
        }
        catch (InvalidJsonException e) {
            throw new InvalidFieldValueException("Source value not valid Json");
        }
    }

    /**
     * Updates a portion of a Json structure given a JsonPath and value
     * @param appId the UUID of the scratch app
     * @param keyName the key name of the existing key-value pair
     * @param value the value to set in the Json
     * @param jsonPathSpec the Jayway JsonPath specification string
     */
    @Override
    public void patchKeyValueJson(UUID appId, String keyName, String value, String jsonPathSpec) {

        // must already exist as a key-value pair to modify...
        ScratchStorageEntry existingJsonValue = this.getKeyValueEntryByAppId(appId, keyName);

        try {
            // parse it to Json
            DocumentContext results = JsonPath.parse(existingJsonValue.getValue()).set(jsonPathSpec, value);

            // write the modified json structure back to the db as a string
            this.setKeyValuePair(appId, keyName, results.jsonString());
        }
        catch (InvalidJsonException e) {
            throw new InvalidFieldValueException("Source Value Not Valid Json");
        }
    }
}
