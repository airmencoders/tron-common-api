package mil.tron.commonapi.controller.documentspace;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.minio.IfMinioEnabledOnStagingIL4OrDevLocal;
import mil.tron.commonapi.annotation.response.WrappedEnvelopeResponse;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.annotation.security.PreAuthorizeOnlySSO;
import mil.tron.commonapi.dto.GenericStringArrayResponseWrapper;
import mil.tron.commonapi.dto.documentspace.*;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.documentspace.DocumentSpaceService;
import mil.tron.commonapi.service.documentspace.util.FilePathSpec;
import mil.tron.commonapi.service.documentspace.util.FilePathSpecWithContents;
import mil.tron.commonapi.validations.DocSpaceFolderOrFilenameValidator;
import org.apache.commons.io.FilenameUtils;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.resource.ResourceUrlProvider;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.security.Principal;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("${api-prefix.v2}" + DocumentSpaceController.ENDPOINT)
@IfMinioEnabledOnStagingIL4OrDevLocal
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

    @Operation(summary = "Retrieves all document spaces for the requesting user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                description = "Successful operation",
                content = @Content(schema = @Schema(implementation = DocumentSpaceResponseDtoResponseWrapper.class))),
            @ApiResponse(responseCode = "403",
            	description = "Forbidden (Requires DASHBOARD_ADMIN or DOCUMENT_SPACE_USER)",
                content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @WrappedEnvelopeResponse
    @PreAuthorize("@accessCheckDocumentSpace.hasDocumentSpaceAccess(authentication) and #principal != null")
	@GetMapping("/spaces")
    public ResponseEntity<List<DocumentSpaceResponseDto>> getSpaces(Principal principal) {
	    return new ResponseEntity<>(documentSpaceService.listSpaces(principal.getName()), HttpStatus.OK);
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
			@ApiResponse(responseCode = "204",
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

    @Operation(summary = "Gets the Document Space privileges of the requesting user")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
				description = "Successful operation",
                content = @Content(schema = @Schema(implementation = DocumentSpacePrivilegeDtoResponseWrapper.class))),
			@ApiResponse(responseCode = "404",
				description = "Not Found - space not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
	        	description = "Forbidden (Not authorized to this Document Space)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @WrappedEnvelopeResponse
    @PreAuthorize("isAuthenticated() and #principal != null")
    @GetMapping("/spaces/{id}/users/dashboard/privileges/self")
    public ResponseEntity<List<DocumentSpacePrivilegeDto>> getSelfDashboardUserPrivilegesForDocumentSpace(@PathVariable UUID id, Principal principal) {
    	return ResponseEntity.ok(documentSpaceService.getDashboardUserPrivilegesForDocumentSpace(id, principal.getName()));
    }

    @Operation(summary = "Sets the default Document Space privileges of the requesting user")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
				description = "Successful operation",
                content = @Content(schema = @Schema(implementation = DocumentSpacePrivilegeDtoResponseWrapper.class))),
			@ApiResponse(responseCode = "404",
				description = "Not Found - space not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
	        	description = "Forbidden (Not authorized to this Document Space)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @PreAuthorize("isAuthenticated() and #principal != null")
    @PatchMapping("/spaces/{id}/user/default")
    public ResponseEntity<Void>patchSelfDocumentSpaceDefault(@PathVariable UUID id, Principal principal) {
		documentSpaceService.setDashboardUserDefaultDocumentSpace(id, principal.getName());
		return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Removes one or more Dashboard User members from a Document Space", description = "Removes Dashboard Users from a Document Space and their privileges")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
				description = "Successful operation"),
			@ApiResponse(responseCode = "404",
				description = "Not Found - space not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizeOnlySSO
	@PreAuthorize("@accessCheckDocumentSpace.hasMembershipAccess(authentication, #id)")
	@DeleteMapping("/spaces/{id}/users/dashboard")
    public ResponseEntity<Object> removeUserFromDocumentSpace(
    		@PathVariable UUID id,
    		@Valid @RequestBody String[] emails) {
	    documentSpaceService.removeDashboardUserFromDocumentSpace(id, emails);
	    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(summary = "Adds multiple users to a Document Space", description = "Adds multiple users via a csv to a Document Space with specified privileges. Returns a list of any errors encountered.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
				description = "Successful or partially successful operation"),
			@ApiResponse(responseCode = "404",
				description = "Not Found - space not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
	        	description = "Forbidden (Requires Membership privilege to document space, or DASHBOARD_ADMIN)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @PreAuthorize("@accessCheckDocumentSpace.hasMembershipAccess(authentication, #id)")
    @PostMapping(value = "/spaces/{id}/batchUsers", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<List<String>> batchAddUserToDocumentSpace(
    		@PathVariable UUID id,
			@RequestPart(value = "file") MultipartFile file) {
		List<String> errorMessages = documentSpaceService.batchAddDashboardUserToDocumentSpace(id, file);

		return ResponseEntity.ok(errorMessages);

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
                                      @RequestParam(value = "path", defaultValue = "") String path,
                                      @RequestPart("file") MultipartFile file) {

		DocSpaceFolderOrFilenameValidator validator = new DocSpaceFolderOrFilenameValidator();
		if (!validator.isValid(file.getOriginalFilename(), null)) {
			throw new BadRequestException("Invalid filename");
		}

		documentSpaceService.uploadFile(id, path, file);
		 
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
	@GetMapping("/space/{id}/**")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable UUID id, HttpServletRequest request) {

		// only way it seems to get the rest-of-url into a variable..
		ResourceUrlProvider urlProvider = (ResourceUrlProvider) request
				.getAttribute(ResourceUrlProvider.class.getCanonicalName());
		String restOfUrl = urlProvider.getPathMatcher().extractPathWithinPattern(
				String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)),
				String.valueOf(request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)));

		String path = FilenameUtils.getPath(restOfUrl);
		String name = FilenameUtils.getName(restOfUrl);

        S3Object s3Data = documentSpaceService.getFile(id, path, name);
        ObjectMetadata s3Meta = s3Data.getObjectMetadata();
        
        var response = new InputStreamResource(s3Data.getObjectContent());
        
        return ResponseEntity
                .ok()
                .contentType(MediaType.valueOf(s3Meta.getContentType()))
                .headers(createDownloadHeaders(name))
                .body(response);
    }
    
    @Operation(summary = "Download chosen files from a chosen Document Space folder", description = "Downloads multiple files from the same folder into a zip file")
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
                                                               @RequestParam(value = "path", defaultValue = "") String path,
                                                               @RequestParam("files") Set<String> files) {
        StreamingResponseBody response = out -> documentSpaceService.downloadAndWriteCompressedFiles(id, path, files, out);

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
                       @RequestParam(value = "path", defaultValue = "") String path,
                       @RequestParam("file") String file) {
    	documentSpaceService.deleteFile(id, path, file);
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
    
    @Operation(summary = "Retrieves files from a space that the authenticated user has recently uploaded")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = DocumentDtoResponseWrapper.class))),
            @ApiResponse(responseCode = "404",
    				description = "Not Found - space not found",
    				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
	        	description = "Forbidden (Requires Read privilege to document space, or DASHBOARD_ADMIN)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PreAuthorize("@accessCheckDocumentSpace.hasReadAccess(authentication, #id) and #principal != null")
    @WrappedEnvelopeResponse
    @GetMapping("/spaces/{id}/files/recently-uploaded")
    public ResponseEntity<List<DocumentDto>> getRecentlyUploadedFilesByDocumentSpaceAndAuthenticatedUser(
    		@PathVariable UUID id,
			@Parameter(name = "size", description = "the amount of entries to retrieve", required = false)
				@RequestParam(name = "size", required = false) Integer size,
				Principal principal) {
        return ResponseEntity
        		.ok(documentSpaceService.getRecentlyUploadedFilesByDocumentSpaceAndAuthUser(id, principal.getName(), size));
    }
    
    @Operation(summary = "Retrieves files from all spaces that the authenticated user has recently uploaded")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = RecentDocumentDtoResponseWrapper.class)))
    })
    @PreAuthorize("isAuthenticated() and #principal != null")
    @WrappedEnvelopeResponse
    @GetMapping("/spaces/files/recently-uploaded")
    public ResponseEntity<Slice<RecentDocumentDto>> getRecentlyUploadedFilesByAuthenticatedUser(
    		@ParameterObject Pageable pageable,
			Principal principal) {
        return ResponseEntity
        		.ok(documentSpaceService.getRecentlyUploadedFilesByAuthUser(principal.getName(), pageable));
    }
    
    @Operation(summary = "Download a filefrom a Document Space", 
    		description = "Download a single file (folder now allowed) from a Document Space by parent folder id and filename")
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
	@GetMapping("/spaces/{id}/folder/{parentFolderId}/file/{filename}")
    public ResponseEntity<InputStreamResource> downloadFileBySpaceAndParent(
	    		@PathVariable UUID id,
	    		@PathVariable UUID parentFolderId,
	    		@PathVariable String filename
    		) {

        S3Object s3Data = documentSpaceService.getFile(id, parentFolderId, filename);
        ObjectMetadata s3Meta = s3Data.getObjectMetadata();
        
        var response = new InputStreamResource(s3Data.getObjectContent());
        
        return ResponseEntity
                .ok()
                .contentType(MediaType.valueOf(s3Meta.getContentType()))
                .headers(createDownloadHeaders(filename))
                .body(response);
    }
    
    @Operation(summary = "Delete from a Document Space", description = "Delete a single file from a Document Space by parent folder id and filename")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204", 
				description = "Successful operation"),
			@ApiResponse(responseCode = "404",
				description = "Not Found - file does not exist",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
	        	description = "Forbidden (Requires Read privilege to document space, or DASHBOARD_ADMIN)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @PreAuthorize("@accessCheckDocumentSpace.hasReadAccess(authentication, #id)")
	@DeleteMapping("/spaces/{id}/folder/{parentFolderId}/file/{filename}")
    public ResponseEntity<Void> deleteFileBySpaceAndParent(
	    		@PathVariable UUID id,
	    		@PathVariable UUID parentFolderId,
	    		@PathVariable String filename
    		) {

        documentSpaceService.deleteFile(id, parentFolderId, filename);
        
        return ResponseEntity
                .noContent()
                .build();
    }

	@Operation(summary = "Creates a new folder within a Document Space")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201",
					description = "Successful operation - folder created",
					content = @Content(schema = @Schema(implementation = FilePathSpec.class))),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space not found or part of supplied path does not exist",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden (Requires Read privilege to document space, or DASHBOARD_ADMIN)",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "409",
					description = "Folder with provided name already exists at that path in this document space",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
	})
	@PreAuthorize("@accessCheckDocumentSpace.hasWriteAccess(authentication, #id)")
	@PostMapping("/spaces/{id}/folders")
	public ResponseEntity<FilePathSpec> createFolder(@PathVariable UUID id, @RequestBody @Valid DocumentSpaceCreateFolderDto dto) {
		return new ResponseEntity<>(documentSpaceService.createFolder(id, dto.getPath(), dto.getFolderName()), HttpStatus.CREATED);
	}

	@Operation(summary = "Deletes a folder at a given path", description = "Deletes a folder and all its files and subfolders.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful deletion",
					content = @Content(schema = @Schema(implementation = DocumentSpaceCreateFolderDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space not found or part of supplied path does not exist",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden (Requires Read privilege to document space, or DASHBOARD_ADMIN)",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorize("@accessCheckDocumentSpace.hasWriteAccess(authentication, #id)")
	@DeleteMapping("/spaces/{id}/folders")
	public ResponseEntity<Void> deleteFolder(@PathVariable UUID id, @RequestBody @Valid DocumentSpacePathDto dto) {
		documentSpaceService.deleteFolder(id, dto.getPath());
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@Operation(summary = "List folders and files at given path", description = "Lists folders and files contained " +
			"within given folder path - one level deep (does not recurse into any sub-folders)")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation - folder created",
					content = @Content(schema = @Schema(implementation = S3PaginationDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space not found or part of supplied path does not exist",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden (Requires Read privilege to document space, or DASHBOARD_ADMIN)",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorize("@accessCheckDocumentSpace.hasReadAccess(authentication, #id)")
	@GetMapping("/spaces/{id}/contents")
	public ResponseEntity<S3PaginationDto> dumpContentsAtPath(@PathVariable UUID id,
													   @RequestParam(value = "path", defaultValue = "") String path) {
		FilePathSpecWithContents contents = documentSpaceService.getFolderContents(id, path);
		List<DocumentDto> filesAndFolders = contents.getEntries().stream().map(entry -> 
			DocumentDto.builder()
					.path(path)
					.size(entry.getSize())
					.spaceId(entry.getDocumentSpaceId().toString())
					.isFolder(entry.isFolder())
					.key(FilenameUtils.getName(entry.getItemName()))
					.lastModifiedBy(entry.getLastModifiedBy() != null ? entry.getLastModifiedBy() : entry.getCreatedBy())
					.lastModifiedDate(entry.getLastModifiedOn() != null ? entry.getLastModifiedOn() : entry.getCreatedOn())
					.build()
		).collect(Collectors.toList());
		
		S3PaginationDto dto = S3PaginationDto.builder()
				.size(filesAndFolders.size())
				.documents(filesAndFolders)
				.currentContinuationToken(null)
				.nextContinuationToken(null)
				.totalElements(filesAndFolders.size())
				.build();
		return new ResponseEntity<>(dto, HttpStatus.OK);
	}

	@Operation(summary = "Renames a folder at a given path")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful rename",
					content = @Content(schema = @Schema(implementation = DocumentSpaceRenameFolderDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space not found or part of supplied path does not exist",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden (Requires Read privilege to document space, or DASHBOARD_ADMIN)",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "409",
					description = "Name conflict",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorize("@accessCheckDocumentSpace.hasWriteAccess(authentication, #id)")
	@PutMapping("/spaces/{id}/folders")
	public ResponseEntity<Void> renameFolder(@PathVariable UUID id, @RequestBody @Valid DocumentSpaceRenameFolderDto dto) {
		documentSpaceService.renameFolder(id, dto.getExistingFolderPath(), dto.getNewName());
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@Operation(summary = "Deletes selected item(s) from a Document Space", description = "Deletes selected files/folder from a Document Space")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation - returns list of items that were not able to be deleted",
					content = @Content(schema = @Schema(implementation = GenericStringArrayResponseWrapper.class))),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space not found, file not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden (Requires Write privilege to document space, or DASHBOARD_ADMIN)",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorize("@accessCheckDocumentSpace.hasWriteAccess(authentication, #id)")
	@WrappedEnvelopeResponse
	@DeleteMapping("/spaces/{id}/delete")
	public ResponseEntity<Object> deleteItems(@PathVariable UUID id, @Valid @RequestBody DocumentSpaceDeleteItemsDto dto) {
		return new ResponseEntity<>(documentSpaceService.deleteItems(id, dto.getCurrentPath(), dto.getItemsToDelete()), HttpStatus.OK);
	}
}
