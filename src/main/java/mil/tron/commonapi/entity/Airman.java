package mil.tron.commonapi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import mil.tron.commonapi.entity.ranks.AirmanRank;
import mil.tron.commonapi.exception.InvalidFieldValueException;
import mil.tron.commonapi.validations.ValidDodId;
import mil.tron.commonapi.validations.ValidPhoneNumber;

import javax.persistence.Entity;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;

import java.util.Date;

@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
public class Airman extends Person {

    private AirmanRank rank;

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
    @ValidDodId
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
     * Rank of Airman service member
     */
    @JsonGetter("rank")
    public String getRank() {
        if (this.rank == null) {
            return null;
        }
        return this.rank.toString();
    }

    @JsonSetter("rank")
    public void setRank(String rank) {
        if (rank == null) {
            this.rank = AirmanRank.UNKNOWN;
        }
        else {
            val rankEnum = AirmanRank.valueByString(rank.toUpperCase());
            if (rankEnum == null) {
                throw new InvalidFieldValueException("Airman rank must be one of: " +
                        AirmanRank.getValueString());
            }
            this.rank = rankEnum;
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
    @ValidPhoneNumber
    private String dutyPhone;

    /**
     * Job title performed as an airman
     */
    @Getter
    @Setter
    private String dutyTitle;

    /**
     * This method will be performed before database operations.
     *
     * Entity parameters are formatted as needed
     */
    @PreUpdate
    @PrePersist
    public void sanitizeEntity() {
        trimStrings();
    }

    public void trimStrings() {
        afsc = (afsc == null) ? null : afsc.trim();
        dodid = (dodid == null) ? null : dodid.trim();
        imds = (imds == null) ? null : imds.trim();
        unit = (unit == null) ? null : unit.trim();
        wing = (wing == null) ? null : wing.trim();
        gp = (gp == null) ? null : gp.trim();
        squadron = (squadron == null) ? null : squadron.trim();
        wc = (wc == null) ? null : wc.trim();
        go81 = (go81 == null) ? null : go81.trim();
        deros = (deros == null) ? null : deros.trim();
        phone = (phone == null) ? null : phone.trim();
        address = (address == null) ? null : address.trim();
        fltChief = (fltChief == null) ? null : fltChief.trim();
        manNumber = (manNumber == null) ? null : manNumber.trim();
        dutyTitle = (dutyTitle == null) ? null : dutyTitle.trim();
    }
}
