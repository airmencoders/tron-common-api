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
import mil.tron.commonapi.dto.documentspace.DocumentDtoResponseWrapper;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceInfoDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceInfoDtoResponseWrapper;
import mil.tron.commonapi.exception.ExceptionResponse;
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

    @Operation(summary = "Creates a Document Space", description = "Creates a Document Space")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation",
				content = @Content(schema = @Schema(implementation = DocumentSpaceInfoDto.class))),
			@ApiResponse(responseCode = "409",
				description = "Conflict - Space already exists",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
				description = "Bad Request - Bad space name",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PostMapping("/spaces")
    public ResponseEntity<DocumentSpaceInfoDto> createSpace(@Valid @RequestBody DocumentSpaceInfoDto dto) {
	    return new ResponseEntity<>(documentSpaceService.createSpace(dto), HttpStatus.CREATED);
    }

    @Operation(summary = "Deletes a Document Space", description = "Deletes a Document Space")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation"),
			@ApiResponse(responseCode = "404",
				description = "Not Found - space not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
				description = "Bad Request - Bad space name",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @DeleteMapping("/spaces/{name}")
    public ResponseEntity<Object> deleteSpace(@PathVariable String name) {
	    documentSpaceService.deleteSpace(name);
	    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(summary = "Uploads a file to a Document Space", description = "Uploads a file to a Document Space")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation"),
			@ApiResponse(responseCode = "404",
				description = "Not Found - space not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
				description = "Bad Request - Bad space name",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PostMapping("/files/{space}")
    public Map<String, String> upload(@PathVariable String space,
                                      @RequestPart("file") MultipartFile file) {

		documentSpaceService.uploadFile(space, file);
		 
        Map<String, String> result = new HashMap<>();
        result.put("key", file.getOriginalFilename());
        return result;
    }

    @Operation(summary = "Download from a Document Space", description = "Download a single file from a Document Space")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation"),
			@ApiResponse(responseCode = "404",
				description = "Not Found - space not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
				description = "Bad Request - Bad space name",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
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
    
    @Operation(summary = "Download multiple files from a Document Space", description = "Downloads multiple files from a space as a zip file")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation"),
			@ApiResponse(responseCode = "404",
				description = "Not Found - space not found, file(s) not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
				description = "Bad Request - Bad space name",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
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
    
    @Operation(summary = "Deletes from a Document Space", description = "Deletes file from a space")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation"),
			@ApiResponse(responseCode = "404",
				description = "Not Found - space not found, file not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
				description = "Bad Request - Bad space name",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @DeleteMapping("/files/{space}/{keyName}")
    public ResponseEntity<Object> delete(@PathVariable String space,
                       @PathVariable("keyName") String keyName) {
    	documentSpaceService.deleteFile(space, keyName);
    	return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }


    @Operation(summary = "Retrieves all file details from a space", description = "Gets all file details from a space. This is not a download")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = DocumentDtoResponseWrapper.class))),
            @ApiResponse(responseCode = "404",
    				description = "Not Found - space not found",
    				content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @WrappedEnvelopeResponse
    @GetMapping("/files/{space}")
    public ResponseEntity<List<DocumentDto>> listObjects(@PathVariable String space) {
        return ResponseEntity
        		.ok(documentSpaceService.listFiles(space));
    }
}
