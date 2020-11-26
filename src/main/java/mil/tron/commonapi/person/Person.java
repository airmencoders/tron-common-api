package mil.tron.commonapi.person;

import lombok.*;

import javax.persistence.Entity;
import javax.persistence.*;
import javax.validation.constraints.Email;

import org.hibernate.annotations.GenericGenerator;

import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
public class Person {

    @Id
    @Getter
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @GeneratedValue(generator = "UUID")
    private UUID id;

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
