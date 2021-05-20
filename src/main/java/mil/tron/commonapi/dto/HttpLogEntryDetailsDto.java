package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

/**
 * DTO for holding http log info + its request body and response body
 */

@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HttpLogEntryDetailsDto {

    @Getter
    @Setter
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "UTC")
    private Date requestTimestamp;

    @Getter
    @Setter
    private UUID id;

    @Getter
    @Setter
    private String requestedUrl;

    @Getter
    @Setter
    private String requestBody;

    @Getter
    @Setter
    private String remoteIp;

    @Getter
    @Setter
    private String requestMethod;

    @Getter
    @Setter
    private String responseBody;

    @Getter
    @Setter
    private String requestHost;

    @Getter
    @Setter
    private String userAgent;

    @Getter
    @Setter
    private String queryString;

    @Getter
    @Setter
    private Long timeTakenMs;

    @Getter
    @Setter
    private String userName;

    @Getter
    @Setter
    private int statusCode;

}
