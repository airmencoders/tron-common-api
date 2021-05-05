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
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;

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

    @Autowired
    private HttpServletRequest request;

    @GetMapping("/test")
    public ResponseEntity<Object> testPuckboardComms() {

        // grab puckboard organizations
        JsonNode orgs;
        try {
            ResponseEntity<String> orgsString = restTemplate.getForEntity(
                    this.puckboardUrl + "/organizations?isSchedulingUnit=true",
                    String.class);
            orgs = mapper.readTree(orgsString.getBody());
            return new ResponseEntity<>(orgs, HttpStatus.OK);

        } catch (RestClientException | JsonProcessingException e) {
            return new ResponseEntity<>("Puckboard Organization Fetch error - " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/extract")
    public ResponseEntity<Object> getPuckboardData() {

        String bearerToken = request.getHeader("authorization");
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null && !bearerToken.isEmpty()) {
            headers.set("Authorization", bearerToken);
        }
        HttpEntity entity = new HttpEntity(headers);

        // grab puckboard organizations
        JsonNode orgs;
        try {
            ResponseEntity<String> orgsString = restTemplate.exchange(
                    this.puckboardUrl + "/organizations?isSchedulingUnit=true",
                    HttpMethod.GET,
                    entity,
                    String.class);
            orgs = mapper.readTree(orgsString.getBody());
        } catch (RestClientException | JsonProcessingException e) {
            return new ResponseEntity<>("Puckboard Organization Fetch error - " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // grab puckboard personnel
        JsonNode people;
        try {
            ResponseEntity<String> peopleString = restTemplate.exchange(
                    this.puckboardUrl + "/personnel",
                    HttpMethod.GET,
                    entity,
                    String.class);
            people = mapper.readTree(peopleString.getBody());
        } catch (RestClientException | JsonProcessingException e) {
            return new ResponseEntity<>("Puckboard Personnel Fetch error - " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // grab puckboard branch/rank info
        JsonNode branchInfo;
        try {
            ResponseEntity<String> branchString = restTemplate.exchange(
                    this.puckboardUrl + "/branch",
                    HttpMethod.GET,
                    entity,
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
