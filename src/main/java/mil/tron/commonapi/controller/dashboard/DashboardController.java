package mil.tron.commonapi.controller.dashboard;

import java.util.Date;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.response.WrappedEnvelopeResponse;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.dashboard.AppSourceErrorRequestCountDto;
import mil.tron.commonapi.dto.dashboard.AppSourceRequestCountDto;
import mil.tron.commonapi.dto.kpi.KpiSummaryDto;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.dashboard.DashboardService;

@RestController
@RequestMapping({"${api-prefix.v2}/dashboard"})
@PreAuthorizeDashboardAdmin
public class DashboardController {
	private DashboardService dashboardService;
	
	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}
	
	@Operation(summary = "Get a list of App Clients that have accessed organization records", 
			description = "Get a list of App Clients that have accessed organization records between two dates."
					+ " Will only include App Clients that have made successful requests to access organization records (http status between 200 and 300.)")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
					description = "Successful operation", 
							content = @Content(schema = @Schema(implementation = KpiSummaryDto.class))),
            @ApiResponse(responseCode = "403",
                description = "Forbidden (Requires DASHBOARD_ADMIN privilege)",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
            	description = "Bad Request. Possible reasons include: \n\n"
            			+ "Start Date required.\n\n"
            			+ "Start date must be before or equal to End Date.\n\n"
            			+ "Start date cannot be in the future (there would be no data).",
                	content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@WrappedEnvelopeResponse
    @GetMapping("/app-client-organization-accessors")
    public ResponseEntity<List<String>> getAppClientsAccessingOrganizations (
    		@Parameter(description = "Earliest date to include in UTC.",
    				schema = @Schema(type="string", format = "date", example = "yyyy-MM-dd")) 
		    	@RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
		    @Parameter(description = "Latest date to include in UTC. Will default to the current date if not provided.",
		    		schema = @Schema(type="string", format = "date", example = "yyyy-MM-dd")) 
		    	@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate
	) {
        return new ResponseEntity<>(dashboardService.getAppClientsAccessingOrgRecords(startDate, endDate), HttpStatus.OK);
    }
	
	@Operation(summary = "Get a list of App Sources along with their respective request count.", 
			description = "Get a list of App Sources along with their respective request counts between two dates."
					+ " Will only include successful requests (http status between 200 and 300)")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
					description = "Successful operation", 
							content = @Content(schema = @Schema(implementation = KpiSummaryDto.class))),
            @ApiResponse(responseCode = "403",
                description = "Forbidden (Requires DASHBOARD_ADMIN privilege)",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
            	description = "Bad Request. Possible reasons include: \n\n"
            			+ "Start Date required.\n\n"
            			+ "Start date must be before or equal to End Date.\n\n"
            			+ "Start date cannot be in the future (there would be no data).",
                	content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@WrappedEnvelopeResponse
    @GetMapping("/app-source-usage")
    public ResponseEntity<List<AppSourceRequestCountDto>> getAppSourceUsageCount (
    		@Parameter(description = "Earliest date to include in UTC.",
    				schema = @Schema(type="string", format = "date", example = "yyyy-MM-dd")) 
		    	@RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
		    @Parameter(description = "Latest date to include in UTC. Will default to the current date if not provided.",
		    		schema = @Schema(type="string", format = "date", example = "yyyy-MM-dd")) 
		    	@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate
	) {
        return new ResponseEntity<>(dashboardService.getAppSourceUsage(startDate, endDate, true, 10), HttpStatus.OK);
    }
	
	@Operation(summary = "Get a list of App Sources along with their respective request count.", 
			description = "Get a list of App Sources along with their respective request counts between two dates."
					+ " Will only include successful requests (http status between 200 and 300)")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
					description = "Successful operation", 
							content = @Content(schema = @Schema(implementation = KpiSummaryDto.class))),
            @ApiResponse(responseCode = "403",
                description = "Forbidden (Requires DASHBOARD_ADMIN privilege)",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
            	description = "Bad Request. Possible reasons include: \n\n"
            			+ "Start Date required.\n\n"
            			+ "Start date must be before or equal to End Date.\n\n"
            			+ "Start date cannot be in the future (there would be no data).",
                	content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@WrappedEnvelopeResponse
    @GetMapping("/app-source-error-usage")
    public ResponseEntity<List<AppSourceErrorRequestCountDto>> getAppSourceErrorUsageCount (
    		@Parameter(description = "Earliest date to include in UTC.",
    				schema = @Schema(type="string", format = "date", example = "yyyy-MM-dd")) 
		    	@RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
		    @Parameter(description = "Latest date to include in UTC. Will default to the current date if not provided.",
		    		schema = @Schema(type="string", format = "date", example = "yyyy-MM-dd")) 
		    	@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate
	) {
        return new ResponseEntity<>(dashboardService.getAppSourceErrorUsage(startDate, endDate), HttpStatus.OK);
    }
}
