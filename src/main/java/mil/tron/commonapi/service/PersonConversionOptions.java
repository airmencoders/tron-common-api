package mil.tron.commonapi.service;

import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PersonConversionOptions {
    @Getter
    @Setter
    @Builder.Default
    private boolean membershipsIncluded = false;

    @Getter
    @Setter
    @Builder.Default
    private boolean leadershipsIncluded = false;

    @Getter
    @Setter
    @Builder.Default
    private boolean metadataIncluded = true;
}
