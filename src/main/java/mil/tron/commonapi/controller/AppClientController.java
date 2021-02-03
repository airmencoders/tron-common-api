package mil.tron.commonapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.service.AppClientUserService;

@RestController
@RequestMapping("${api-prefix.v1}/app-client")
@PreAuthorizeDashboardAdmin
public class AppClientController {
	
	AppClientUserService userService;
	
	public AppClientController(AppClientUserService userService) {
		this.userService = userService;
	}
	
	@GetMapping
	public ResponseEntity<Object> getUsers() {
		return new ResponseEntity<>(userService.getAppClientUsers(), HttpStatus.OK);
	}
}
