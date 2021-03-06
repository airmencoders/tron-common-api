package mil.tron.commonapi.entity.scratch;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * This is the table/entries representation of the scratch storage area of key/value pairs
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name="scratch_storage")
public class ScratchStorageEntry {

    @Id
    @JsonIgnore
    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    @NotNull
    private UUID appId;

    @Getter
    @Setter
    @NotNull
    @NotBlank
    private String key;

    @Getter
    @Setter
    @Column(length = 2097152)
    private String value;
}
