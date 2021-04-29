package mil.tron.commonapi.dto.appsource;

import java.util.UUID;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AppEndpointDto extends EndpointDto {
    @Getter
    @Setter
    private boolean deleted;

    @Builder
    public AppEndpointDto(UUID id, String path, String requestType, boolean deleted) {
        super(id, path, requestType);
        this.deleted = deleted;
    }
}
