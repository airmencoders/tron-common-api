package mil.tron.commonapi.dto.documentspace.mobile;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class S3MobilePaginationDto {
    @Getter
    @Setter
    @NotNull
    List<DocumentMobileDto> documents;

    @Getter
    @Setter
    String currentContinuationToken;

    @Getter
    @Setter
    String nextContinuationToken;

    @Getter
    @Setter
    @NotNull
    @Schema(description = "The size of the page")
    int size;

    @Getter
    @Setter
    @NotNull
    @Schema(description = "The size of the returned elements of this page")
    int totalElements;
}