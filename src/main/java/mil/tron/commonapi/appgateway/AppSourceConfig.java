package mil.tron.commonapi.appgateway;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import javax.transaction.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;

@Service
@Slf4j
public class AppSourceConfig {

    private Map<AppSourceInterfaceDefinition, AppSource> appSourceDefs;

    private AppSourceRepository appSourceRepository;

    @Autowired
    public AppSourceConfig(AppSourceRepository appSourceRepository,
                           @Value("${appsource.definition-file}") String appSourceDefFile) {
        this.appSourceRepository = appSourceRepository;
        this.appSourceDefs = new HashMap<>();
        this.registerAppSources(this.parseAppSourceDefs(appSourceDefFile));
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

    public Map<AppSourceInterfaceDefinition, AppSource> getAppSourceDefs() {
        return this.appSourceDefs;
    }

    private void registerAppSources(AppSourceInterfaceDefinition[] appSourceDefs) {
        // attempt adding
        if (appSourceDefs != null) {
            for (AppSourceInterfaceDefinition appDef : appSourceDefs) {
                this.appSourceDefs.put(appDef, this.registerAppSource(appDef));
            }
        }
    }

    @Transactional
    AppSource registerAppSource(AppSourceInterfaceDefinition appDef) {
        if (!this.appSourceRepository.existsByNameIgnoreCase(appDef.getName())) {
            // add new app source
            AppSource newAppSource = AppSource.builder()
                    .name(appDef.getName())
                    .openApiSpecFilename(appDef.getOpenApiSpecFilename())
                    .appSourcePath(appDef.getAppSourcePath())
                    .build();
            try {
                return this.appSourceRepository.save(newAppSource);                
            }
            catch (Exception e) {
                log.warn(String.format("Unable to add app source %s.", appDef.getName()), e);
            }
        }
        return this.appSourceRepository.findByNameIgnoreCaseWithEndpoints(appDef.getName());
    }

}
