package mil.tron.commonapi.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import java.io.Serializable;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@IdClass(PersonMetadata.PersonMetadataPK.class)
@JsonIgnoreProperties(ignoreUnknown = true)
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
