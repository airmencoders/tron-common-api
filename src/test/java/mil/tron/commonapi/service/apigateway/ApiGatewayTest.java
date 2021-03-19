package mil.tron.commonapi.service.apigateway;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import mil.tron.commonapi.appgateway.AppSourceEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.stream.Collectors;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
public class ApiGatewayTest {

    @Value("classpath:/appsourceapis/puckboard.yml")
    Resource yamlResource;

    @Test
    void testYamlParse() throws Exception {
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(yamlResource.getURI().toString(),
                null, null);
        List<AppSourceEndpoint> endpoints = result.getOpenAPI().getPaths().entrySet().stream()
                .map(path -> {
                    String pathString = path.getKey();
                    // change {xx} to ${header.xx}
                    String updatedPath = pathString.replaceAll("\\{([^}]+)\\}", "\\${header.$1}" );
                    // get operations from paths.. .readOperations
                    List<AppSourceEndpoint> operations = path.getValue().readOperationsMap()
                            // build pojo for path and operations
                            .keySet().stream().map(method -> new AppSourceEndpoint(updatedPath,
                                    ApiGatewayTest.convertMethod(method)))
                            .collect(Collectors.toList());
                    return operations;
                }).flatMap(List::stream).collect(Collectors.toList());
        // iterate through operations and build proxies
        System.out.println(result.toString());
    }

    private static RequestMethod convertMethod(PathItem.HttpMethod swaggerHttpMethod) {
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
}
