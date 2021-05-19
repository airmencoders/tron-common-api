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

    public List<HttpLogEntryDto> getLogsFromDate(Date fromDate, Pageable pageable) {
        return httpLogsRepository
                .findByRequestTimestampGreaterThanEqual(fromDate, pageable)
                .stream()
                .map(item -> modelMapper.map(item, HttpLogEntryDto.class))
                .collect(Collectors.toList());
    }

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

            httpLogsRepository.saveAndFlush(
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

