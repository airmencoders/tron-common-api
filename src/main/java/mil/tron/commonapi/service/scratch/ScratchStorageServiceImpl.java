package mil.tron.commonapi.service.scratch;

import com.google.common.collect.Lists;
import mil.tron.commonapi.dto.ScratchStorageAppUserPrivDto;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppRegistryEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppUserPriv;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.entity.scratch.ScratchStorageUser;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.scratch.ScratchStorageAppRegistryEntryRepository;
import mil.tron.commonapi.repository.scratch.ScratchStorageAppUserPrivRepository;
import mil.tron.commonapi.repository.scratch.ScratchStorageRepository;
import mil.tron.commonapi.repository.scratch.ScratchStorageUserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScratchStorageServiceImpl implements ScratchStorageService {

    private ScratchStorageRepository repository;
    private ScratchStorageAppRegistryEntryRepository appRegistryRepo;
    private ScratchStorageUserRepository scratchUserRepo;
    private ScratchStorageAppUserPrivRepository appPrivRepo;
    private PrivilegeRepository privRepo;


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
    }

    @Override
    public Iterable<ScratchStorageEntry> getAllEntries() {
        return repository.findAll();
    }

    @Override
    public Iterable<ScratchStorageEntry> getAllEntriesByApp(UUID appId) {
        return repository.findAllByAppId(appId);
    }

    @Override
    public ScratchStorageEntry getEntryById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new RecordNotFoundException("Cannot find record with that UUID"));
    }

    @Override
    public ScratchStorageEntry getKeyValueEntryByAppId(UUID appId, String keyName) {
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
        Optional<ScratchStorageEntry> entry = repository.findByAppIdAndKey(appId, key);

        if (entry.isPresent()) {
            // update
            ScratchStorageEntry existingEntry = entry.get();
            existingEntry.setValue(value);
            return repository.save(existingEntry);
        }
        else {
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
        ScratchStorageEntry entry = repository.findByAppIdAndKey(appId, key).orElseThrow(() ->
                new RecordNotFoundException("Cannot delete specified record, record not found"));

        repository.deleteByAppIdAndKey(appId, key);

        // return the entity deleted
        return entry;
    }

    @Override
    public Iterable<ScratchStorageEntry> deleteAllKeyValuePairsForAppId(UUID appId) {

        List<ScratchStorageEntry> deletedEntries = new ArrayList<>();
        List<ScratchStorageEntry> entries = Lists.newArrayList(repository.findAllByAppId(appId));
        for (ScratchStorageEntry entry : entries) {
            repository.deleteById(entry.getId());
            deletedEntries.add(entry);
        }

        // return all key-values deleted
        return deletedEntries;
    }

    //
    // scratch storage app management...

    @Override
    public Iterable<ScratchStorageAppRegistryEntry> getAllRegisteredScratchApps() {
        return appRegistryRepo.findAll();
    }

    @Override
    public ScratchStorageAppRegistryEntry addNewScratchAppName(ScratchStorageAppRegistryEntry entry) {
        if (entry.getId() == null) {
            entry.setId(UUID.randomUUID());
        }

        if (appRegistryRepo.existsById(entry.getId())) {
            throw new ResourceAlreadyExistsException("Scratch Space app by that UUID already exists");
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

        return appRegistryRepo.save(entry);
    }

    @Override
    public ScratchStorageAppRegistryEntry deleteScratchStorageApp(UUID id) {

        ScratchStorageAppRegistryEntry app = appRegistryRepo.findById(id)
                .orElseThrow(() -> new RecordNotFoundException("Cannot delete non-existent app with UUID: " + id));

        appRegistryRepo.deleteById(id);

        return app;
    }

    @Override
    public ScratchStorageAppRegistryEntry addUserPrivToApp(UUID appId, ScratchStorageAppUserPrivDto priv) {

        ScratchStorageAppRegistryEntry app = appRegistryRepo.findById(appId)
                .orElseThrow(() -> new RecordNotFoundException("Cannot find app with UUID: " + appId));

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

        ScratchStorageUser user = scratchUserRepo.findById(dto.getUserId())
                .orElseThrow(() -> new RecordNotFoundException("User for privilege not found"));

        Privilege priv = privRepo.findById(dto.getPrivilegeId())
                .orElseThrow(() -> new RecordNotFoundException("Could not find privilege with ID: " + dto.getPrivilegeId()));

        ScratchStorageAppUserPriv entity = ScratchStorageAppUserPriv
                .builder()
                .id(dto.getId())
                .user(user)
                .privilege(priv)
                .build();

        return entity;
    }

    //
    // Scratch Storage user management functions

    @Override
    public Iterable<ScratchStorageUser> getAllScratchUsers() {
        return scratchUserRepo.findAll();
    }

    @Override
    public ScratchStorageUser addNewScratchUser(ScratchStorageUser user) {
        if (user.getId() == null) {
            user.setId(UUID.randomUUID());
        }

        if (scratchUserRepo.existsById(user.getId())) {
            throw new ResourceAlreadyExistsException("Scratch Space user already exists");
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

        return scratchUserRepo.save(user);
    }

    @Override
    public ScratchStorageUser deleteScratchUser(UUID id) {
        ScratchStorageUser user = scratchUserRepo.findById(id)
                .orElseThrow(() -> new RecordNotFoundException("Cannot delete non-existent scratch user with UUID: " + id));

        scratchUserRepo.deleteById(id);
        return user;
    }
}
