package mil.tron.commonapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import mil.tron.commonapi.repository.AppClientUserRespository;

@RestController
@RequestMapping("${api-prefix.v1}/app-client")
public class AppClientController {
	
	AppClientUserRespository repository;
	
	public AppClientController(AppClientUserRespository repository) {
		this.repository = repository;
	}
	
	@GetMapping
	public ResponseEntity<Object> getHeaders() {
		return new ResponseEntity<>(repository.findAll(), HttpStatus.OK);
	}
}
