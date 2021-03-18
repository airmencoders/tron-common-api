package mil.tron.commonapi.appgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.pro.packaged.O;
import lombok.extern.slf4j.Slf4j;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AppSourceConfig {

//    private final List<AppSourceInterfaceDefinition> appSourceDefs = Arrays.asList(
//            new AppSourceInterfaceDefinition("Puckboard",
//                    "puckboard.yml",
//                    "")
//    );

    private AppSourceInterfaceDefinition[] appSourceDefs;

    private AppSourceRepository appSourceRepository;

    @Autowired
    public AppSourceConfig(AppSourceRepository appSourceRepository,
                           @Value("${appsource.definition-file}") String appSourceDefFile) {
        this.appSourceRepository = appSourceRepository;
        this.appSourceDefs = this.parseAppSourceDefs(appSourceDefFile);
        this.registerAppSources(this.appSourceDefs);
        System.out.println(appSourceDefFile);
    }

    private AppSourceInterfaceDefinition[] parseAppSourceDefs(String configFile) {
        Resource appSourceDefResource = new ClassPathResource(configFile);
        ObjectMapper objectMapper = new ObjectMapper();
        AppSourceInterfaceDefinition[] defs = null;
        try {
             defs = objectMapper.readValue(new File(appSourceDefResource.getURI()),
                    AppSourceInterfaceDefinition[].class);
        }
        catch (IOException e) {
            log.warn("Could not parse app source file config.");
        }
        return defs;
    }

    public AppSourceInterfaceDefinition[] getAppSourceDefs() {
        return this.appSourceDefs;
    }

    private void registerAppSources(AppSourceInterfaceDefinition[] appSourceDefs) {
        // attempt adding
        for (AppSourceInterfaceDefinition appDef : appSourceDefs) {
            this.registerAppSource(appDef);
        }
    }

    @Transactional
    void registerAppSource(AppSourceInterfaceDefinition appDef) {
        if (!this.appSourceRepository.existsByNameIgnoreCase(appDef.getName())) {
            // add new app source
            AppSource newAppSource = AppSource.builder()
                    .name(appDef.getName())
                    .openApiSpecFilename(appDef.getOpenApiSpecFilename())
                    .build();
            this.appSourceRepository.save(newAppSource);
        }
    }

}
