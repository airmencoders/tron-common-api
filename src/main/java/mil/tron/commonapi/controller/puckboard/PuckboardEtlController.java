package mil.tron.commonapi.controller.puckboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.service.puckboard.PuckboardExtractorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("${api-prefix.v1}/puckboard")
public class PuckboardEtlController {

    @Autowired
    private PuckboardExtractorService puckboardService;

    @Value("${puckboard-url}")
    private String puckboardUrl;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Make a bean here for use/visibility in the Unit Test mocking
     * @param builder
     * @return
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Autowired
    private RestTemplate restTemplate;

    @GetMapping("/extract")
    public ResponseEntity<Object> getPuckboardData() {

        // grab puckboard organizations
        JsonNode orgs;
        try {
            String orgsString = restTemplate.getForObject(this.puckboardUrl + "/organizations", String.class);
            orgs = mapper.readTree(orgsString);
        }
        catch (RestClientException | JsonProcessingException e) {
            return new ResponseEntity<>("Puckboard Organization Fetch error - " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // grab puckboard personnel
        JsonNode people;
        try {
            String peopleString = restTemplate.getForObject(this.puckboardUrl + "/personnel", String.class);
            people = mapper.readTree(peopleString);
        }
        catch (RestClientException | JsonProcessingException e) {
            return new ResponseEntity<>("Puckboard Personnel Fetch error - " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // grab puckboard branch/rank info
        JsonNode branchInfo;
        try {
            String branchString = restTemplate.getForObject(this.puckboardUrl + "/branch", String.class);
            branchInfo = mapper.readTree(branchString);
        }
        catch (RestClientException | JsonProcessingException e) {
            return new ResponseEntity<>("Puckboard Branch Info Fetch error - " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // give to the extractor service for processing
        // return the orgs/personnel as list of Common API Squadron entities
        return new ResponseEntity<>(puckboardService.persistOrgsAndMembers(orgs, people, branchInfo), HttpStatus.OK);
    }
}
