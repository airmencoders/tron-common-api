package mil.tron.commonapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties()
@Validated
public class ApplicationProperties {
    
    @Getter
    @Setter
    private List<String> combinedPrefixes = new ArrayList<>();

    @Getter
    @Setter
    @NotEmpty
    private Map<String, @NotEmpty String> apiPrefix;

    @Getter
    @Setter
    @NotEmpty
    private String appSourcesPrefix;
    
    @PostConstruct
    private void createCombinedPrefixes() {
        if(apiPrefix != null && !apiPrefix.isEmpty())  {
            String appSourcePath = appSourcesPrefix == null ? "" : appSourcesPrefix;
            this.combinedPrefixes.addAll(apiPrefix.values().stream().map(prefix -> {
                if(prefix != null) {
                    return prefix.concat(appSourcePath);
                }
                return appSourcePath;
            }).collect(Collectors.toList()));
        }
    }
}
