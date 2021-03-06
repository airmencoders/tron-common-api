package mil.tron.commonapi.appgateway;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class AppSourceInterfaceDefinition {
    String name;
    String openApiSpecFilename;
    String sourceUrl;
    String appSourcePath;
}
