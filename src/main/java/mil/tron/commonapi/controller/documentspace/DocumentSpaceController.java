package mil.tron.commonapi.controller.documentspace;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.minio.IfMinioEnabledOnIL4OrDevLocal;
import mil.tron.commonapi.annotation.response.WrappedEnvelopeResponse;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.annotation.security.PreAuthorizeOnlySSO;
import mil.tron.commonapi.dto.appclient.AppClientSummaryDto;
import mil.tron.commonapi.dto.appclient.AppClientSummaryDtoResponseWrapper;
import mil.tron.commonapi.dto.documentspace.*;
import mil.tron.commonapi.dto.documentspace.mobile.DocumentMobileDto;
import mil.tron.commonapi.dto.documentspace.mobile.DocumentMobileDtoResponseWrapper;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.service.documentspace.DocumentSpaceFileSystemService;
import mil.tron.commonapi.service.documentspace.DocumentSpaceService;
import mil.tron.commonapi.service.documentspace.DocumentSpaceUserCollectionService;
import mil.tron.commonapi.service.documentspace.util.FilePathSpec;
import mil.tron.commonapi.service.documentspace.util.FilePathSpecWithContents;
import org.apache.commons.io.FilenameUtils;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.resource.ResourceUrlProvider;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("${api-prefix.v2}" + DocumentSpaceController.ENDPOINT)
@IfMinioEnabledOnIL4OrDevLocal
public class DocumentSpaceController {
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMMyyyyHHmm");

	protected static final String ENDPOINT = "/document-space";
	
	public static final Pattern DOCUMENT_SPACE_PATTERN = Pattern.compile(String.format("\\/v[\\d]\\%s", ENDPOINT));
	
	private final DocumentSpaceService documentSpaceService;

	private final DocumentSpaceUserCollectionService documentSpaceUserCollectionService;

	private final DocumentSpaceFileSystemService documentSpaceFileSystemService;

	public DocumentSpaceController(DocumentSpaceService documentSpaceService, DocumentSpaceUserCollectionService documentSpaceUserCollectionService, DocumentSpaceFileSystemService documentSpaceFileSystemService) {
		this.documentSpaceService = documentSpaceService;
		this.documentSpaceUserCollectionService = documentSpaceUserCollectionService;
		this.documentSpaceFileSystemService = documentSpaceFileSystemService;
	}

	// static helper used for file download headers -- used by the webdav controller also
	public static HttpHeaders createDownloadHeaders(String filename) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Content-disposition", "attachment; filename=\"" + filename + "\"");
		
		return headers;
	}

	private HttpHeaders createPreviewHeaders(String filename) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Content-disposition", "inline; filename=\"" + filename + "\"");
		
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
				description = "Successful operation"),
			@ApiResponse(responseCode = "404",
				description = "Not Found - space not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
	        	description = "Forbidden (Not authorized to this Document Space)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @PreAuthorize("(hasAuthority('DASHBOARD_ADMIN') || @accessCheckDocumentSpace.hasReadAccess(authentication, #id)) and isAuthenticated() and #principal != null")
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

    @Operation(summary = "Uploads a file to a Document Space", description = "Uploads a file to a Document Space. " +
			"API will attempt to use the `Last-Modified` date (formatted as long epoch date) in the header in order to keep" +
			" the uploaded copy's modified date to track its origin.  If that header is not given, then current date/time will be used.")
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
                                      HttpServletRequest request,
                                      @RequestParam(value = "path", defaultValue = "") String path,
                                      @RequestPart("file") MultipartFile file) {

		// attempt to preserve the last modified date the client sent us (if it did) in the Last-Modified header
		Date lastModified = new Date();
		if (request.getHeader("last-modified") != null) {
			try {
				lastModified = new Date(Long.parseLong(request.getHeader("Last-Modified")));
			} catch (NumberFormatException e) {
				lastModified = new Date();
			}
		}
		documentSpaceService.uploadFile(id, path, file, lastModified);
		 
        Map<String, String> result = new HashMap<>();
        result.put("key", file.getOriginalFilename());
        return result;
    }

	@Operation(summary = "Renames a file")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful operation"),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden (Requires Write privilege to document space, or DASHBOARD_ADMIN)",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorize("@accessCheckDocumentSpace.hasWriteAccess(authentication, #id)")
	@PostMapping(value = "/spaces/{id}/files/rename")
	public ResponseEntity<Object> renameFile(@PathVariable UUID id,
									  @Valid @RequestBody DocumentSpaceRenameFileDto renameFileDto) {

		documentSpaceService.renameFile(id, renameFileDto.getFilePath(), renameFileDto.getExistingFilename(), renameFileDto.getNewName());
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
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
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable UUID id,
															@RequestParam(value = "download", required = false) boolean isDownload,
															HttpServletRequest request,
															Authentication authentication) {

		// only way it seems to get the rest-of-url into a variable..
		ResourceUrlProvider urlProvider = (ResourceUrlProvider) request
				.getAttribute(ResourceUrlProvider.class.getCanonicalName());
		String restOfUrl = urlProvider.getPathMatcher().extractPathWithinPattern(
				String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)),
				String.valueOf(request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)));

		String path = FilenameUtils.getPath(restOfUrl);
		String name = FilenameUtils.getName(restOfUrl);

        S3Object s3Data = documentSpaceService.getFile(id, path, name, authentication.getName());
        ObjectMetadata s3Meta = s3Data.getObjectMetadata();
        
        var response = new InputStreamResource(s3Data.getObjectContent());
        
        return ResponseEntity
                .ok()
                .contentType(MediaType.valueOf(s3Meta.getContentType()))
                .headers(isDownload ? createDownloadHeaders(name) : createPreviewHeaders(name))
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
                                                               @RequestParam("files") Set<String> files,
                                                               Authentication authentication) {

        StreamingResponseBody response = out -> documentSpaceService.downloadAndWriteCompressedFiles(id, path, files, out, authentication.getName());

        String zipName = "files";

        if (files.size() == 1) {
        	String itemName = "";
        	for (Iterator<String> iter = files.iterator(); iter.hasNext();) {
        		itemName = iter.next();
        	}

        	if (documentSpaceFileSystemService.isFolder(id, path, itemName)) {
        		zipName = itemName;
        	}
        } else {
        	OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        	zipName = now.format(DATE_FORMAT);
        }

        return ResponseEntity
                .ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .headers(createDownloadHeaders(String.format("%s.zip", zipName)))
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
    	DocumentSpace documentSpace = documentSpaceService.getDocumentSpaceOrElseThrow(id);
        StreamingResponseBody response = out -> documentSpaceService.downloadAllInSpaceAndCompress(id, out);
        return ResponseEntity
                .ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .headers(createDownloadHeaders(String.format("%s.zip", documentSpace.getName())))
                .body(response);
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
 
    @Operation(summary = "Retrieves files from all spaces that the authenticated user has recently uploaded")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = RecentDocumentDtoResponseWrapper.class)))
    })
    @PreAuthorize("isAuthenticated() and #principal != null")
    @WrappedEnvelopeResponse
    @GetMapping("/spaces/files/recently-uploaded")
    public ResponseEntity<Page<RecentDocumentDto>> getRecentlyUploadedFilesByAuthenticatedUser(
    		@ParameterObject Pageable pageable,
			Principal principal) {
        return ResponseEntity
        		.ok(documentSpaceService.getRecentlyUploadedFilesByAuthUser(principal.getName(), pageable));
    }
    
    @Operation(summary = "Download a file from a Document Space", 
    		description = "Download a single file (folders not allowed) from a Document Space by parent folder id and filename")
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
	    		@PathVariable String filename,
	    		@RequestParam(value = "download", required = false) boolean isDownload,
	    		Authentication authentication
    		) {

        S3Object s3Data = documentSpaceService.getFile(id, parentFolderId, filename, authentication.getName());
        ObjectMetadata s3Meta = s3Data.getObjectMetadata();
        
        var response = new InputStreamResource(s3Data.getObjectContent());
        
        return ResponseEntity
                .ok()
                .contentType(MediaType.valueOf(s3Meta.getContentType()))
                .headers(isDownload ? createDownloadHeaders(filename) : createPreviewHeaders(filename))
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
	        	description = "Forbidden (Requires Write privilege to document space, or DASHBOARD_ADMIN)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @PreAuthorize("@accessCheckDocumentSpace.hasWriteAccess(authentication, #id)")
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
    
    @Operation(summary = "Archive a file/folder from a Document Space", description = "Archive a single file/folder from a Document Space by parent folder id and item name")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204", 
				description = "Successful operation"),
			@ApiResponse(responseCode = "404",
				description = "Not Found - file does not exist",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
	        	description = "Forbidden (Requires Write privilege to document space, or DASHBOARD_ADMIN)",
	            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
    @PreAuthorize("@accessCheckDocumentSpace.hasWriteAccess(authentication, #id)")
	@DeleteMapping("/spaces/{id}/folder/{parentFolderId}/file/{filename}/archive")
    public ResponseEntity<Void> deleteArchiveItemBySpaceAndParent(
	    		@PathVariable UUID id,
	    		@PathVariable UUID parentFolderId,
	    		@PathVariable String filename
    		) {
        documentSpaceService.archiveItem(id, parentFolderId, filename);
        
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

	@Operation(summary = "List folders and files at given path", description = "Lists folders and files contained " +
			"within given folder path - one level deep (does not recurse into any sub-folders)")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
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
		return new ResponseEntity<>(
				convertFileSystemEntriesToDto(path, documentSpaceService.getFolderContents(id, path)),
				HttpStatus.OK);
	}

	@Operation(summary = "Get info about a file(s) at given path",
			description = "Similar to usage of the Unix stat command")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation - provided path and doc space were valid",
					content = @Content(schema = @Schema(implementation = FilePathSpecWrapper.class))),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space not found or supplied path does not exist",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden (Requires Read privilege to document space, or DASHBOARD_ADMIN)",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@WrappedEnvelopeResponse
	@PreAuthorize("@accessCheckDocumentSpace.hasReadAccess(authentication, #id)")
	@PostMapping("/spaces/{id}/stat")
	public ResponseEntity<Object> statElementsAtPath(@PathVariable UUID id,
														  @Valid @RequestBody DocumentSpacePathItemsDto items) {

		List<FilePathSpec> fileSpecs = new ArrayList<>();

		// go thru and attempt to stat each item, for those not found, we just don't add
		//   to the response..
		for (String item : items.getItems()) {
			try {
				fileSpecs.add(documentSpaceService.statFileAtPath(id, items.getCurrentPath(), item));
			}
			catch (RecordNotFoundException ignored) { //NOSONAR
			}
		}
		return new ResponseEntity<>(fileSpecs, HttpStatus.OK);
	}

	@Operation(summary = "Get a folder's total size")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation - provided path and doc space were valid",
					content = @Content(schema = @Schema(implementation = DocumentSpaceFolderInfoDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space not found or supplied path does not exist",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden (Requires Read privilege to document space, or DASHBOARD_ADMIN)",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorize("@accessCheckDocumentSpace.hasReadAccess(authentication, #id)")
	@GetMapping("/spaces/{id}/folder-size")
	public ResponseEntity<Object> getFolderSize(@PathVariable UUID id,
													 @RequestParam(defaultValue = "") String path) {

		return new ResponseEntity<>(documentSpaceService.getFolderSize(id, path), HttpStatus.OK);
	}


	@Operation(summary = "List folders and files that are in Archived status", description = "Lists folders and files that are archived -" +
			"folders/sub-folders cannot be navigated into while in archived status - just folder name is shown")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = S3PaginationDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space not found or part of supplied path does not exist",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden (Requires Read privilege to document space, or DASHBOARD_ADMIN)",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorize("@accessCheckDocumentSpace.hasReadAccess(authentication, #id)")
	@GetMapping("/spaces/{id}/archived/contents")
	public ResponseEntity<S3PaginationDto> dumpArchivedContents(@PathVariable UUID id) {

		List<DocumentDto> filesAndFolders = documentSpaceService.getArchivedContents(id);
		return new ResponseEntity<>(
				 S3PaginationDto.builder()
						.size(filesAndFolders.size())
						.documents(filesAndFolders)
						.currentContinuationToken(null)
						.nextContinuationToken(null)
						.totalElements(filesAndFolders.size())
						.build(),
				HttpStatus.OK);
	}

	@Operation(summary = "Retrieves all archived files for the requesting user for spaces they have access to")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = S3PaginationDto.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden (Requires DASHBOARD_ADMIN or DOCUMENT_SPACE_USER)",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorize("isAuthenticated() and #principal != null")
	@GetMapping("/spaces/archived")
	public ResponseEntity<S3PaginationDto> getAllArchivedFilesForAuthUser(Principal principal) {
		List<DocumentDto> allArchivedItems = documentSpaceService.getAllArchivedContentsForAuthUser(principal);

		return new ResponseEntity<>(
				S3PaginationDto.builder()
						.size(allArchivedItems.size())
						.documents(allArchivedItems)
						.currentContinuationToken(null)
						.nextContinuationToken(null)
						.totalElements(allArchivedItems.size())
						.build(),
				HttpStatus.OK);
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
			@ApiResponse(responseCode = "204",
					description = "Successful operation"),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space not found, file not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden (Requires Write privilege to document space, or DASHBOARD_ADMIN)",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorize("@accessCheckDocumentSpace.hasWriteAccess(authentication, #id)")
	@DeleteMapping("/spaces/{id}/delete")
	public ResponseEntity<Object> deleteItems(@PathVariable UUID id, @Valid @RequestBody DocumentSpacePathItemsDto dto) {
		documentSpaceService.deleteItems(id, dto.getCurrentPath(), dto.getItems());
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@Operation(summary = "Archives selected item(s) from a Document Space", description = "Archives (soft-delete) selected files/folder from a Document Space")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful operation"),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space not found, file not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden (Requires Write privilege to document space, or DASHBOARD_ADMIN)",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorize("@accessCheckDocumentSpace.hasWriteAccess(authentication, #id)")
	@DeleteMapping("/spaces/{id}/archive")
	public ResponseEntity<Object> archiveItems(@PathVariable UUID id, @Valid @RequestBody DocumentSpaceArchiveItemsDto dto) {
		documentSpaceService.archiveItems(id, dto.getCurrentPath(), dto.getItemsToArchive());
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@Operation(summary = "Un-archives selected item(s) from a Document Space", description = "Un-archives selected files/folder from a Document Space")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful operation"),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space not found, file not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden (Requires Write privilege to document space, or DASHBOARD_ADMIN)",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorize("@accessCheckDocumentSpace.hasWriteAccess(authentication, #id)")
	@PostMapping("/spaces/{id}/unarchive")
	public ResponseEntity<Object> unArchiveItems(@PathVariable UUID id, @Valid @RequestBody DocumentSpaceUnArchiveItemsDto dto) {
		documentSpaceService.unArchiveItems(id, dto.getItemsToUnArchive());
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	/**
	 * Private helper to box up a FilePathSpecWithContents into an S3PaginationDto for the UI
	 * @param path
	 * @param contents
	 * @return
	 */
	private S3PaginationDto convertFileSystemEntriesToDto(String path, FilePathSpecWithContents contents) {
		List<DocumentDto> filesAndFolders = contents.getEntries().stream().map(entry ->
			DocumentDto.builder()
				.path(FilenameUtils.normalizeNoEndSeparator(path))
				.size(entry.getSize())
				.spaceId(entry.getDocumentSpaceId().toString())
				.isFolder(entry.isFolder())
				.key(FilenameUtils.getName(entry.getItemName()))
				.lastModifiedBy(entry.getLastModifiedBy() != null ? entry.getLastModifiedBy() : entry.getCreatedBy())
				.lastModifiedDate(entry.getLastModifiedOn() != null ? entry.getLastModifiedOn() : entry.getCreatedOn())
				.lastActivity(entry.getLastActivity() != null ? entry.getLastActivity() : entry.getCreatedOn())
				.hasContents(entry.isHasNonArchivedContents())
				.build()
		).collect(Collectors.toList());

		return S3PaginationDto.builder()
				.size(filesAndFolders.size())
				.documents(filesAndFolders)
				.currentContinuationToken(null)
				.nextContinuationToken(null)
				.totalElements(filesAndFolders.size())
				.build();
	}

	@Operation(summary = "Adds a new entry to a favorites collection. If no collection exists, it also creates one.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201",
					description = "Successful operation - added",
					content = @Content(schema = @Schema(implementation = FilePathSpec.class))),
			@ApiResponse(responseCode = "404",
					description = "Not Found - user, space, or entry not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "409",
					description = "Entry already exists in the favorites collection",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
	})
	@PreAuthorize("(hasAuthority('DASHBOARD_ADMIN') || @accessCheckDocumentSpace.hasDocumentSpaceAccess(authentication))and #principal != null")
	@PostMapping("/spaces/{id}/collection/favorite/{entryId}")
	public ResponseEntity<Void> addEntityToFavorites(@PathVariable UUID id, @PathVariable UUID entryId, Principal principal){

		documentSpaceUserCollectionService.addEntityToFavoritesFolder(principal.getName(), entryId, id);
		return new ResponseEntity<>(HttpStatus.CREATED);
	}

	@Operation(summary = "Adds a new entry provided with a path to a favorites collection. If no collection exists, it also creates one.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201",
					description = "Successful operation"),
			@ApiResponse(responseCode = "404",
					description = "Not Found - user, space, or entry not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "409",
					description = "Entry already exists in the favorites collection",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
	})
	@PreAuthorize("(hasAuthority('DASHBOARD_ADMIN') || @accessCheckDocumentSpace.hasDocumentSpaceAccess(authentication)) and #principal != null")
	@PostMapping("/spaces/{id}/collection/favorite/")
	public ResponseEntity<Void> addPathEntityToFavorites(@PathVariable UUID id, @Valid @RequestBody DocumentSpacePathItemsDto dto, Principal principal) {
		documentSpaceUserCollectionService.addFileSystemEntryToCollection(principal.getName(), id, dto);
		return new ResponseEntity<>(HttpStatus.CREATED);
	}

	@Operation(summary = "Removes an entry provided with a path from a favorites collection.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful operation"),
			@ApiResponse(responseCode = "404",
					description = "Not Found - user, space, or entry not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
	})
	@PreAuthorize("(hasAuthority('DASHBOARD_ADMIN') || @accessCheckDocumentSpace.hasDocumentSpaceAccess(authentication))and #principal != null")
	@DeleteMapping("/spaces/{id}/collection/favorite/")
	public ResponseEntity<Void> removePathEntityFromFavorites(@PathVariable UUID id, @Valid @RequestBody DocumentSpacePathItemsDto dto, Principal principal) {
		documentSpaceUserCollectionService.removeFileSystemEntryToCollection(principal.getName(), id, dto);
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}


	@Operation(summary = "Removes an entry from a favorites collection.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful operation - removed",
					content = @Content(schema = @Schema(implementation = FilePathSpec.class))),
			@ApiResponse(responseCode = "404",
					description = "Not Found - user, space, or entry not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
	})
	@PreAuthorize("(hasAuthority('DASHBOARD_ADMIN') || @accessCheckDocumentSpace.hasDocumentSpaceAccess(authentication)) and #principal != null")
	@DeleteMapping("/spaces/{id}/collection/favorite/{entryId}")
	public ResponseEntity<Void> removeEntityFromFavorites(@PathVariable UUID id, @PathVariable UUID entryId, Principal principal){
		documentSpaceUserCollectionService.removeEntityFromFavoritesFolder(principal.getName(), entryId, id);
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@Operation(summary = "Gets entries from a favorites collection. If no collection exists, returns empty list.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful",
					content = @Content(schema = @Schema(implementation = DocumentSpaceUserCollectionResponseDtoWrapper.class))),
			@ApiResponse(responseCode = "404",
					description = "Not Found - user, space, or entry not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorize("(hasAuthority('DASHBOARD_ADMIN') || @accessCheckDocumentSpace.hasDocumentSpaceAccess(authentication)) and #principal != null")
	@GetMapping("/spaces/{id}/collection/favorite")
	public ResponseEntity<DocumentSpaceUserCollectionResponseDtoWrapper> getFavorites(@PathVariable UUID id, Principal principal){
		DocumentSpaceUserCollectionResponseDtoWrapper response = new DocumentSpaceUserCollectionResponseDtoWrapper();
		response.setData(documentSpaceUserCollectionService.getFavoriteEntriesForUserInDocumentSpace(principal.getName(), id));
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Gets path of entryId and document space id.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful",
					content = @Content(schema = @Schema(implementation = String.class))),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space, or entry not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorize("(hasAuthority('DASHBOARD_ADMIN') || @accessCheckDocumentSpace.hasDocumentSpaceAccess(authentication)) and #principal != null")
	@GetMapping("/spaces/{id}/path/{entryId}")
	public ResponseEntity<String> getDocumentSpaceEntryPath(@PathVariable UUID id, @PathVariable UUID entryId, Principal principal){
		return ResponseEntity.ok(documentSpaceFileSystemService.getFilePath(id, entryId));
	}

	@Operation(summary = "Moves file(s)/folders(s) from one location in a document space to another within same space.",
			description = "Moves files either within the same space (if sourceSpaceId is equal to the destination space 'id').  If moving cross-space, " +
					"then the user must have READ privileges at a minimum on the source space ID - and obviously needs at least WRITE at the destination")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful"),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space, or entry not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorize("hasAuthority('DASHBOARD_ADMIN') || " +
			"(@accessCheckDocumentSpace.hasReadAccess(authentication, #sourceSpaceId ?: #id) and @accessCheckDocumentSpace.hasWriteAccess(authentication, #id) and #principal != null)")
	@PostMapping("/spaces/{id}/move")
	public ResponseEntity<Object> moveFiles(@Parameter(name = "id", description = "UUID of the destination space", required=true) @PathVariable UUID id,
											Principal principal,
											@RequestBody Map<String, String> files,
											@Parameter(name = "sourceSpaceId", description = "UUID of the source space (if cross space moving) - otherwise uses current space")
												@RequestParam(required=false) UUID sourceSpaceId) {

		for (Map.Entry<String, String> entry : files.entrySet()) {
			documentSpaceService.moveFile(id, sourceSpaceId, entry.getKey(), entry.getValue());
		}
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@Operation(summary = "Copies file(s)/folders(s) from one location in a document space to another within same space.",
	description = "Copies files either within the same space (if sourceSpaceId is equal to the destination space 'id').  If copying cross-space, " +
			"then the user must have READ privileges at a minimum on the source space ID - and obviously needs at least WRITE at the destination")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful"),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space, or entry not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorize("hasAuthority('DASHBOARD_ADMIN') || " +
			"(@accessCheckDocumentSpace.hasReadAccess(authentication, #sourceSpaceId ?: #id) and @accessCheckDocumentSpace.hasWriteAccess(authentication, #id) and #principal != null)")
	@PostMapping("/spaces/{id}/copy")
	public ResponseEntity<Object> copyFiles(@Parameter(name="id", description="Destination Space UUID", required=true) @PathVariable UUID id,
											Principal principal,
											@RequestBody Map<String, String> files,
											@Parameter(name = "sourceSpaceId", description = "UUID of the source space (if cross space copying) - otherwise uses current space")
												@RequestParam(required=false) UUID sourceSpaceId) {

		for (Map.Entry<String, String> entry : files.entrySet()) {
			documentSpaceService.copyFile(id, sourceSpaceId, entry.getKey(), entry.getValue());
		}
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@Operation(summary = "Gets provided space's recently uploaded files/updated files activity",
			description = "Requester must have at least READ access to provided Space.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful",
					content = @Content(schema = @Schema(implementation = RecentDocumentDtoResponseWrapper.class))),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space, or entry not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorize("hasAuthority('DASHBOARD_ADMIN') || @accessCheckDocumentSpace.hasReadAccess(authentication, #id)")
	@WrappedEnvelopeResponse
	@GetMapping("/spaces/{id}/recents")
	public ResponseEntity<Page<RecentDocumentDto>> getRecentsForSpace(@Parameter(name="id", description="Space UUID", required=true) @PathVariable UUID id,
											   @Parameter(name="date", description="ISO UTC date/time to search from looking back") @RequestParam(required=false) Date date,
											   @ParameterObject Pageable pageable) {
		if (date == null) {
			date = new Date();
		}

		return new ResponseEntity<>(documentSpaceService.getRecentlyUploadedFilesBySpace(id, date, pageable), HttpStatus.OK);
	}

	@Operation(summary = "Search a document space for a filename or part of a filename",
			description = "Requester must have at least READ access to provided Space.  Query is case-insensitive and is treated like a filename that 'contains'...")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful",
					content = @Content(schema = @Schema(implementation = DocumentMobileDtoResponseWrapper.class))),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space, or entry not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorize("(hasAuthority('DASHBOARD_ADMIN') || @accessCheckDocumentSpace.hasReadAccess(authentication, #id)) and #principal != null")
	@WrappedEnvelopeResponse
	@PostMapping("/spaces/{id}/search")
	public ResponseEntity<Page<DocumentMobileDto>> searchDocumentSpace(@Parameter(name="id", description="Space UUID", required=true) @PathVariable UUID id,
																	   @Valid @RequestBody DocumentSpaceSearchDto searchDto,
																	   @ParameterObject Pageable pageable,
																	   Principal principal) {

		return new ResponseEntity<>(documentSpaceService.findFilesInSpaceLike(id, searchDto.getQuery(), pageable, principal), HttpStatus.OK);
	}

	@Operation(summary = "Adds an App Client to a Document Space", description = "Adds an App Client to a Document Space with specified privileges")
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
	@PostMapping("/spaces/{id}/app-client")
	public ResponseEntity<Object> addAppClientToDocumentSpace(
			@PathVariable UUID id,
			@Valid @RequestBody DocumentSpaceAppClientMemberRequestDto dto) {
		documentSpaceService.addAppClientUserToDocumentSpace(id, dto);
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@Operation(summary = "Removes one or more App Client(s) from a Document Space", description = "Removes App Client(s) from a Document Space and their privileges")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful operation"),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizeOnlySSO
	@PreAuthorize("@accessCheckDocumentSpace.hasMembershipAccess(authentication, #id)")
	@DeleteMapping("/spaces/{id}/app-client")
	public ResponseEntity<Object> removeAppClientFromDocumentSpace(
			@PathVariable UUID id,
			@RequestParam UUID appClientId) {
		documentSpaceService.removeAppClientUserFromDocumentSpace(id, appClientId);
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@Operation(summary = "Gets the App Clients that can access a given Document Space", description = "Gets the App Clients that can access a space and their privileges")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = DocumentSpaceAppClientResponseDtoWrapper.class))),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden (Requires Membership privilege to document space, or DASHBOARD_ADMIN)",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@WrappedEnvelopeResponse
	@PreAuthorizeOnlySSO
	@PreAuthorize("@accessCheckDocumentSpace.hasMembershipAccess(authentication, #id)")
	@GetMapping("/spaces/{id}/app-clients")
	public ResponseEntity<List<DocumentSpaceAppClientResponseDto>> getAppClientUsersForDocumentSpace(@PathVariable UUID id) {
		return ResponseEntity.ok(documentSpaceService.getAppClientsForDocumentSpace(id));
	}

	@Operation(summary = "Gets list of App Clients that are available to assignment to given doc space",
			description = "The list includes those that are not associated currently with given space")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = AppClientSummaryDtoResponseWrapper.class))),
			@ApiResponse(responseCode = "404",
					description = "Not Found - space not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden (Requires Membership privilege to document space, or DASHBOARD_ADMIN)",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@WrappedEnvelopeResponse
	@PreAuthorizeOnlySSO
	@PreAuthorize("@accessCheckDocumentSpace.hasMembershipAccess(authentication, #id)")
	@GetMapping("/spaces/{id}/available-app-clients")
	public ResponseEntity<List<AppClientSummaryDto>> getAppClientsForAssignmentToDocumentSpace(@PathVariable UUID id) {
		return ResponseEntity.ok(documentSpaceService.getAppClientsForAssignmentToDocumentSpace(id));
	}
}
