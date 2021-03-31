package mil.tron.commonapi.appgateway;

import io.swagger.v3.oas.models.PathItem;
import mil.tron.commonapi.entity.appsource.AppSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.bind.annotation.RequestMethod;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
public class AppSourceEndpointBuilderTest {

    @Autowired
    private AppSourceEndpointsBuilder appSourceEndpointsBuilder;

    @Test
    void testNoFailForBadConfig() {
        assertDoesNotThrow(
                () -> {
                    this.appSourceEndpointsBuilder.initializeWithAppSourceDef(
                            new AppSourceInterfaceDefinition("Name",
                                    "Nofile.yml", "localhost", "/"),
                            AppSource.builder().name("Name").appSourcePath("/").openApiSpecFilename("NoFile.yml").build()
                    );
                });
    }

    @Test
    void testMethodConversion() {
        assertEquals(RequestMethod.GET, AppSourceEndpointsBuilder.convertMethod(PathItem.HttpMethod.GET));
        assertEquals(RequestMethod.POST, AppSourceEndpointsBuilder.convertMethod(PathItem.HttpMethod.POST));
        assertEquals(RequestMethod.DELETE, AppSourceEndpointsBuilder.convertMethod(PathItem.HttpMethod.DELETE));
        assertEquals(RequestMethod.PUT, AppSourceEndpointsBuilder.convertMethod(PathItem.HttpMethod.PUT));
        assertEquals(RequestMethod.PATCH, AppSourceEndpointsBuilder.convertMethod(PathItem.HttpMethod.PATCH));
    }
}
