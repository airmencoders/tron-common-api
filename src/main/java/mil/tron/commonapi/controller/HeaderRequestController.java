package mil.tron.commonapi.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api-prefix.v1}/list-request-headers")
public class HeaderRequestController {
	@GetMapping
	public ResponseEntity<Object> getHeaders(@RequestHeader MultiValueMap<String, String> headers) {
		Map<String, String> mappedHeaders = new HashMap<>();
		
		headers.forEach((k, v) -> {
			String values = v.stream().collect(Collectors.joining("|"));
			mappedHeaders.put(k, values);
		});
		
		return new ResponseEntity<>(mappedHeaders, HttpStatus.OK);
	}
}
