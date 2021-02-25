package mil.tron.commonapi.dto.persons;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import mil.tron.commonapi.dto.PersonDto;

import java.util.Date;

public class Airman extends PersonDto {
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
     * Integrated Maintenance Data System id
     */
    @Getter
    @Setter
    private String imds;

    @Getter
    @Setter
    private String unit;

    /**
     * Service member's owning Wing
     */
    @Getter
    @Setter
    private String wing;

    /**
     * Service member's owning Group
     */
    @Getter
    @Setter
    private String gp;

    /**
     * Service member's owning squadron
     */
    @Getter
    @Setter
    private String squadron;

    /**
     * Work Center (Office Symbol)
     */
    @Getter
    @Setter
    private String wc;

    /**
     * ID in the GO81 training requirements system
     */
    @Getter
    @Setter
    private String go81;

    /**
     * Date current rank was obtained (date of rank)
     */
    @Getter
    @Setter
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "UTC")
    private Date dor;

    /**
     * Date estimated return from overseas (DEROS)
     */
    @Getter
    @Setter
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "UTC")
    private String deros;

    /**
     * General purpose flag used by Tempest
     */
    @Getter
    @Setter
    private boolean admin;

    @Getter
    @Setter
    private String fltChief;

    @Getter
    @Setter
    private boolean approved;

    @Getter
    @Setter
    private String manNumber;

}
