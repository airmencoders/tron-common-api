package mil.tron.commonapi.entity.scratch;

import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
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

    @Id
    @Getter
    @Setter
    private UUID id;

    @Getter
    @Setter
    @NotBlank
    @NotNull
    private String appName;

}
