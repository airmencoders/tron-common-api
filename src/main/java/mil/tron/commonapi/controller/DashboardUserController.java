package mil.tron.commonapi.controller;

import io.swagger.v3.oas.annotations.Parameter;
import mil.tron.commonapi.annotation.security.PreAuthorizeWrite;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.service.DashboardUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("${api-prefix.v1}/dashboard-users")
public class DashboardUserController {

    DashboardUserService dashboardUserService;

    public DashboardUserController(DashboardUserService dashboardUserService) {
        this.dashboardUserService = dashboardUserService;
    }

//    @GetMapping
//    public ResponseEntity<DashboardUser> getHeaders() {
//        return new ResponseEntity<>(dashboardUserService.getDashboardUsers(), HttpStatus.OK);
//    }
    @GetMapping("")
    public ResponseEntity<Iterable<DashboardUser>> getAllDashboardUsers() {
        return new ResponseEntity<>(dashboardUserService.getAllDashboardUsers(), HttpStatus.OK);
    }

    @PreAuthorizeWrite
    @PostMapping("")
    public ResponseEntity<DashboardUser> addDashboardUser(@Parameter(description = "Dashboard user to add", required = true) @Valid @RequestBody DashboardUser dashboardUser) {
        return new ResponseEntity<>(dashboardUserService.createDashboardUser(dashboardUser), HttpStatus.CREATED);
    }
}
