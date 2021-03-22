package mil.tron.commonapi.controller.appsource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.dto.appsource.AppSourceDto;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.AppSourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

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
                    description = "No DASHBOARD_ADMIN privileges",
            		content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
					)),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Request Body",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
                    ))
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
                    description = "Successful operation",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AppSourceDto.class)))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
            		content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
					))
    })
    @PreAuthorizeDashboardAdmin
    @GetMapping
    public ResponseEntity<List<AppSourceDto>> getAppSources() {
        return new ResponseEntity<>(this.appSourceService.getAppSources(), HttpStatus.OK);
    }

    @Operation(summary = "Returns the details for an App Source")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AppSourceDetailsDto.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
            		content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
					)),
            @ApiResponse(responseCode = "404",
                    description = "Requested App Source not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
                    ))
    })
    @PreAuthorizeDashboardAdmin
    @GetMapping("/{id}")
    public ResponseEntity<AppSourceDetailsDto> getAppSourceDetails(
            @Parameter(name = "id", description = "App Source UUID", required = true) @PathVariable UUID id) {
        return new ResponseEntity<>(this.appSourceService.getAppSource(id), HttpStatus.OK);
    }

    @Operation(summary = "Updates the details for an App Source")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AppSourceDetailsDto.class))),
            @ApiResponse(responseCode = "404",
                    description = "Requested App Source not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
                    )),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
            		content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
					)),
            @ApiResponse(responseCode = "400",
                    description = "Malformed Request Body",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
                    ))
    })
    @PreAuthorizeDashboardAdmin
    @PutMapping("/{id}")
    public ResponseEntity<AppSourceDetailsDto> updateAppSourceDetails(
            @Parameter(name = "id", description = "App Source id to update", required = true)
                    @PathVariable UUID id,
            @Parameter(description = "App Source Dto", required = true)
            @Valid @RequestBody AppSourceDetailsDto appSourceDetailsDto) {
        return new ResponseEntity<>(this.appSourceService.updateAppSource(id, appSourceDetailsDto), HttpStatus.OK);
    }

    @Operation(summary = "Deletes the App Source",
            description = "Requester has to have DASHBOARD_ADMIN rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "App Source Removed OK",
                    content = @Content(schema = @Schema(implementation = AppSourceDetailsDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Id is malformed",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "App Source Id not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "No DASHBOARD_ADMIN privileges",
            		content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExceptionResponse.class)
					))
    })
    @PreAuthorizeDashboardAdmin
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteAppSource(
            @Parameter(name = "id", description = "App Source UUID", required = true) @PathVariable UUID id) {

        return new ResponseEntity<>(appSourceService.deleteAppSource(id), HttpStatus.OK);
    }
}
