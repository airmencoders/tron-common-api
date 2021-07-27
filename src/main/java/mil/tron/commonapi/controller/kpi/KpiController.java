package mil.tron.commonapi.controller.kpi;

import java.time.LocalDate;

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
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.kpi.KpiSummaryDto;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.kpi.KpiService;

@RestController
@RequestMapping({"${api-prefix.v2}/kpi"})
@PreAuthorizeDashboardAdmin
public class KpiController {
	private KpiService kpiService;

	KpiController(KpiService kpiService) {
        this.kpiService = kpiService;
    }
	
	@Operation(summary = "Retrieves all KPI information", description = "Retrieves all KPI information between two dates.")
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
    @GetMapping("/summary")
    public ResponseEntity<KpiSummaryDto> getKpiSummary (
            @Parameter(description = "Earliest date to include", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Latest date to include. Will default to today if not provided") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
	) {
        return new ResponseEntity<>(kpiService.aggregateKpis(startDate, endDate), HttpStatus.OK);
    }
}
