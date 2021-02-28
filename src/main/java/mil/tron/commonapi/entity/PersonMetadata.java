package mil.tron.commonapi.entity;

import lombok.*;

import javax.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@IdClass(PersonMetadata.PersonMetadataPK.class)
public class PersonMetadata {
    @Id
    @Getter
    @Setter
    private UUID personId;

    @Id
    @Getter
    @Setter
    private String key;

    @Getter
    @Setter
    private String value;

    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class PersonMetadataPK implements Serializable {
        protected UUID personId;
        protected String key;
    }
}
