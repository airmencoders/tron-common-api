package mil.tron.commonapi.entity;

import lombok.*;

import javax.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@IdClass(OrganizationMetadata.OrganizationMetadataPK.class)
@EqualsAndHashCode
public class OrganizationMetadata {
    @Id
    @Getter
    @Setter
    private UUID organizationId;

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
    public static class OrganizationMetadataPK implements Serializable {
        protected UUID organizationId;
        protected String key;
    }
}
