package mil.tron.commonapi.person;

import lombok.*;

import javax.persistence.Entity;
import javax.persistence.*;
import javax.validation.constraints.Email;

import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Person {

    @Id
    @Getter
    @Builder.Default
    private UUID id = UUID.randomUUID();

    public boolean equals(Person other) {
        return this.id == other.getId();
    }

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

    @Email
    @Getter
    @Setter
    private String email;

    public String getFullName() {
        return String.format("%s %s", firstName, lastName);
    }
    
}
