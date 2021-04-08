package mil.tron.commonapi.controller.metric;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.metrics.AppSourceMetricDto;
import mil.tron.commonapi.dto.metrics.EndpointMetricDto;
import mil.tron.commonapi.service.MetricService;

// @RestController
// @RequestMapping("${api-prefix.v1}/metrics")
// @PreAuthorizeDashboardAdmin
// public class MetricsController {
    
//     public MetricService metricService;

//     public MetricsController(MetricService metricService) {
//         this.metricService = metricService;
//     }

//     @Operation(summary = "Retrieves the number of requests by endpoint", description = "Retrieves the authorized dashboard user")
//     @ApiResponses(value = {
//             @ApiResponse(responseCode = "200",
//                     description = "Successful operation",
//                     content = @Content(schema = @Schema(implementation = AppSourceMetricDto.class)))
//     })
//     @GetMapping(value = "/appsource/{id}")
//     public ResponseEntity<AppSourceMetricDto> getAllMetricsForAppSource(@Parameter(description = "AppSource Id to get metrics for", required = true) @PathVariable("id") UUID id) {
//         AppSourceMetricDto endpointMetrics = metricService.getMetricsForAppSource(id);
//         return new ResponseEntity<>(endpointMetrics, HttpStatus.OK);
//     }

//     @Operation(summary = "Retrieves the number of requests by endpoint", description = "Retrieves the authorized dashboard user")
//     @ApiResponses(value = {
//             @ApiResponse(responseCode = "200",
//                     description = "Successful operation",
//                     content = @Content(schema = @Schema(implementation = EndpointMetricDto.class)))
//     })
//     @GetMapping(value = "/endpoint/{id}")
//     public ResponseEntity<EndpointMetricDto> getMetricForEndpoint(@Parameter(description = "AppEndpoint Id to get metrics for", required = true) @PathVariable("id") UUID id) {
//         EndpointMetricDto endpointMetric = metricService.getMetricForEndpoint(id);
//         return new ResponseEntity<>(endpointMetric, HttpStatus.OK);
//     }
// }
