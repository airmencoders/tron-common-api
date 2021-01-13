package mil.tron.commonapi.entity;

import com.fasterxml.jackson.annotation.*;
import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.Email;

import java.util.UUID;

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
     * Converted value of {@link Person#email} to lowercase. 
     * This is used for a unique constraint in the database for emails.
     */
    @JsonIgnore
    private String emailAsLower;
    
    /**
     * This method will be performed before database operations.
     * 
     * It ensures that blank emails are set to null.
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
    @PreUpdate
    @PrePersist
    public void sanitizeEmailForUniqueConstraint() {
    	if (email != null && email.isBlank()) {
    		this.email = null;
    	}
    	
    	emailAsLower = email == null ? null : email.toLowerCase();
    }
    
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
    
}
