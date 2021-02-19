package mil.tron.commonapi.entity.scratch;

import lombok.*;
import mil.tron.commonapi.entity.Privilege;

import javax.persistence.*;
import java.util.UUID;

/**
 * Represents the lookup of an scratch app's UUID (from ScratchStorageAppRegistryEntry) to a requesting
 * user's privilege within that app's scratch space
 */
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@Builder
@Entity
@Table(name="scratch_storage_app_user_privs")
public class ScratchStorageAppUserPriv {

    @Id
    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    @OneToOne
    private ScratchStorageUser user;

    @Getter
    @Setter
    @OneToOne
    private Privilege privilege;

}
