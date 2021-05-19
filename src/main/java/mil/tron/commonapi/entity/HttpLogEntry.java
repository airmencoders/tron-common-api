package mil.tron.commonapi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;
import java.util.UUID;

/**
 * Log Entry entity that holds an entry of data containing information about an atomic request and its response
 */
@Entity
@EqualsAndHashCode
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "http_logs")
public class HttpLogEntry {

    @Id
    @Getter
    @Setter
    @Builder.Default
    @JsonIgnore
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "UTC")
    private Date requestTimestamp;

    @Getter
    @Setter
    private String requestedUrl;

    @Getter
    @Setter
    private String remoteIp;

    @Getter
    @Setter
    private String requestMethod;

    @Getter
    @Setter
    private String requestHost;

    @Getter
    @Setter
    private String queryString;

    @Getter
    @Setter
    private int statusCode;

    @Getter
    @Setter
    private String userAgent;

    @Getter
    @Setter
    private Long timeTakenMs;

    @Getter
    @Setter
    private String userName;

    @Getter
    @Setter
    private String requestBody;

    @Getter
    @Setter
    private String responseBody;
}
