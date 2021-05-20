package mil.tron.commonapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.response.WrappedEnvelopeResponse;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.HttpLogDtoPaginationResponseWrapper;
import mil.tron.commonapi.dto.HttpLogEntryDetailsDto;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.service.trace.HttpTraceService;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

@RestController
@RequestMapping(value = { "${api-prefix.v1}/logs", "${api-prefix.v2}/logs" })
@PreAuthorizeDashboardAdmin
@Profile("production | development")
public class HttpLogsController {

    private HttpTraceService httpTraceService;

    public HttpLogsController(HttpTraceService httpTraceService) {
        this.httpTraceService = httpTraceService;
    }

    @Operation(summary = "Retrieves a subset of the server http trace logs from a specified date",
            description = "Must have DASHBOARD_ADMIN privilege to access.  Date time given is in the format 'yyyy-MM-ddTHH:mm:ss' and should be in UTC time. " +
                    "This date time parameter is required.  This is also a pageable interface (using page and size query params).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = HttpLogDtoPaginationResponseWrapper.class))),
                    headers = @Header(
                            name="link",
                            description = "Contains the appropriate pagination links if application. "
                                    + "If no pagination query params given, then no pagination links will exist. "
                                    + "Possible rel values include: first, last, prev, next",
                            schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "403",
                    description = "Insufficient privileges (requires DASHBOARD_ADMIN)"),
    })
    @WrappedEnvelopeResponse
    @GetMapping("")
    public ResponseEntity<Object> getHttpLogs(
            @Parameter(required = true, description = "The date/time from which to query from - format yyyy-MM-dd'T'HH:mm:ss - in UTC")
                @RequestParam(name="fromDate") String fromDate,
            @Parameter(description = "HTTP method to filter on, e.g. GET, POST, etc")
                @RequestParam(name="method", required = false, defaultValue = "") String method,
            @Parameter(description = "Username to filter on")
                @RequestParam(name="userName", required = false, defaultValue = "") String userName,
            @Parameter(description = "HTTP response status to sort on")
                @RequestParam(name="status", required = false, defaultValue = "-1") int status,
            @Parameter(description = "Filter by user agent containing given string")
                @RequestParam(name="userAgentContains", required = false, defaultValue = "") String userAgentContains,
            @Parameter(description = "Filter by requested url containing given string")
                @RequestParam(name="requestedUrlContains", required = false, defaultValue = "") String requestedUrlContains,
            @ParameterObject Pageable page) {
        try {
            if (!fromDate.isBlank()) {
                SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = fmt.parse(fromDate);
                return new ResponseEntity<>(httpTraceService
                        .getLogsFromDate(date,
                                method,
                                userName,
                                status,
                                userAgentContains,
                                requestedUrlContains,
                                page), HttpStatus.OK);
            }

            throw new BadRequestException("Could not parse date and time");
        }
        catch (ParseException e) {
            throw new BadRequestException("No Date and Time parameter provided");
        }
    }

    @Operation(summary = "Retrieves the full record of a particular request",
            description = "Must have DASHBOARD_ADMIN privilege to access. This detailed info includes the request/response bodies (if present)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = HttpLogEntryDetailsDto.class)))),
            @ApiResponse(responseCode = "403",
                    description = "Insufficient privileges (requires DASHBOARD_ADMIN)")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Object> getHttpLogDetails(@PathVariable UUID id) {
        return new ResponseEntity<>(httpTraceService.getLogInfoDetails(id), HttpStatus.OK);
    }
}
