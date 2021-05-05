package mil.tron.commonapi.dto.appclient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.UUID;

/**
 * A DTO to show just the info we need for a given App Client's Endpoints
 * Prevents recursive serialization that would otherwise occur if we used a
 * fill AppEndpointPriv
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppEndpointClientInfoDto {

    @Getter
    @Setter
    private UUID id;

    @Getter
    @Setter
    private String appSourceName;

    @Getter
    @Setter
    String path;
    
    @Getter
    @Setter
    String requestPath;

    @Getter
    @Setter
    RequestMethod method;

    @Getter
    @Setter
    boolean deleted;

}
