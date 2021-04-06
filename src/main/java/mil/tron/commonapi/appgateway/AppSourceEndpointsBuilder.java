package mil.tron.commonapi.appgateway;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import mil.tron.commonapi.repository.appsource.AppEndpointPrivRepository;
import org.assertj.core.util.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import mil.tron.commonapi.controller.AppGatewayController;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.repository.appsource.AppEndpointRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import mil.tron.commonapi.service.AppGatewayService;

@Service
@Slf4j
public class AppSourceEndpointsBuilder {

    private AppGatewayController queryController;

    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private AppSourceConfig appSourceConfig;

    private AppGatewayService appGatewayService;

    private AppEndpointRepository appEndpointRepository;

    private AppEndpointPrivRepository appEndpointPrivRepository;

    private String apiVersionPrefix;
    

    @Autowired
    AppSourceEndpointsBuilder(RequestMappingHandlerMapping requestMappingHandlerMapping,
                              AppGatewayController queryController,
                              AppGatewayService appGatewayService,
                              AppSourceConfig appSourceConfig,
                              AppSourceRepository appSourceRepository,
                              AppEndpointRepository appEndpointRepository,
                              AppEndpointPrivRepository appEndpointPrivRepository,
                              @Value("${api-prefix.v1}") String apiVersionPrefix
    ) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
        this.queryController = queryController;
        this.appSourceConfig = appSourceConfig;
        this.apiVersionPrefix = apiVersionPrefix;
        this.appGatewayService = appGatewayService;
        this.appEndpointRepository = appEndpointRepository;
        this.appEndpointPrivRepository = appEndpointPrivRepository;
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
            if (newMapping) {
                for (AppSourceEndpoint appEndpoint: appSourceEndpoints) {
                    this.addMapping(appDef.getAppSourcePath(), appEndpoint);
                    this.addEndpointToSource(appEndpoint, appSource);
                }
                deDuplicateRows(appSource);
            }
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

    private void addMapping(String appSourcePath, AppSourceEndpoint endpoint) throws NoSuchMethodException {

        RequestMappingInfo requestMappingInfo = RequestMappingInfo
                .paths(String.format("%s/app/%s%s",
                        this.apiVersionPrefix,
                        appSourcePath,
                        endpoint.getPath()))
                .methods(endpoint.getMethod())
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .build();

        if (endpoint.getMethod().equals(RequestMethod.GET)) {
            requestMappingHandlerMapping.
                    registerMapping(requestMappingInfo, queryController,
                            AppGatewayController.class.getDeclaredMethod("handleGetRequests",
                                    HttpServletRequest.class, HttpServletResponse.class, Map.class)
                    );
        }
    }

    private void addEndpointToSource(AppSourceEndpoint endpoint, AppSource appSource) {
        AppEndpoint appEndpoint = AppEndpoint.builder()
            .appSource(appSource)
            .method(endpoint.getMethod())
            .path(endpoint.getPath())
            .build();
        try {
            // only add this endpoint to the db if it's not already in there.
            if (!appEndpointRepository.existsByAppSourceEqualsAndMethodEqualsAndAndPathEquals(
                    appSource, endpoint.getMethod(), endpoint.getPath())) {
                this.appEndpointRepository.save(appEndpoint);
            }
        } catch (Exception e) {
            log.warn(String.format("Unable to add endpoint to app source %s.", appSource.getName()), e);
        }
    }

    /**
     * Utility function to de dupe any endpoints in the database produced by camel.
     * This function prefers the first match it finds out of a route that has duplicates and tries to delete
     * the others (breaking links if necessary).  This method should **have** to run, since there should not be
     * duplicates.
     * @param appSource the app source to search
     */
    private void deDuplicateRows(AppSource appSource) {
        for (AppEndpoint point : Lists.newArrayList(appEndpointRepository.findAllByAppSource(appSource))) {
            List<AppEndpoint> others = Lists.newArrayList(
                    appEndpointRepository.findAllByAppSourceEqualsAndMethodEqualsAndPathEquals(appSource, point.getMethod(), point.getPath()));

            for (int i = 1; i < others.size(); i++) {
                try {
                    appEndpointPrivRepository.removeAllByAppEndpoint(others.get(i));
                    appEndpointRepository.delete(others.get(i));
                } catch (Exception e) {
                    log.warn(String.format("Unable to delete duplicate app endpoint %s - %s.", point.getId(), point.getPath()), e);
                }
            }
        }

    }
}
