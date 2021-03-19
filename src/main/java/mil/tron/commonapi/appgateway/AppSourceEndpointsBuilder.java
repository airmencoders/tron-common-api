package mil.tron.commonapi.appgateway;

import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.extern.slf4j.Slf4j;
import mil.tron.commonapi.controller.AppGatewayController;
import mil.tron.commonapi.service.AppGatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AppSourceEndpointsBuilder {

    private AppGatewayController queryController;

    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private AppSourceConfig appSourceConfig;

    private AppGatewayService appGatewayService;

    private String apiVersionPrefix;

    @Autowired
    AppSourceEndpointsBuilder(RequestMappingHandlerMapping requestMappingHandlerMapping,
                              AppGatewayController queryController,
                              AppGatewayService appGatewayService,
                              AppSourceConfig appSourceConfig,
                              @Value("${api-prefix.v1}") String apiVersionPrefix
    ) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
        this.queryController = queryController;
        this.appSourceConfig = appSourceConfig;
        this.apiVersionPrefix = apiVersionPrefix;
        this.appGatewayService = appGatewayService;
        this.createAppSourceEndpoints(this.appSourceConfig);
    }

    private void createAppSourceEndpoints(AppSourceConfig appSourceConfig) {
        AppSourceInterfaceDefinition[] appDefs = appSourceConfig.getAppSourceDefs();
        if (appDefs == null) {
            log.warn("No AppSource Definitions were found.");
            return;
        }
        for (AppSourceInterfaceDefinition appDef : appDefs) {
            this.initializeWithAppSourceDef(appDef);
        }
    }

    public void initializeWithAppSourceDef(AppSourceInterfaceDefinition appDef) {
        try {
            List<AppSourceEndpoint> appSourceEndpoints = this.parseAppSourceEndpoints(appDef.getOpenApiSpecFilename());
            this.appGatewayService.addSourceDefMapping(appDef.getAppSourcePath(),
                    appDef);
            for (AppSourceEndpoint appEndpoint: appSourceEndpoints) {
                this.addMapping(appDef.getAppSourcePath(), appEndpoint);
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
                    List<AppSourceEndpoint> operations = path.getValue().readOperationsMap()
                            // build pojo for path and operations
                            .keySet().stream().map(method -> new AppSourceEndpoint(pathString,
                                    AppSourceEndpointsBuilder.convertMethod(method)))
                            .collect(Collectors.toList());
                    return operations;
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

    private void addMapping(String appSource, AppSourceEndpoint endpoint) throws NoSuchMethodException {

        RequestMappingInfo requestMappingInfo = RequestMappingInfo
                .paths(String.format("%s/app/%s%s",
                        this.apiVersionPrefix,
                        appSource,
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

}
