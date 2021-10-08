package mil.tron.commonapi.controller.documentspace;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.response.WrappedEnvelopeResponse;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceRequestDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceResponseDtoResponseWrapper;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceResponseDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceDashboardMemberRequestDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceDashboardMemberResponseDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceDashboardMemberResponseDtoResponseWrapper;
import mil.tron.commonapi.dto.documentspace.S3PaginationDto;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.documentspace.DocumentSpaceService;

import org.springdoc.api.annotations.ParameterObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.validation.Valid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("${api-prefix.v2}" + DocumentSpaceController.ENDPOINT)
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

    @Operation(summary = "Retrieves all document spaces")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                description = "Successful operation",
                content = @Content(schema = @Schema(implementation = DocumentSpaceResponseDtoResponseWrapper.class))),
            @ApiResponse(responseCode = "403",
            	description = "Forbidden (Requires DASHBOARD_ADMIN privilege)",
                content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @WrappedEnvelopeResponse
    @PreAuthorizeDashboardAdmin
	@GetMapping("/spaces")
    public ResponseEntity<List<DocumentSpaceResponseDto>> getSpaces() {
	    return new ResponseEntity<>(documentSpaceService.listSpaces(), HttpStatus.OK);
    }

    @Operation(summary = "Creates a Document Space", description = "Creates a Document Space")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation",
				content = @Content(schema = @Schema(implementation = DocumentSpaceResponseDto.class))),
			@ApiResponse(responseCode = "409",
				description = "Conflict - Space already exists",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
	        	description = "Forbidden (Requires DASHBOARD_ADMIN privilege)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @PreAuthorizeDashboardAdmin
	@PostMapping("/spaces")
    public ResponseEntity<DocumentSpaceResponseDto> createSpace(@Valid @RequestBody DocumentSpaceRequestDto dto) {
	    return new ResponseEntity<>(documentSpaceService.createSpace(dto), HttpStatus.CREATED);
    }

    @Operation(summary = "Deletes a Document Space", description = "Deletes a Document Space")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation"),
			@ApiResponse(responseCode = "404",
				description = "Not Found - space not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
	        	description = "Forbidden (Requires DASHBOARD_ADMIN privilege)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @PreAuthorizeDashboardAdmin
    @DeleteMapping("/spaces/{id}")
    public ResponseEntity<Object> deleteSpace(@PathVariable UUID id) {
	    documentSpaceService.deleteSpace(id);
	    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
    
    @Operation(summary = "Adds a user to a Document Space", description = "Adds a user to a Document Space with specified privileges")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation"),
			@ApiResponse(responseCode = "404",
				description = "Not Found - space not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
	        	description = "Forbidden (Requires Membership privilege to document space, or DASHBOARD_ADMIN)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @PreAuthorize("@accessCheckDocumentSpace.hasMembershipAccess(authentication, #id)")
    @PostMapping("/spaces/{id}/users")
    public ResponseEntity<Object> addUserToDocumentSpace(
    		@PathVariable UUID id,
    		@Valid @RequestBody DocumentSpaceDashboardMemberRequestDto dto) {
	    documentSpaceService.addDashboardUserToDocumentSpace(id, dto);
	    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
    
    @Operation(summary = "Gets the members for a Document Space", description = "Gets members for a Document Space. Pagination enabled.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation",
                content = @Content(schema = @Schema(implementation = DocumentSpaceDashboardMemberResponseDtoResponseWrapper.class))),
			@ApiResponse(responseCode = "404",
				description = "Not Found - space not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
	        	description = "Forbidden (Requires Membership privilege to document space, or DASHBOARD_ADMIN)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @WrappedEnvelopeResponse
    @PreAuthorize("@accessCheckDocumentSpace.hasMembershipAccess(authentication, #id)")
    @GetMapping("/spaces/{id}/users/dashboard")
    public ResponseEntity<Page<DocumentSpaceDashboardMemberResponseDto>> getDashboardUsersForDocumentSpace(
    		@PathVariable UUID id,
    		@ParameterObject Pageable pageable) {
    	return ResponseEntity.ok(documentSpaceService.getDashboardUsersForDocumentSpace(id, pageable));
    }

    @Operation(summary = "Uploads a file to a Document Space", description = "Uploads a file to a Document Space")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation"),
			@ApiResponse(responseCode = "404",
				description = "Not Found - space not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
	        	description = "Forbidden (Requires Write privilege to document space, or DASHBOARD_ADMIN)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @PreAuthorize("@accessCheckDocumentSpace.hasWriteAccess(authentication, #id)")
	@PostMapping(value = "/spaces/{id}/files/upload", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public Map<String, String> upload(@PathVariable UUID id,
                                      @RequestPart("file") MultipartFile file) {

		documentSpaceService.uploadFile(id, file);
		 
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
			@ApiResponse(responseCode = "403",
	        	description = "Forbidden (Requires Read privilege to document space, or DASHBOARD_ADMIN)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @PreAuthorize("@accessCheckDocumentSpace.hasReadAccess(authentication, #id)")
    @GetMapping("/spaces/{id}/files/download/single")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable UUID id,
                                                            @RequestParam("file") String file) {

        S3Object s3Data = documentSpaceService.downloadFile(id, file);
        ObjectMetadata s3Meta = s3Data.getObjectMetadata();
        
        var response = new InputStreamResource(s3Data.getObjectContent());
        
        return ResponseEntity
                .ok()
                .contentType(MediaType.valueOf(s3Meta.getContentType()))
                .headers(createDownloadHeaders(file))
                .body(response);
    }
    
    @Operation(summary = "Download multiple files from a Document Space", description = "Downloads multiple files from a space as a zip file")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation"),
			@ApiResponse(responseCode = "404",
				description = "Not Found - space not found, file(s) not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
	        	description = "Forbidden (Requires Read privilege to document space, or DASHBOARD_ADMIN)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @PreAuthorize("@accessCheckDocumentSpace.hasReadAccess(authentication, #id)")
    @GetMapping("/spaces/{id}/files/download")
    public ResponseEntity<StreamingResponseBody> downloadFiles(@PathVariable UUID id,
                                                               @RequestParam("files") Set<String> files) {
        StreamingResponseBody response = out -> documentSpaceService.downloadAndWriteCompressedFiles(id, files, out);
        
        return ResponseEntity
                .ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .headers(createDownloadHeaders("files.zip"))
                .body(response);
    }
    
    @Operation(summary = "Download all files from a Document Space", description = "Downloads all files from a space as a zip file")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation"),
			@ApiResponse(responseCode = "404",
				description = "Not Found - space not found, file(s) not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
	        	description = "Forbidden (Requires Read privilege to document space, or DASHBOARD_ADMIN)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @PreAuthorize("@accessCheckDocumentSpace.hasReadAccess(authentication, #id)")
    @GetMapping("/spaces/{id}/files/download/all")
    public ResponseEntity<StreamingResponseBody> downloadAllFilesInSpace(@PathVariable UUID id) {
        StreamingResponseBody response = out -> documentSpaceService.downloadAllInSpaceAndCompress(id, out);
        return ResponseEntity
                .ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .headers(createDownloadHeaders("files.zip"))
                .body(response);
    }
    
    @Operation(summary = "Deletes a file from a Document Space", description = "Deletes file from a space")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
				description = "Successful operation"),
			@ApiResponse(responseCode = "404",
				description = "Not Found - space not found, file not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
	        	description = "Forbidden (Requires Write privilege to document space, or DASHBOARD_ADMIN)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @PreAuthorize("@accessCheckDocumentSpace.hasWriteAccess(authentication, #id)")
    @DeleteMapping("/spaces/{id}/files/delete")
    public ResponseEntity<Object> delete(@PathVariable UUID id,
                       @RequestParam("file") String file) {
    	documentSpaceService.deleteFile(id, file);
    	return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(summary = "Retrieves files from a space", description = "Gets files from a space. This is not a download")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = S3PaginationDto.class))),
            @ApiResponse(responseCode = "404",
    				description = "Not Found - space not found",
    				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
	        	description = "Forbidden (Requires Read privilege to document space, or DASHBOARD_ADMIN)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PreAuthorize("@accessCheckDocumentSpace.hasReadAccess(authentication, #id)")
    @GetMapping("/spaces/{id}/files")
    public ResponseEntity<S3PaginationDto> listObjects(
    		@PathVariable UUID id, 
    		@Parameter(name = "continuation", description = "the continuation token", required = false)
				@RequestParam(name = "continuation", required = false) String continuation,
			@Parameter(name = "limit", description = "page limit", required = false)
				@RequestParam(name = "limit", required = false) Integer limit) {
        return ResponseEntity
        		.ok(documentSpaceService.listFiles(id, continuation, limit));
    }
}
