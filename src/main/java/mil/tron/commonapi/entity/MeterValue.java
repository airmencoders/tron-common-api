package mil.tron.commonapi.entity;

import java.util.Date;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotBlank;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppSource;

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
