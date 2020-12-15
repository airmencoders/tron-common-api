package mil.tron.commonapi.airman;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import mil.tron.commonapi.person.Person;

import javax.persistence.Entity;
import java.util.Date;

@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
public class Airman extends Person {

    /**
     * An airman's Air Force Specialty Code.
     * e.g. "17D" is cyber warfare officer.
     */
    @Getter
    @Setter
    private String afsc;

    /**
     * An airman's Expiration of Term of Service.
     * e.g. When their enlistment expires - N/A for officers
     */
    @Getter
    @Setter
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "UTC")
    private Date etsDate;

    /**
     * An airman's date of most recent physical fitness evalulation.
     */
    @Getter
    @Setter
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "UTC")
    private Date ptDate;

    /**
     * An 10-digit airman's DOD Identification number.
     */
    @Getter
    @Setter
    private String dodid;
    //
    // Putting DODID as a string since using a Long would require manually padding
    //  value in string output if the dodid had leading zeros, this was it stays literal
}
