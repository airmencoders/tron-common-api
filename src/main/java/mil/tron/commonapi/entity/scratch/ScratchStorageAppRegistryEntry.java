package mil.tron.commonapi.entity.scratch;

import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents an entry of an app name that has space available in the scratch storage table
 * (an app needs an entry here so it is allowed to have/store key value pairs)
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name="scratch_storage_app_registry")
public class ScratchStorageAppRegistryEntry {

    /**
     * This is essentially the UUID of the app - will be used from the outside
     * for scratch operations
     */
    @Id
    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    @NotBlank
    @NotNull
    private String appName;

    @Getter
    @Builder.Default
    @OneToMany
    private Set<ScratchStorageAppUserPriv> userPrivs = new HashSet<>();

    public void addUserAndPriv(ScratchStorageAppUserPriv priv) {
        this.userPrivs.add(priv);
    }

    public void removeUserAndPriv(ScratchStorageAppUserPriv priv) {
        this.userPrivs.remove(priv);
    }

}
