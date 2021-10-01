package mil.tron.commonapi.appgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class AppSourceConfig {

    private Map<AppSourceInterfaceDefinition, AppSource> appSourceDefs;
    private Map<String, AppSourceInterfaceDefinition> appSourcePathToDefinitionMap;
    
    private AppSourceRepository appSourceRepository;

    @Autowired
    public AppSourceConfig(AppSourceRepository appSourceRepository,
                           @Value("${appsource.definition-file}") String appSourceDefFile) {
        this.appSourceRepository = appSourceRepository;
        this.appSourceDefs = new HashMap<>();
        this.appSourcePathToDefinitionMap = new HashMap<>();
        this.registerAppSources(this.parseAppSourceDefs(appSourceDefFile));
    }

    private AppSourceInterfaceDefinition[] parseAppSourceDefs(String configFile) {
        ObjectMapper objectMapper = new ObjectMapper();
        AppSourceInterfaceDefinition[] defs = null;
        try (
            InputStream in = getClass().getResourceAsStream(configFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        ) {

            defs = objectMapper.readValue(reader, AppSourceInterfaceDefinition[].class);
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
        AppSource appSource;
        if (!this.appSourceRepository.existsByNameIgnoreCase(appDef.getName())
                && !this.appSourceRepository.existsByAppSourcePath(appDef.getAppSourcePath())) {
            // add new app source
            appSource = AppSource.builder()
                    .name(appDef.getName())
                    .openApiSpecFilename(appDef.getOpenApiSpecFilename())
                    .appSourcePath(appDef.getAppSourcePath())
                    .availableAsAppSource(true)
                    .nameAsLower(appDef.getName().toLowerCase())
                    .build();
        }
        else if (!this.appSourceRepository.existsByNameIgnoreCase(appDef.getName())
                && this.appSourceRepository.existsByAppSourcePath(appDef.getAppSourcePath())) {

            // weird case where incoming appSourceConfig.json has a different name but same app source pathname
            //  this would happen if appSource's name was changed in the appSourceConfig.json file and promoted up
            //  to deployment - it would cause a 500 db error since we would otherwise attempt to add this seemingly
            //  new appsource and it would croak since appSourcePath have to be unique.  in this case we want to sync the
            //  old app source name to the new incoming one (keying off of the app source path column in the db)

            appSource = this.appSourceRepository.findByAppSourcePath(appDef.getAppSourcePath());

            // sync existing according to app source pathname
            appSource.setName(appDef.getName());
            appSource.setAvailableAsAppSource(true);
            appSource.setOpenApiSpecFilename(appDef.getOpenApiSpecFilename());
        }
        else {
            appSource = this.appSourceRepository.findByNameIgnoreCaseWithEndpoints(appDef.getName());
            // refresh the database to always be correct
            appSource.setAvailableAsAppSource(true);
            appSource.setOpenApiSpecFilename(appDef.getOpenApiSpecFilename());
            appSource.setAppSourcePath(appDef.getAppSourcePath());
        }
        try {
            this.appSourceRepository.save(appSource);                
        }
        catch (Exception e) {
            log.warn(String.format("Unable to add app source %s.", appDef.getName()), e);
        }
        return this.appSourceRepository.findByNameIgnoreCaseWithEndpoints(appDef.getName());
    }
    
    /**
     * Adds a source def mapping to the map
     * @param appSourcePath
     * @param appDef
     * @return True if the app def is added. False if the app source path is not added and was already defined.
     */
    public boolean addAppSourcePathToDefMapping(String appSourcePath, AppSourceInterfaceDefinition appDef) {
        if (this.appSourcePathToDefinitionMap.get(appSourcePath) == null) {
            this.appSourcePathToDefinitionMap.put(appSourcePath, appDef);
            return true;
        }
        return false;
    }

    public void clearAppSourceDefs() {
        this.appSourcePathToDefinitionMap.clear();
    }
    
    public Map<String, AppSourceInterfaceDefinition> getPathToDefinitionMap() {
    	return this.appSourcePathToDefinitionMap;
    }


}
