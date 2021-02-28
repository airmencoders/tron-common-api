package mil.tron.commonapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardUser;
import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.DashboardUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("${api-prefix.v1}/dashboard-users")
public class DashboardUserController {

    DashboardUserService dashboardUserService;

    public DashboardUserController(DashboardUserService dashboardUserService) {
        this.dashboardUserService = dashboardUserService;
    }

    @PreAuthorizeDashboardUser
    @GetMapping("")
    public ResponseEntity<Iterable<DashboardUserDto>> getAllDashboardUsers() {
        return new ResponseEntity<>(dashboardUserService.getAllDashboardUsersDto(), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves a dashboard user by ID", description = "Retrieves a dashboard user by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = PersonDto.class))),
            @ApiResponse(responseCode = "404",
                    description = "Resource not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Bad request",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PreAuthorizeDashboardUser
    @GetMapping(value = "/{id}")
    public ResponseEntity<DashboardUserDto> getDashboardUser(
            @Parameter(description = "Dashboard User ID to retrieve", required = true) @PathVariable("id") UUID id) {

        DashboardUserDto dashboardUser = dashboardUserService.getDashboardUserDto(id);
        return new ResponseEntity<>(dashboardUser, HttpStatus.OK);
    }

    @PreAuthorizeDashboardAdmin
    @PostMapping("")
    public ResponseEntity<DashboardUserDto> addDashboardUser(@Parameter(description = "Dashboard user to add", required = true) @Valid @RequestBody DashboardUserDto dashboardUser) {
        return new ResponseEntity<>(dashboardUserService.createDashboardUserDto(dashboardUser), HttpStatus.CREATED);
    }

    @Operation(summary = "Updates an existing dashboard user", description = "Updates an existing dashboard user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = PersonDto.class))),
            @ApiResponse(responseCode = "404",
                    description = "Resource not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PreAuthorizeDashboardAdmin
    @PutMapping(value = "/{id}")
    public ResponseEntity<DashboardUserDto> updateDashboardUser(
            @Parameter(description = "Dashboard User ID to update", required = true) @PathVariable("id") UUID id,
            @Parameter(description = "Updated person", required = true) @Valid @RequestBody DashboardUserDto dashboardUserDto) {

        DashboardUserDto updatedDashboardUser = dashboardUserService.updateDashboardUserDto(id, dashboardUserDto);
        return new ResponseEntity<>(updatedDashboardUser, HttpStatus.OK);
    }

    @Operation(summary = "Deletes an existing person", description = "Deletes an existing person")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204",
                    description = "Successful operation",
                    content = @Content),
            @ApiResponse(responseCode = "404",
                    description = "Resource not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PreAuthorizeDashboardAdmin
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Object> deleteDashboardUser(
            @Parameter(description = "Dashboard ID to delete", required = true) @PathVariable("id") UUID id) {
        dashboardUserService.deleteDashboardUser(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
