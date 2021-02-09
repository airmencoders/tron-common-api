package mil.tron.commonapi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.pubsub.listeners.PersonEntityListener;
import mil.tron.commonapi.validations.ValidDodId;
import mil.tron.commonapi.validations.ValidPhoneNumber;

import javax.persistence.*;
import javax.validation.constraints.Email;
import java.util.Date;
import java.util.UUID;

@EntityListeners(PersonEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@JsonIgnoreProperties(ignoreUnknown = true)
@Table(uniqueConstraints = { @UniqueConstraint(columnNames = "emailAsLower") })
public class Person {

    @Id
    @Getter
    @Setter
    @Builder.Default
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    private String firstName;
    
    @Getter
    @Setter
    private String middleName;
    
    @Getter
    @Setter
    private String lastName;
    
    /**
    * The title of a person, as in how they should be addressed.
    * Examples: Mr., Ms., Dr., SSgt, PFC, PO2, LCpl
    */
    @Getter
    @Setter
    private String title;

    @Email(message="Malformed email address")
    @Getter
    @Setter
    private String email;


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
     * Service member's rank
     */
    @Getter
    @Setter
    @ManyToOne
    private Rank rank;

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
     * Converted value of {@link Person#email} to lowercase. 
     * This is used for a unique constraint in the database for emails.
     */
    @JsonIgnore
    private String emailAsLower;
    

    
    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        else if (other instanceof Person) {
            Person otherPerson = (Person) other;
            return this.id == otherPerson.getId();
        }
        else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }
    
    @JsonIgnore
    public String getFullName() {
        return String.format("%s %s", firstName, lastName);
    }

    /**
     * This method will be performed before database operations.
     *
     * Entity parameters are formatted as needed
     */
    @PreUpdate
    @PrePersist
    public void sanitizeEntity() {
        trimStrings();
        sanitizeEmailForUniqueConstraint();
    }

    /**
     * This method ensures that blank emails are set to null and trims all strings.
     *
     * It will set {@link Person#email} to null if an empty string
     * or a string of one or more whitespaces is provided. It will then
     * set {@link Person#emailAsLower} to a lowercase variant of
     * {@link Person#email} if it exists, else null.
     *
     * This method is needed to provide the unique constraint on
     * emails because this field may be optional and blank strings
     * will be considered to be null emails when saved to the database.
     */
    private void sanitizeEmailForUniqueConstraint() {
        if (email != null && email.isBlank()) {
            this.email = null;
        }

        emailAsLower = email == null ? null : email.toLowerCase();
    }

    private void trimStrings() {
        firstName = (firstName == null) ? null : firstName.trim();
        middleName = (middleName == null) ? null : middleName.trim();
        lastName = (lastName == null) ? null : lastName.trim();
        title = (title == null) ? null : title.trim();
        email = (email == null) ? null : email.trim();
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
