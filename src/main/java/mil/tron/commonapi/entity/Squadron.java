package mil.tron.commonapi.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.OneToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;

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

    /**
     * This method will be performed before database operations.
     *
     * Trims all necessary string member variables
     */
    @PreUpdate
    @PrePersist
    public void trimStrings() {
        baseName = (baseName == null) ? null : baseName.trim();
        majorCommand = (majorCommand == null) ? null : majorCommand.trim();
    }
}
