package mil.tron.commonapi.appgateway;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.extern.slf4j.Slf4j;
import mil.tron.commonapi.ApplicationProperties;
import mil.tron.commonapi.controller.AppGatewayController;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.repository.appsource.AppEndpointRepository;
import mil.tron.commonapi.service.AppGatewayService;

@Service
@Slf4j
public class AppSourceEndpointsBuilder {

    private AppGatewayController queryController;

    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private AppSourceConfig appSourceConfig;

    private AppGatewayService appGatewayService;

    private AppEndpointRepository appEndpointRepository;

    private ApplicationProperties versionProperties;

    private AppGatewayRouteBuilder appGatewayRouteBuilder;
    
    @Autowired
    AppSourceEndpointsBuilder(RequestMappingHandlerMapping requestMappingHandlerMapping,
                              AppGatewayController queryController,
                              AppGatewayService appGatewayService,
                              AppSourceConfig appSourceConfig,
                              AppEndpointRepository appEndpointRepository,
                              ApplicationProperties versionProperties,
                              AppGatewayRouteBuilder appGatewayRouteBuilder

    ) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
        this.queryController = queryController;
        this.appSourceConfig = appSourceConfig;
        this.versionProperties = versionProperties;
        this.appGatewayService = appGatewayService;
        this.appEndpointRepository = appEndpointRepository;
        this.appGatewayRouteBuilder = appGatewayRouteBuilder;
        this.createAppSourceEndpoints(this.appSourceConfig);
    }

    private void createAppSourceEndpoints(AppSourceConfig appSourceConfig) {
        Map<AppSourceInterfaceDefinition, AppSource> appDefs = appSourceConfig.getAppSourceDefs();
        if (appDefs.keySet().size() == 0) {
            log.warn("No AppSource Definitions were found.");
            return;
        }
        for (AppSourceInterfaceDefinition appDef : appDefs.keySet()) {
            this.initializeWithAppSourceDef(appDef, appDefs.get(appDef));
        }
    }

    public void initializeWithAppSourceDef(AppSourceInterfaceDefinition appDef, AppSource appSource) {
        try {
            List<AppSourceEndpoint> appSourceEndpoints = this.parseAppSourceEndpoints(appDef.getOpenApiSpecFilename());
            boolean newMapping = this.appGatewayService.addSourceDefMapping(appDef.getAppSourcePath(),
                    appDef);
            
            // Register Camel routes for each individual App Source
            appGatewayRouteBuilder.createGatewayRoute(appDef.getAppSourcePath());
            
            if (newMapping) {
                for (AppSourceEndpoint appEndpoint: appSourceEndpoints) {
                    for(String prefix : this.versionProperties.getCombinedPrefixes()) {
                        this.addMapping(appDef.getAppSourcePath(), appEndpoint, prefix);
                    }
                    this.addEndpointToSource(appEndpoint, appSource);
                }
            }
            setUnusedFlagOnEndpointsNotInSpec(appSourceEndpoints, appSource);
        }
        catch (FileNotFoundException e) {
            log.warn(String.format("Endpoints for %s could not be loaded from %s. File not found.", appDef.getName(),
                    appDef.getOpenApiSpecFilename()), e);
        }
        catch (IOException e) {
            log.warn(String.format("Endpoints for %s could not be loaded from %s", appDef.getName(),
                    appDef.getOpenApiSpecFilename()), e);
        }
        catch (NoSuchMethodException e) {
            log.warn("Unable to map app source path to a controller handler.", e);
        }
    }

    private void setUnusedFlagOnEndpointsNotInSpec(List<AppSourceEndpoint> appSourceEndpoints, AppSource appSource) {
        List<AppEndpoint> unusedEndpoints = appSource.getAppEndpoints().parallelStream()
            .filter(item -> appSourceEndpoints.stream()
                    .noneMatch(appSourceEndpoint -> item.getPath().equals(appSourceEndpoint.getPath()) && item.getMethod().equals(appSourceEndpoint.getMethod())))
            .map(item -> {
                item.setDeleted(true);
                return item;
            })
            .collect(Collectors.toList());
        appEndpointRepository.saveAll(unusedEndpoints);
    }

    public List<AppSourceEndpoint> parseAppSourceEndpoints(String openApiFilename) throws IOException {
        Resource apiResource = new ClassPathResource("appsourceapis/" + openApiFilename);
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(apiResource.getURI().toString(),
                null, null);
        return result.getOpenAPI().getPaths().entrySet().stream()
                .map(path -> {
                    String pathString = path.getKey();
                    // get operations from paths.. .readOperations
                    return path.getValue().readOperationsMap()
                            // build pojo for path and operations
                            .keySet().stream().map(method -> new AppSourceEndpoint(pathString,
                                    AppSourceEndpointsBuilder.convertMethod(method)))
                            .collect(Collectors.toList());
                }).flatMap(List::stream).collect(Collectors.toList());
    }

    public static RequestMethod convertMethod(PathItem.HttpMethod swaggerHttpMethod) {
        RequestMethod converted;
        switch (swaggerHttpMethod) {
            case GET:
                converted = RequestMethod.GET;
                break;
            case POST:
                converted = RequestMethod.POST;
                break;
            case PUT:
                converted = RequestMethod.PUT;
                break;
            case DELETE:
                converted = RequestMethod.DELETE;
                break;
            case PATCH:
                converted = RequestMethod.PATCH;
                break;
            default:
                converted = RequestMethod.GET;
                break;
        }
        return converted;
    }

    private void addMapping(String appSourcePath, AppSourceEndpoint endpoint, String prefix) throws NoSuchMethodException {

        RequestMappingInfo requestMappingInfo = RequestMappingInfo
            .paths(String.format("%s/%s%s",
                prefix,
                appSourcePath,
                endpoint.getPath()))
            .methods(endpoint.getMethod())
            .produces(MediaType.APPLICATION_JSON_VALUE)
            .build();

        if (endpoint.getMethod().equals(RequestMethod.GET)) {
        	requestMappingHandlerMapping.registerMapping(requestMappingInfo, queryController,
        			AppGatewayController.class.getDeclaredMethod("handleCachedRequests", HttpServletRequest.class, HttpServletResponse.class, Map.class));
        } else {
            requestMappingHandlerMapping.registerMapping(requestMappingInfo, queryController,
                    AppGatewayController.class.getDeclaredMethod("handleRequests", HttpServletRequest.class, HttpServletResponse.class, Map.class));
        }
	      
    }

    private void addEndpointToSource(AppSourceEndpoint endpoint, AppSource appSource) {
        AppEndpoint appEndpoint = appEndpointRepository.findByAppSourceEqualsAndMethodEqualsAndPathEquals(appSource, endpoint.getMethod(), endpoint.getPath())
            .orElse(AppEndpoint.builder()
                .appSource(appSource)
                .method(endpoint.getMethod())
                .path(endpoint.getPath())
                .build());
        appEndpoint.setDeleted(false);
        try {
            this.appEndpointRepository.save(appEndpoint);
        } catch (Exception e) {
            log.warn(String.format("Unable to add endpoint to app source %s.", appSource.getName()), e);
        }
    }
}
