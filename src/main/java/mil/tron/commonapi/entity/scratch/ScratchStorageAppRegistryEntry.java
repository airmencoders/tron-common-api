package mil.tron.commonapi.entity.scratch;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import javax.persistence.*;
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
@Table(name="scratch_storage_app_registry", uniqueConstraints = { @UniqueConstraint(columnNames = "appNameAsLower") })
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

    /**
     * The String name of the app
     */
    @Getter
    @Setter
    @NotBlank
    @NotNull
    private String appName;

    @JsonIgnore
    private String appNameAsLower;

    @Getter
    @Setter
    private boolean appHasImplicitRead = false;

    /**
     * Whether we use ACLs to control access to this app's key-values
     */
    @Getter
    @Setter
    @Column(name = "acl_mode")
    private boolean aclMode = false;

    /**
     * Collection of user privs for this registered app name
     */
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

    @PreUpdate
    @PrePersist
    public void sanitizeEntity() {
        trimStrings();
        sanitizeAppNameForUniqueConstraint();
    }

    private void sanitizeAppNameForUniqueConstraint() {
        appNameAsLower = appName == null ? null : appName.toLowerCase();
    }

    private void trimStrings() {
        appName = trim(appName);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

}
