package mil.tron.commonapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnProperty(value = "minio.enabled", havingValue = "true")
public class DocumentSpaceConfig {
	@Value("${minio.connection-string}")
	private String connectionString;
	
	@Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket-name}")
    private String bucketName;
    
    @Value("${aws-default-region")
    private String region;

    @Bean
    public AmazonS3 documentSpaceClient() {
        if (this.accessKey == null || this.secretKey == null) {
        	log.warn("Missing Minio credentials");
            return null;
        }
        
	    BasicAWSCredentials awsCredentials = new BasicAWSCredentials(this.accessKey, this.secretKey);
	    ClientConfiguration clientConfiguration = new ClientConfiguration();
	    
	    return AmazonS3ClientBuilder.standard()
	    		.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(connectionString, region))
	    		.withPathStyleAccessEnabled(true)
	    		.withClientConfiguration(clientConfiguration)
	            .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
	            .build();
    }
    
    @Bean
    public TransferManager documentSpaceTransferManager() {
    	if (documentSpaceClient() == null) {
    		log.warn("Document Space client is not available to create Transfer Manager");
    		return null;
    	}
    	
    	return TransferManagerBuilder.standard().withS3Client(documentSpaceClient()).build();
    }
}
