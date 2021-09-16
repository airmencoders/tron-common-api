package mil.tron.commonapi.controller.documentspace;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.response.WrappedEnvelopeResponse;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.documentspace.DocumentDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceInfoDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceInfoDtoResponseWrapper;
import mil.tron.commonapi.service.documentspace.DocumentSpaceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("${api-prefix.v2}" + DocumentSpaceController.ENDPOINT)
@PreAuthorizeDashboardAdmin
@ConditionalOnProperty(value = "minio.enabled", havingValue = "true")
public class DocumentSpaceController {
	protected static final String ENDPOINT = "/document-space";
	
	public static final Pattern DOCUMENT_SPACE_PATTERN = Pattern.compile(String.format("\\/v[\\d]\\%s", ENDPOINT));
	
	private final DocumentSpaceService documentSpaceService;
	
	public DocumentSpaceController(DocumentSpaceService documentSpaceService) {
		this.documentSpaceService = documentSpaceService;
	}
	
	private HttpHeaders createDownloadHeaders(String filename) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Content-disposition", "attachment; filename=\"" + filename + "\"");
		
		return headers;
	}

    @Operation(summary = "Retrieves all document space names")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = DocumentSpaceInfoDtoResponseWrapper.class)))
    })
    @WrappedEnvelopeResponse
	@GetMapping("/spaces")
    public ResponseEntity<Object> getSpaces() {
	    return new ResponseEntity<>(documentSpaceService.listSpaces(), HttpStatus.OK);
    }

	@PostMapping("/spaces")
    public ResponseEntity<Object> createSpace(@Valid @RequestBody DocumentSpaceInfoDto dto) {
	    return new ResponseEntity<>(documentSpaceService.createSpace(dto), HttpStatus.CREATED);
    }

    @DeleteMapping("/spaces/{name}")
    public ResponseEntity<Object> deleteSpace(@PathVariable String name) {
	    documentSpaceService.deleteSpace(name);
	    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

	@PostMapping("/files/{space}")
    public Map<String, String> upload(@PathVariable String space,
                                      @RequestPart("file") MultipartFile file) {

		documentSpaceService.uploadFile(space, file);
		 
        Map<String, String> result = new HashMap<>();
        result.put("key", file.getOriginalFilename());
        return result;
    }

    @GetMapping("/file/{space}/{keyName}")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable String space,
                                                            @PathVariable("keyName") String keyName) {

        S3Object s3Data = documentSpaceService.downloadFile(space, keyName);
        ObjectMetadata s3Meta = s3Data.getObjectMetadata();
        
        var response = new InputStreamResource(s3Data.getObjectContent());
        
        return ResponseEntity
                .ok()
                .contentType(MediaType.valueOf(s3Meta.getContentType()))
                .headers(createDownloadHeaders(keyName))
                .body(response);
    }
    
    @GetMapping("/files/{space}/{keyNames}")
    public ResponseEntity<StreamingResponseBody> downloadFiles(@PathVariable String space,
                                                               @PathVariable("keyNames") Set<String> keyNames) {
        StreamingResponseBody response = out -> documentSpaceService.downloadAndWriteCompressedFiles(space, keyNames, out);
        
        return ResponseEntity
                .ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .headers(createDownloadHeaders("files.zip"))
                .body(response);
    }
    
    @DeleteMapping("/files/{space}/{keyName}")
    public void delete(@PathVariable String space,
                       @PathVariable("keyName") String keyName) {
    	documentSpaceService.deleteFile(space, keyName);
    }


    @GetMapping("/files/{space}")
    public List<DocumentDto> listObjects(@PathVariable String space) {
        return documentSpaceService.listFiles(space);
    }
}
