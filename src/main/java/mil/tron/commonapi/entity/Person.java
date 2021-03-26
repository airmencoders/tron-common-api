package mil.tron.commonapi.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.validations.ValidDodId;
import mil.tron.commonapi.validations.ValidPhoneNumber;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Table(uniqueConstraints = { @UniqueConstraint(columnNames = "emailAsLower") })
public class Person {

    @Id
    @Getter
    @Setter
    @Builder.Default
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

    @Getter
    @Setter
    private String title;

    @Getter
    @Setter
    private String dutyTitle;

    @Getter
    @Setter
    private String email;

    /**
     * An 10-digit airman's DOD Identification number.
     * Putting DODID as a string since using a Long would require manually padding
     * value in string output if the dodid had leading zeros, this was it stays literal
     */
    @Getter
    @Setter
    @ValidDodId
    private String dodid;

    @Getter
    @Setter
    @ValidPhoneNumber
    private String phone;

    @Getter
    @Setter
    @ValidPhoneNumber
    private String dutyPhone;

    @Getter
    @Setter
    private String address;

    @Getter
    @Setter
    @ManyToOne
    private Rank rank;

    @Getter
    @Builder.Default
    @ManyToMany
    @JoinTable(name = "organization_members", 
        joinColumns = @JoinColumn(name = "members_id"),
        inverseJoinColumns = @JoinColumn(name = "organization_id"))
    private Set<Organization> organizationMemberships = new HashSet<>();

    @Getter
    @Builder.Default
    @OneToMany(mappedBy = "leader")
    private Set<Organization> organizationLeaderships = new HashSet<>();

    @Getter
    @Builder.Default
    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name="personId")
    private Set<PersonMetadata> metadata = new HashSet<>();

    /**
     * Converted value of {@link Person#email} to lowercase.
     * This is used for a unique constraint in the database for emails.
     */
    private String emailAsLower; //NOSONAR ignore as this is used to generate a column for uniqueness


    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_created")
    private Date dateCreated;

    @UpdateTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_modified")
    private Date dateModified;

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
        firstName = trim(firstName);
        middleName = trim(middleName);
        lastName = trim(lastName);
        title = trim(title);
        email = trim(email);
        dodid = trim(dodid);
        phone = trim(phone);
        address = trim(address);
        dutyTitle = trim(dutyTitle);
    }

    private final String trim(String value) {
        return value == null ? null : value.trim();
    }
}
