package mil.tron.commonapi.controller;

import java.util.Date;
import java.util.UUID;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.dto.metrics.AppClientCountMetricDto;
import mil.tron.commonapi.dto.metrics.AppEndpointCountMetricDto;
import mil.tron.commonapi.dto.metrics.AppSourceCountMetricDto;
import mil.tron.commonapi.dto.metrics.AppSourceMetricDto;
import mil.tron.commonapi.dto.metrics.EndpointMetricDto;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.MetricService;

@RestController
@RequestMapping("${api-prefix.v1}/metrics")
@PreAuthorize("hasAuthority('DASHBOARD_ADMIN') or @appSourceService.userIsAdminForAppSource(#id, principal.username)")
public class MetricsController {
    private MetricService metricService;
    private final String dateMessage = "Start date must be before End Date";

    @Value("${api-prefix.v1}")
    private String apiVersion;

    @Autowired
    MetricsController(MetricService metricService) {
        this.metricService = metricService;
    }

    @Operation(summary = "Retrieves all stored metrics values for given endpoint", description = "Retrieves all stored metric values for given endpoint")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
					description = "Successful operation", 
						content = @Content(schema = @Schema(implementation = EndpointMetricDto.class))),
            @ApiResponse(responseCode = "400",
                description = "Bad Rquest (Start date and end date are both required. Start date must be before end date)",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                description = "Forbidden (Requires DASHBOARD_ADMIN privilege or must be an Admin of the endpoint's App Source)",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                description = "Resource not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @PreAuthorize("hasAuthority('DASHBOARD_ADMIN') or @appSourceService.userIsAdminForAppSourceByEndpoint(#id, principal.username)")
    @GetMapping("/endpoint/{id}")
    public ResponseEntity<EndpointMetricDto> getAllMetricsForEndpoint (
        @Parameter(description = "Endpoint Id to search with", required = true) @PathVariable("id") UUID id,
        @Parameter(description = "Earliest date to include", required = true) @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Valid Date startDate,
        @Parameter(description = "Latest date to include", required = true) @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Valid Date endDate
    ) {
        if(startDate.compareTo(endDate) > -1) {
            throw new BadRequestException(dateMessage);
        }
        return new ResponseEntity<>(metricService.getAllMetricsForEndpointDto(id, startDate, endDate), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves all stored metrics values for given app source", description = "Retrieves all stored metric values for given app source")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
					description = "Successful operation", 
						content = @Content(schema = @Schema(implementation = AppSourceMetricDto.class))),
            @ApiResponse(responseCode = "400",
                description = "Bad Rquest (Start date and end date are both required. Start date must be before end date)",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                description = "Forbidden (Requires DASHBOARD_ADMIN privilege or must be an Admin of the App Source)",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                description = "Resource not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @GetMapping("/appsource/{id}")
    public ResponseEntity<AppSourceMetricDto> getAllMetricsForAppSource (
        @Parameter(description = "App Source Id to search with", required = true) @PathVariable("id") UUID id,
        @Parameter(description = "Earliest date to include", required = true) @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date startDate,
        @Parameter(description = "Latest date to include", required = true) @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date endDate
    ) {
        if(startDate.compareTo(endDate) > -1) {
            throw new BadRequestException(dateMessage);
        }
        return new ResponseEntity<>(metricService.getMetricsForAppSource(id, startDate, endDate), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves sum of stored metric values for given app source", description = "Retrieves sum of stored metric values for given app source for each endppoint and for each app client")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
					description = "Successful operation", 
						content = @Content(schema = @Schema(implementation = AppSourceCountMetricDto.class))),
            @ApiResponse(responseCode = "400",
                description = "Bad Rquest (Start date and end date are both required. Start date must be before end date)",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                description = "Forbidden (Requires DASHBOARD_ADMIN privilege or must be an Admin of the App Source)",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                description = "Resource not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @GetMapping("/count/{id}")
    public ResponseEntity<AppSourceCountMetricDto> getCountOfMetricsForAppSource (
        @Parameter(description = "App Source Id to search with", required = true) @PathVariable("id") UUID id,
        @Parameter(description = "Earliest date to include", required = true) @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date startDate,
        @Parameter(description = "Latest date to include", required = true) @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date endDate
    ) {
        if(startDate.compareTo(endDate) > -1) {
            throw new BadRequestException(dateMessage);
        }
        return new ResponseEntity<>(metricService.getCountOfMetricsForAppSource(id, startDate, endDate), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves sum of stored metric values for given endpoint path on given app source", description = "Retrieves sum of stored metric values for given endpoint path on given app source for each app client")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
					description = "Successful operation", 
						content = @Content(schema = @Schema(implementation = AppEndpointCountMetricDto.class))),
            @ApiResponse(responseCode = "400",
                description = "Bad Rquest (Start date, end date, and path are all required. Start date must be before end date)",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                description = "Forbidden (Requires DASHBOARD_ADMIN privilege or must be an Admin of the App Source)",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                description = "Resource not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @GetMapping("/count/{id}/endpoint")
    public ResponseEntity<AppEndpointCountMetricDto> getCountOfMetricsForEndpoint (
        @Parameter(description = "App Source Id to search with", required = true) @PathVariable("id") UUID id,
        @Parameter(description = "Endpoint Path to search with", required = true) @RequestParam("path") String path,
        @Parameter(description = "Endpoint Request Method Type", required = true) @RequestParam("method") RequestMethod method,
        @Parameter(description = "Earliest date to include", required = true) @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date startDate,
        @Parameter(description = "Latest date to include", required = true) @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date endDate
    ) {
        if(startDate.compareTo(endDate) > -1) {
            throw new BadRequestException(dateMessage);
        }
        return new ResponseEntity<>(metricService.getCountOfMetricsForEndpoint(id, path, method, startDate, endDate), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves sum of stored metric values for given app client name on given app source", description = "Retrieves sum of stored metric values for given app client name on given app source for each endpoint")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
					description = "Successful operation", 
						content = @Content(schema = @Schema(implementation = AppClientCountMetricDto.class))),
            @ApiResponse(responseCode = "400",
                description = "Bad Rquest (Start date, end date, and name are all required. Start date must be before end date)",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                description = "Forbidden (Requires DASHBOARD_ADMIN privilege or must be an Admin of the App Source)",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                description = "Resource not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @GetMapping("/count/{id}/appclient")
    public ResponseEntity<AppClientCountMetricDto> getCountOfMetricsForAppClient (
        @Parameter(description = "App Source Id to search with", required = true) @PathVariable("id") UUID id,
        @Parameter(description = "App Client Name to search with", required = true) @RequestParam("name") String name,
        @Parameter(description = "Earliest date to include", required = true) @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date startDate,
        @Parameter(description = "Latest date to include", required = true) @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date endDate
    ) {
        if(startDate.compareTo(endDate) > -1) {
            throw new BadRequestException(dateMessage);
        }
        return new ResponseEntity<>(metricService.getCountOfMetricsForAppClient(id, name, startDate, endDate), HttpStatus.OK);
    }
}
