package mil.tron.commonapi.service.trace;

import mil.tron.commonapi.dto.HttpLogEntryDetailsDto;
import mil.tron.commonapi.dto.HttpLogEntryDto;
import mil.tron.commonapi.entity.HttpLogEntry;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.HttpLogsRepository;
import org.modelmapper.ModelMapper;
import org.springframework.boot.actuate.trace.http.HttpTrace;
import org.springframework.boot.actuate.trace.http.HttpTraceRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A class that intercepts traffic in a request and the response we send back for logging.
 * This is used by Spring Acutator's HTTP Trace - hence the need to implement findAll() and add().
 *
 * It is in the add() method we convert the Trace (and our custom ContentTrace) into the HttpLogEntry
 * POJO for storage to the database.
 */
@Service
@Profile("production | development")
public class HttpTraceService implements HttpTraceRepository {

    private ModelMapper modelMapper = new ModelMapper();
    private final Object lockObj = new Object();

    private ContentTraceManager contentTraceManager;
    private HttpLogsRepository httpLogsRepository;

    public HttpTraceService(HttpLogsRepository httpLogsRepository,
                            ContentTraceManager contentTraceManager) {
        this.httpLogsRepository = httpLogsRepository;
        this.contentTraceManager = contentTraceManager;
    }

    /**
     * Gets http logs from a given ISO date/time in UTC timezone.  This is called from the
     * /logs controller
     * @param fromDate date in form yyyy-MM-dd'T'HH:mm:ss
     * @param method optional http method to filter on
     * @param userName optional username to filter on
     * @param status optional status code to filter on
     * @param userAgentContains optional user agent string to filter on
     * @param requestedUrlContains optional url string to filter on
     * @param pageable Pageable object from the controller (i.e. page= & size= & sort=)
     * @return HttpLogEntryDto list (not including response and request bodies)
     */
    public List<HttpLogEntryDto> getLogsFromDate(Date fromDate,
                                                 String method,
                                                 String userName,
                                                 int status,
                                                 String userAgentContains,
                                                 String requestedUrlContains,
                                                 Pageable pageable) {
        return httpLogsRepository
                .findByRequestTimestampGreaterThanEqual(fromDate, pageable)
                .stream()
                .filter(item -> method.isEmpty() || item.getRequestMethod().toLowerCase().contains(method.toLowerCase()))
                .filter(item -> userName.isEmpty() || item.getUserName().toLowerCase().contains(userName.toLowerCase()))
                .filter(item -> status == -1 || item.getStatusCode() == status)
                .filter(item -> userAgentContains.isEmpty() || item.getUserAgent().toLowerCase().contains(userAgentContains.toLowerCase()))
                .filter(item -> requestedUrlContains.isEmpty() || item.getRequestedUrl().toLowerCase().contains(requestedUrlContains.toLowerCase()))
                .map(item -> modelMapper.map(item, HttpLogEntryDto.class))
                .collect(Collectors.toList());
    }

    /**
     * Gets a single http log by its UUID, which will include response and request bodies
     * @param id UUID of the http record
     * @return the HttpLogEntryDetailsDto
     */
    public HttpLogEntryDetailsDto getLogInfoDetails(UUID id) {
        HttpLogEntry dto = httpLogsRepository
                .findById(id)
                .orElseThrow(() -> new RecordNotFoundException("Http Log with that ID not found"));

        return modelMapper.map(dto, HttpLogEntryDetailsDto.class);
    }

    @Override
    public List<HttpTrace> findAll() {
        return new ArrayList<>();
    }

    /**
     * Spring actuator calls this to add a trace to our database.  We also fetch our @RequestScope'd
     * ContentTrace POJO so we can add in the request body and response body.  This operation is synchronized
     * to ensure no other requests can "cross" here since we need to ensure the ContentTrace POJOs stay with
     * their respective requests.
     * @param trace the injected HTTP trace
     */
    @Override
    public void add(HttpTrace trace) {
        synchronized (lockObj) {
            ContentTrace contentTrace = contentTraceManager.getTrace();

            // get the principal - which may be null
            String user = "Unknown";
            HttpTrace.Principal principal = trace.getPrincipal();
            if (principal != null) {
                user = principal.getName();
            }

            // get the user-agent - which may or may not be there, and may be of varying case
            // HttpTrace.Request class doesn't do the case insensitivity for us, so we must to be safe
            Map<String, List<String>> lowerCaseHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            lowerCaseHeaders.putAll(trace.getRequest().getHeaders());
            List<String> userAgentHeader = lowerCaseHeaders.get("user-agent");
            String userAgent = userAgentHeader != null ? userAgentHeader.get(0) : "Unknown";

            httpLogsRepository.save(
                    HttpLogEntry
                            .builder()
                            .userName(user)
                            .timeTakenMs(trace.getTimeTaken())
                            .queryString(trace.getRequest().getUri().getQuery())
                            .userAgent(userAgent)
                            .remoteIp(trace.getRequest().getRemoteAddress())
                            .requestTimestamp(Date.from(trace.getTimestamp()))
                            .requestMethod(trace.getRequest().getMethod())
                            .requestedUrl(trace.getRequest().getUri().toString())
                            .requestHost(trace.getRequest().getUri().getHost())
                            .requestBody(contentTrace.getRequestBody())
                            .responseBody(contentTrace.getResponseBody())
                            .statusCode(trace.getResponse().getStatus())
                            .build());
        }
    }
}
