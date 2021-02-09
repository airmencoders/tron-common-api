package mil.tron.commonapi.entity.ranks;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import mil.tron.commonapi.entity.branches.Branch;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
public class Rank {

    @Id
    @Getter
    @Setter
    @JsonIgnore
    private UUID id;

    @Getter
    @Setter
    private String abbreviation;

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private String payGrade;

    @Getter
    @Setter
    @Enumerated(value = EnumType.STRING)
    protected Branch branchType = Branch.OTHER;
}
