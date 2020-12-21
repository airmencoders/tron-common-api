package mil.tron.commonapi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import mil.tron.commonapi.exception.InvalidFieldValueException;

import javax.persistence.Entity;
import javax.persistence.Transient;
import java.util.*;

@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
public class Airman extends Person {

    /**
     * List of valid USAF ranks plus contractor (CTR) and civilian (CIV)
     */
    @Transient
    private static final HashSet<String> ranks = new HashSet<>(Arrays.asList(
            "AB", "AMN", "A1C", "SRA", "SSGT", "TSGT", "MSGT", "SMGT", "CMSGT", "CMSGT",
            "CCMSGT", "CMSAF", "2LT", "1LT", "CAPT", "MAJ", "LTCOL", "COL", "BG", "MG",
            "LTG", "GEN", "CIV", "CTR", "SES"));

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

    /**
     * Integrated Maintenance Data System id
     */
    @Getter
    @Setter
    private String imds;

    /**
     * Rank field that returns/sets the base class (Person) 'title' field
     */
    public String getRank() {
        return this.getTitle();
    }

    public void setRank(String rank) {
        if (ranks.contains(rank.toUpperCase())) {
            this.setTitle(rank.toUpperCase());
        }
        else {
            throw new InvalidFieldValueException("Airman rank must be one of: " + String.join(", ", Airman.ranks));
        }
    }

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

    @Getter
    @Setter
    private String phone;

    @Getter
    @Setter
    private String address;

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

    @Getter
    @Setter
    private String dutyPhone;

    /**
     * Job title performed as an airman
     */
    @Getter
    @Setter
    private String dutyTitle;
}
