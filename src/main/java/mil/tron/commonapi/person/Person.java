package mil.tron.commonapi.person;

import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.Email;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Person {

    @Id
    @Getter
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
    
    @Email
    @Getter
    @Setter
    private String email;
    
    public boolean equals(Person other) {
        if (other == null) {
            return false;
        }
        else {
            return this.id == other.getId();
        }
    }
    public String getFullName() {
        return String.format("%s %s", firstName, lastName);
    }
    
}
