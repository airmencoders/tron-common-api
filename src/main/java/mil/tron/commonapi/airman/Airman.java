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

    @Getter
    @Setter
    private String afsc;

    @Getter
    @Setter
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "UTC")
    private Date etsDate;

    @Getter
    @Setter
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "UTC")
    private Date ptDate;

    @Getter
    @Setter
    // Putting as a string since using a Long would require manually padding
    //  value in string output if the dodid had leading zeros, this was it stays literal
    private String dodid;
}
