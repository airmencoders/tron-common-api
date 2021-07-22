package mil.tron.commonapi.controller.puckboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.service.puckboard.PuckboardExtractorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping({"${api-prefix.v1}/puckboard", "${api-prefix.v2}/puckboard"})
public class PuckboardEtlController {

    @Autowired
    private PuckboardExtractorService puckboardService;

    @Value("${puckboard-url-stable}")
    private String puckboardUrlStable;

    @Value("${puckboard-url}")
    private String puckboardUrl;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Make a bean here for use/visibility in the Unit Test mocking
     *
     * @param builder
     * @return
     */
    @Bean("puckboardFetcher")
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Autowired
    @Qualifier("puckboardFetcher")
    private RestTemplate restTemplate;

    @GetMapping("/test")
    public ResponseEntity<Object> testPuckboardComms(
            @RequestParam(name = "type", required = false, defaultValue = "organization") String type) {

        String uri = this.puckboardUrlStable + "/organizations";

        switch (type) {
            case "personnel":
                uri = this.puckboardUrlStable + "/personnel";
                break;
            case "branch":
                uri = this.puckboardUrl + "/branch";
                break;
            default:
                break;
        }

        JsonNode orgs;
        try {
            ResponseEntity<String> orgsString = restTemplate.getForEntity(
                    uri,
                    String.class);
            orgs = mapper.readTree(orgsString.getBody());
            return new ResponseEntity<>(orgs, HttpStatus.OK);

        } catch (RestClientException | JsonProcessingException e) {
            return new ResponseEntity<>("Puckboard Fetch error - " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/extract")
    public ResponseEntity<Object> getPuckboardData() {

        // grab puckboard organizations
        JsonNode orgs;
        try {
            ResponseEntity<String> orgsString = restTemplate.getForEntity(
                    this.puckboardUrlStable + "/organizations",
                    String.class);
            orgs = mapper.readTree(orgsString.getBody());
        } catch (RestClientException | JsonProcessingException e) {
            return new ResponseEntity<>("Puckboard Organization Fetch error - " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // grab puckboard personnel
        JsonNode people;
        try {
            ResponseEntity<String> peopleString = restTemplate.getForEntity(
                    this.puckboardUrlStable + "/personnel",
                    String.class);
            people = mapper.readTree(peopleString.getBody());
        } catch (RestClientException | JsonProcessingException e) {
            return new ResponseEntity<>("Puckboard Personnel Fetch error - " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // grab puckboard branch/rank info
        JsonNode branchInfo;
        try {
            ResponseEntity<String> branchString = restTemplate.getForEntity(
                    this.puckboardUrl + "/branch",
                    String.class);
            branchInfo = mapper.readTree(branchString.getBody());
        } catch (RestClientException | JsonProcessingException e) {
            return new ResponseEntity<>("Puckboard Branch Info Fetch error - " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // give to the extractor service for processing
        // return the orgs/personnel as list of Common API Squadron entities
        return new ResponseEntity<>(puckboardService.persistOrgsAndMembers(orgs, people, branchInfo), HttpStatus.OK);
    }
}
