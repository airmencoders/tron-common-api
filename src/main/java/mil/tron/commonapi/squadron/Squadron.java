package mil.tron.commonapi.squadron;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import mil.tron.commonapi.organization.Organization;
import mil.tron.commonapi.person.Person;

import javax.persistence.Entity;
import javax.persistence.OneToOne;

@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
public class Squadron extends Organization {

    @Getter
    @Setter
    @OneToOne
    private Person operationsDirector;

    @Getter
    @Setter
    @OneToOne
    private Person chief;

    @Getter
    @Setter
    private String baseName;

    @Getter
    @Setter
    private String majorCommand;
}
