package mil.tron.commonapi.entity;

import lombok.*;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppSource;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import java.util.Date;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MeterValue {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @NonNull
    @NotBlank
    private String metricName;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "timestamp")
    @NonNull
    private Date timestamp;

    @NonNull
    private Double value;

    @ManyToOne(cascade = CascadeType.ALL)
    private AppEndpoint appEndpoint;

    @ManyToOne(cascade = CascadeType.ALL)
    private AppSource appSource;

    @ManyToOne(cascade = CascadeType.ALL)
    private AppClientUser appClientUser;
}
