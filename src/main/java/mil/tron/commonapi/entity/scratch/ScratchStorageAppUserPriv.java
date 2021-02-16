package mil.tron.commonapi.entity.scratch;

import lombok.*;
import mil.tron.commonapi.entity.Privilege;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.Table;
import java.util.UUID;

/**
 * Represents the lookup of an scratch app's UUID (from ScratchStorageAppRegistryEntry) to a requesting
 * user's privilege within that app's scratch space
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name="scratch_storage_app_user_privs")
public class ScratchStorageAppUserPriv {

    @Id
    @Getter
    @Setter
    private UUID id;

    @Getter
    @Setter
    @ManyToMany
    private ScratchStorageAppRegistryEntry app;

    @Getter
    @Setter
    private ScratchStorageUser user;

    @Getter
    @Setter
    @ManyToMany
    private Privilege privilege;

}
