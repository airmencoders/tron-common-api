package mil.tron.commonapi.controller.documentspace;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.minio.IfMinioEnabledOnIL4OrDevLocal;
import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpacePrivilegeDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceResponseDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceUserCollectionResponseDto;
import mil.tron.commonapi.dto.documentspace.mobile.DocumentMobileDto;
import mil.tron.commonapi.dto.documentspace.mobile.DocumentSpaceMobileResponseDto;
import mil.tron.commonapi.dto.documentspace.mobile.S3MobilePaginationDto;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.DashboardUserService;
import mil.tron.commonapi.service.documentspace.DocumentSpacePrivilegeType;
import mil.tron.commonapi.service.documentspace.DocumentSpaceService;
import mil.tron.commonapi.service.documentspace.DocumentSpaceUserCollectionService;
import mil.tron.commonapi.service.documentspace.util.FilePathSpecWithContents;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static mil.tron.commonapi.service.DashboardUserServiceImpl.DASHBOARD_ADMIN_PRIV;

/**
 * Select endpoints for document space that allow less calls to the backend to get
 * information about a user's privs, spaces, and favorites within a given space/path
 */

@RestController
@RequestMapping("${api-prefix.v2}" + DocumentSpaceMobileController.ENDPOINT)
@IfMinioEnabledOnIL4OrDevLocal
public class DocumentSpaceMobileController {
    protected static final String ENDPOINT = "/document-space-mobile";
    private final DocumentSpaceService documentSpaceService;
    private final DocumentSpaceUserCollectionService documentSpaceUserCollectionService;
    private final DashboardUserService dashboardUserService;

    @Autowired
    public DocumentSpaceMobileController(DocumentSpaceService documentSpaceService,
                                         DocumentSpaceUserCollectionService documentSpaceUserCollectionService,
                                         DashboardUserService dashboardUserService) {
        this.documentSpaceService = documentSpaceService;
        this.documentSpaceUserCollectionService = documentSpaceUserCollectionService;
        this.dashboardUserService = dashboardUserService;
    }

    private List<DocumentSpaceMobileResponseDto.SpaceInfo> extractUserPrivFromSpace(DocumentSpaceResponseDto space, Principal principal) {
        List<DocumentSpaceMobileResponseDto.SpaceInfo> spacesWithPrivs = new ArrayList<>();
        List<DocumentSpacePrivilegeDto> privs = documentSpaceService.getDashboardUserPrivilegesForDocumentSpace(space.getId(), principal.getName());
        boolean hasMembership = false;
        boolean hasWrite = false;
        for (DocumentSpacePrivilegeDto priv : privs) {
            if (priv.getType().equals(DocumentSpacePrivilegeType.MEMBERSHIP)) hasMembership = true;
            else if (priv.getType().equals(DocumentSpacePrivilegeType.WRITE)) hasWrite = true;
        }

        if (hasMembership && hasWrite) {
            spacesWithPrivs.add(DocumentSpaceMobileResponseDto.SpaceInfo.builder()
                    .id(space.getId())
                    .name(space.getName())
                    .privilege(DocumentSpaceMobileResponseDto.DocumentSpaceShortPrivilege.ADMIN)
                    .build());
        }
        else if (hasWrite) {
            spacesWithPrivs.add(DocumentSpaceMobileResponseDto.SpaceInfo.builder()
                    .id(space.getId())
                    .name(space.getName())
                    .privilege(DocumentSpaceMobileResponseDto.DocumentSpaceShortPrivilege.EDITOR)
                    .build());
        }
        else {
            spacesWithPrivs.add(DocumentSpaceMobileResponseDto.SpaceInfo.builder()
                    .id(space.getId())
                    .name(space.getName())
                    .privilege(DocumentSpaceMobileResponseDto.DocumentSpaceShortPrivilege.VIEWER)
                    .build());
        }

        return spacesWithPrivs;
    }

    @Operation(summary = "Retrieves all document spaces that the requesting user can access, and their default space (if any)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = DocumentSpaceMobileResponseDto.class))),
            @ApiResponse(responseCode = "403",
                    description = "Forbidden (Requires DASHBOARD_ADMIN or DOCUMENT_SPACE_USER)",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PreAuthorize("@accessCheckDocumentSpace.hasDocumentSpaceAccess(authentication) and #principal != null")
    @GetMapping("/spaces")
    public ResponseEntity<DocumentSpaceMobileResponseDto> getSpacesAvailableAndDefault(Principal principal) {
        DocumentSpaceMobileResponseDto dto = new DocumentSpaceMobileResponseDto();
        DashboardUserDto userDto = dashboardUserService.getSelf(principal.getName());
        List<DocumentSpaceResponseDto> spaces = documentSpaceService.listSpaces(principal.getName());
        List<DocumentSpaceMobileResponseDto.SpaceInfo> spacesWithPrivs = new ArrayList<>();
        for (DocumentSpaceResponseDto space : spaces) {

            // if dashboard_admin - we're an admin for this space - move to next
            if (userDto.getPrivileges().stream().map(item -> item.getName()).collect(Collectors.toList()).contains(DASHBOARD_ADMIN_PRIV)) {
                spacesWithPrivs.add(DocumentSpaceMobileResponseDto.SpaceInfo.builder()
                        .id(space.getId())
                        .name(space.getName())
                        .privilege(DocumentSpaceMobileResponseDto.DocumentSpaceShortPrivilege.ADMIN)
                        .build());

                continue;
            }

            spacesWithPrivs.addAll(extractUserPrivFromSpace(space, principal));
        }

        dto.setSpaces(spacesWithPrivs);
        if (userDto.getDefaultDocumentSpaceId() != null) {
            Optional<DocumentSpaceMobileResponseDto.SpaceInfo> defaultSpaceInfo = spacesWithPrivs.stream().filter(item -> item.getId().equals(userDto.getDefaultDocumentSpaceId())).findFirst();
            if (defaultSpaceInfo.isPresent()) {
                dto.setDefaultSpace(defaultSpaceInfo.get());
            }
        }

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @Operation(summary = "List folders and files at given path - with favorites indicator included", description = "Lists folders and files contained " +
            "within given folder path - one level deep (does not recurse into any sub-folders).  Intended for mobile usage since it included favorites " +
            "indication as well")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = S3MobilePaginationDto.class))),
            @ApiResponse(responseCode = "404",
                    description = "Not Found - space not found or part of supplied path does not exist",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "Forbidden (Requires Read privilege to document space, or DASHBOARD_ADMIN)",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PreAuthorize("(hasAuthority('DASHBOARD_ADMIN') || @accessCheckDocumentSpace.hasDocumentSpaceAccess(authentication)) and #principal != null")
    @GetMapping("/spaces/{id}/contents")
    public ResponseEntity<S3MobilePaginationDto> dumpContentsAtPath(@PathVariable UUID id,
                                                                    Principal principal,
                                                                    @RequestParam(value = "path", defaultValue = "") String path) {
        return new ResponseEntity<>(
                convertFileSystemEntriesToDto(id, path, principal, documentSpaceService.getFolderContents(id, path)),
                HttpStatus.OK);
    }

    /**
     * Private helper to box up a FilePathSpecWithContents into an S3PaginationDto for the UI
     * @param path
     * @param contents
     * @return
     */
    private S3MobilePaginationDto convertFileSystemEntriesToDto(UUID space, String path, Principal principal, FilePathSpecWithContents contents) {
        List<DocumentSpaceUserCollectionResponseDto> favs = documentSpaceUserCollectionService.getFavoriteEntriesForUserInDocumentSpace(principal.getName(), space);

        List<DocumentMobileDto> filesAndFolders = contents.getEntries().stream().map(entry ->
                DocumentMobileDto.builder()
                        .path(FilenameUtils.normalizeNoEndSeparator(path))
                        .size(entry.getSize())
                        .spaceId(entry.getDocumentSpaceId().toString())
                        .isFolder(entry.isFolder())
                        .parentId(entry.getParentEntryId())
                        .isFavorite(!favs.stream().filter(item -> item.getItemId().equals(entry.getItemId())).findAny().isEmpty())
                        .elementUniqueId(entry.getItemId())
                        .key(FilenameUtils.getName(entry.getItemName()))
                        .lastModifiedBy(entry.getLastModifiedBy() != null ? entry.getLastModifiedBy() : entry.getCreatedBy())
                        .lastModifiedDate(entry.getLastModifiedOn() != null ? entry.getLastModifiedOn() : entry.getCreatedOn())
                        .lastActivity(entry.getLastActivity() != null ? entry.getLastActivity() : entry.getCreatedOn())
                        .hasContents(entry.isHasNonArchivedContents())
                        .build()
        ).collect(Collectors.toList());

        return S3MobilePaginationDto.builder()
                .size(filesAndFolders.size())
                .documents(filesAndFolders)
                .currentContinuationToken(null)
                .nextContinuationToken(null)
                .totalElements(filesAndFolders.size())
                .build();
    }
}
