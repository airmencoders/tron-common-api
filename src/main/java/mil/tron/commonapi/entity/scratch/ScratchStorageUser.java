package mil.tron.commonapi.entity.scratch;


import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.Email;
import java.util.UUID;

/** Represents a user/consumer of the scratch space -- they will have a privilege associated with them
 *
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name="scratch_storage_user")
public class ScratchStorageUser {

    @Id
    @Getter
    @Setter
    private UUID id;

    @Getter
    @Setter
    @Email(message = "Malformed email address")
    private String email;


}
