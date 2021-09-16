package mil.tron.commonapi.service.documentspace;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import io.findify.s3mock.S3Mock;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceInfoDto;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class DocumentSpaceServiceImplTest {
    private DocumentSpaceServiceImpl documentService;
    private AmazonS3 amazonS3;
    private S3Mock s3Mock;
    private DocumentSpaceInfoDto dto = DocumentSpaceInfoDto.builder()
            .name("test")
            .build();

    private DocumentSpaceInfoDto invalidDto = DocumentSpaceInfoDto.builder()
            .name("test name")
            .build();

    @BeforeEach
    void setup() {
        BasicAWSCredentials awsCredentials = new BasicAWSCredentials("admin", "admin");
        ClientConfiguration clientConfiguration = new ClientConfiguration();

        amazonS3 = AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:9002", "Earth"))
                .withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfiguration)
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                .build();

        documentService = new DocumentSpaceServiceImpl(amazonS3, "testbucket");

        s3Mock = new S3Mock.Builder()
                .withPort(9002)
                .withInMemoryBackend()
                .build();

        s3Mock.start();
        amazonS3.createBucket("testbucket");
    }

    @AfterEach
    void destroy() {
        s3Mock.shutdown();
    }

    @Test
    void testListSpaces() {
        assertEquals(0, documentService.listSpaces().size());
        documentService.createSpace(dto);
        assertEquals(1, documentService.listSpaces().size());

        // test invalid name caught
        assertThrows(BadRequestException.class, () -> documentService.createSpace(invalidDto));

        // test duplicate name caught
        assertThrows(ResourceAlreadyExistsException.class, () -> documentService.createSpace(dto));
    }

    @Test
    void deleteSpace() {
        documentService.createSpace(dto);
        assertEquals(1, documentService.listSpaces().size());
        assertDoesNotThrow(() -> documentService.deleteSpace("test"));
        assertThrows(RecordNotFoundException.class, () -> documentService.deleteSpace("test"));
    }

}
