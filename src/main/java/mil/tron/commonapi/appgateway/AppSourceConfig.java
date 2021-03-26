package mil.tron.commonapi.appgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.*;

@Service
@Slf4j
public class AppSourceConfig {

    private AppSourceInterfaceDefinition[] appSourceDefs;

    private AppSourceRepository appSourceRepository;

    @Autowired
    public AppSourceConfig(AppSourceRepository appSourceRepository,
                           @Value("${appsource.definition-file}") String appSourceDefFile) {
        this.appSourceRepository = appSourceRepository;
        this.appSourceDefs = this.parseAppSourceDefs(appSourceDefFile);
        this.registerAppSources(this.appSourceDefs);
    }

    private AppSourceInterfaceDefinition[] parseAppSourceDefs(String configFile) {
        ObjectMapper objectMapper = new ObjectMapper();
        AppSourceInterfaceDefinition[] defs = null;
        try {
            InputStream in = getClass().getResourceAsStream(configFile);
            if (in == null) {
                throw new NullPointerException(String.format("Unable to find config for %s",
                        configFile));
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            defs = objectMapper.readValue(reader, AppSourceInterfaceDefinition[].class);
            in.close();
            reader.close();
        }
        catch (IOException e) {
            log.warn("Could not parse app source file config.");
        }
        catch (NullPointerException e) {
            log.warn("Could not find resource file.", e);
        }
        return defs;
    }

    public AppSourceInterfaceDefinition[] getAppSourceDefs() {
        return this.appSourceDefs;
    }

    private void registerAppSources(AppSourceInterfaceDefinition[] appSourceDefs) {
        // attempt adding
        if (appSourceDefs != null) {
            for (AppSourceInterfaceDefinition appDef : appSourceDefs) {
                this.registerAppSource(appDef);
            }
        }
    }

    @Transactional
    void registerAppSource(AppSourceInterfaceDefinition appDef) {
        if (!this.appSourceRepository.existsByNameIgnoreCase(appDef.getName())) {
            // add new app source
            AppSource newAppSource = AppSource.builder()
                    .name(appDef.getName())
                    .openApiSpecFilename(appDef.getOpenApiSpecFilename())
                    .appSourcePath(appDef.getAppSourcePath())
                    .build();
            try {
                this.appSourceRepository.save(newAppSource);
            }
            catch (Exception e) {
                log.warn(String.format("Unable to add app source %s.", appDef.getName()), e);
            }
        }
    }

}
