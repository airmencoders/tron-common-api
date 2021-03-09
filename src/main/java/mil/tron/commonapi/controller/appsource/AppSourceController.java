package mil.tron.commonapi.controller.appsource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.dto.appsource.AppSourceDto;
import mil.tron.commonapi.service.AppSourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/***
 * Controller for App Source endpoints.
 */
@RestController
@RequestMapping("${api-prefix.v1}/app-source")
public class AppSourceController {

    private AppSourceService appSourceService;

    @Autowired
    AppSourceController(AppSourceService appSourceService) {
        this.appSourceService = appSourceService;
    }

    @Operation(summary = "Creates an App Source including App Client permissions.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
                    description = "Successful creation"),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges"),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Request Body")
    })
    @PreAuthorizeDashboardAdmin
    @PostMapping
    public ResponseEntity<AppSourceDetailsDto> createAppSource(
            @Parameter(name = "App Source", required = true) @Valid @RequestBody AppSourceDetailsDto appSourceDto) {
        return new ResponseEntity<>(this.appSourceService.createAppSource(appSourceDto), HttpStatus.CREATED);
    }

    @Operation(summary = "Gets all App Sources.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful creation"),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges"),
    })
    @PreAuthorizeDashboardAdmin
    @GetMapping
    public ResponseEntity<List<AppSourceDto>> getAppSources() {
        return new ResponseEntity<>(this.appSourceService.getAppSources(), HttpStatus.OK);
    }
}
