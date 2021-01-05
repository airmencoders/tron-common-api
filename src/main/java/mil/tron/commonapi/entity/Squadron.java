package mil.tron.commonapi.entity;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;

@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
public class Squadron extends Organization {

    /**
     * Only serialize by ID all the time for this field and use custom deserializer to allowed setting via UUID
     */
    @Getter
    @Setter
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    @JsonIdentityReference(alwaysAsId = true)
    @JsonDeserialize(using = PersonDeserializer.class)
    @OneToOne
    private Person operationsDirector;

    /**
     * Only serialize by ID all the time for this field and use custom deserializer to allowed setting via UUID
     */
    @Getter
    @Setter
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    @JsonIdentityReference(alwaysAsId = true)
    @JsonDeserialize(using = PersonDeserializer.class)
    @OneToOne
    private Person chief;

    @Getter
    @Setter
    private String baseName;

    @Getter
    @Setter
    private String majorCommand;
}
