package mil.tron.commonapi.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import mil.tron.commonapi.annotation.efa.ProtectedField;
import mil.tron.commonapi.annotation.security.PiiField;
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
@Table(uniqueConstraints = { @UniqueConstraint(columnNames = "emailAsLower"), @UniqueConstraint(columnNames = "dodid") })
public class Person {
	@JsonIgnore
	public static final String ID_FIELD = "id";
	@JsonIgnore
	public static final String RANK_FIELD = "rank";
	@JsonIgnore
	public static final String EMAIL_FIELD = "email";
	@JsonIgnore
	public static final String DODID_FIELD = "dodid";
	@JsonIgnore
	public static final String ORG_MEMBERSHIPS_FIELD = "organizationMemberships";
	@JsonIgnore
	public static final String ORG_LEADERSHIPS_FIELD = "organizationLeaderships";

    @Id
    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    @ProtectedField
    private String firstName;
    
    @Getter
    @Setter
    @ProtectedField
    private String middleName;
    
    @Getter
    @Setter
    @ProtectedField
    private String lastName;

    @Getter
    @Setter
    @ProtectedField
    private String title;

    @Getter
    @Setter
    @ProtectedField
    private String dutyTitle;

    @PiiField
    @Getter
    @Setter
    @ProtectedField
    private String email;

    /**
     * An 10-digit airman's DOD Identification number.
     * Putting DODID as a string since using a Long would require manually padding
     * value in string output if the dodid had leading zeros, this was it stays literal
     */
    @PiiField
    @Getter
    @Setter
    @ValidDodId
    @ProtectedField
    private String dodid;

    @PiiField
    @Getter
    @Setter
    @ValidPhoneNumber
    @ProtectedField
    private String phone;

    @Getter
    @Setter
    @ValidPhoneNumber
    @ProtectedField
    private String dutyPhone;

    @PiiField
    @Getter
    @Setter
    @ProtectedField
    private String address;

    @Getter
    @Setter
    @ManyToOne
    @ProtectedField
    private Rank rank;

    @Getter
    @Setter
    @ManyToOne
    @ProtectedField
    private Organization primaryOrganization;

    @Getter
    @Builder.Default
    @ManyToMany
    @ProtectedField
    @JoinTable(name = "organization_members", 
        joinColumns = @JoinColumn(name = "members_id"),
        inverseJoinColumns = @JoinColumn(name = "organization_id"))
    private Set<Organization> organizationMemberships = new HashSet<>();

    @Getter
    @Builder.Default
    @ProtectedField
    @OneToMany(mappedBy = "leader")
    private Set<Organization> organizationLeaderships = new HashSet<>();

    @Getter
    @Builder.Default
    @ProtectedField
    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name="personId", updatable = false, insertable = false)
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
            return this.id.equals(otherPerson.getId());
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
