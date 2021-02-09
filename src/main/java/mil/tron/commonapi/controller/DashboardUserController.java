package mil.tron.commonapi.controller;

import mil.tron.commonapi.service.DashboardUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api-prefix.v1}/dashboard-users")
public class DashboardUserController {

    DashboardUserService dashboardUserService;

    public DashboardUserController(DashboardUserService dashboardUserService) {
        this.dashboardUserService = dashboardUserService;
    }

    @GetMapping
    public ResponseEntity<Object> getHeaders() {
        return new ResponseEntity<>(dashboardUserService.getDashboardUsers(), HttpStatus.OK);
    }
}
