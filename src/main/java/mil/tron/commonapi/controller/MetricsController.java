package mil.tron.commonapi.controller;

import java.util.Date;
import java.util.UUID;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.metrics.AppSourceMetricDto;
import mil.tron.commonapi.dto.metrics.EndpointMetricDto;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.InvalidFieldValueException;
import mil.tron.commonapi.service.MetricService;

@RestController
@RequestMapping("${api-prefix.v1}/metrics")
public class MetricsController {
    private MetricService metricService;

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
						content = @Content(array = @ArraySchema(schema = @Schema(implementation = EndpointMetricDto.class))))
	})
    @PreAuthorizeDashboardAdmin
    @GetMapping("/endpoint/{id}")
    public ResponseEntity<EndpointMetricDto> getAllMetricsForEndpoint (
        @Parameter(description = "Endpoint Id to search with", required = true) @PathVariable("id") UUID id,
        @Parameter(description = "Earliest date to include", required = true) @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Valid Date startDate,
        @Parameter(description = "Latest date to include", required = true) @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Valid Date endDate
    ) {
        if(startDate.compareTo(endDate) > -1) {
            throw new BadRequestException("Start date must be before End Date");
        }
        return new ResponseEntity<>(metricService.getAllMetricsForEndpointDto(id, startDate, endDate), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves all stored metrics values for given app source", description = "Retrieves all stored metric values for given app source")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
					description = "Successful operation", 
						content = @Content(array = @ArraySchema(schema = @Schema(implementation = AppSourceMetricDto.class))))
	})
    @PreAuthorizeDashboardAdmin
    @GetMapping("/appsource/{id}")
    public ResponseEntity<AppSourceMetricDto> getAllMetricsForAppSource (
        @Parameter(description = "App Source Id to search with", required = true) @PathVariable("id") UUID id,
        @Parameter(description = "Earliest date to include", required = true) @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date startDate,
        @Parameter(description = "Latest date to include", required = true) @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date endDate
    ) {
        if(startDate.compareTo(endDate) > -1) {
            throw new BadRequestException("Start date must be before End Date");
        }
        return new ResponseEntity<>(metricService.getMetricsForAppSource(id, startDate, endDate), HttpStatus.OK);
    }
}
