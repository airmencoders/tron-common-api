package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.Date;
import java.util.UUID;

/**
 * DTO for holding http log info
 */

@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HttpLogEntryDto {

    @Getter
    @Setter
    private UUID id;

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

}
