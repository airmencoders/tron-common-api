package mil.tron.commonapi.person;

import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.Email;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
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
    public String getFullName() {
        return String.format("%s %s", firstName, lastName);
    }
    
}
