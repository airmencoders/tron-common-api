package mil.tron.commonapi.controller.documentspace;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import mil.tron.commonapi.WebConfig;
import mil.tron.commonapi.annotation.minio.IfMinioEnabledOnStagingIL4OrDevLocal;
import mil.tron.commonapi.service.documentspace.DocumentSpaceService;
import mil.tron.commonapi.service.webdav.WebDavService;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.ResourceUrlProvider;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

@RestController
@RequestMapping("${api-prefix.v2}" + DocumentSpaceWebDavController.ENDPOINT)
@IfMinioEnabledOnStagingIL4OrDevLocal
public class DocumentSpaceWebDavController {
    protected static final String ENDPOINT = "/document-space-dav";

    @Autowired
    private WebDavService webDavService;

    @Autowired
    private DocumentSpaceService documentSpaceService;

    // Note: In formal implementation WebDAV verbs will need to be "whitelisted" in a filter
    @RequestMapping(value = "/{spaceId}/**", produces = { "application/xml"})
    @ResponseBody public ResponseEntity<Object> processWebDavCommand(@PathVariable UUID spaceId,
                                                                     HttpServletRequest request,
                                                                     HttpServletResponse response) throws Exception {
        // only way it seems to get the rest-of-url into a variable..
        ResourceUrlProvider urlProvider = (ResourceUrlProvider) request
                .getAttribute(ResourceUrlProvider.class.getCanonicalName());

        String restOfUrl = urlProvider.getPathMatcher().extractPathWithinPattern(
                String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)),
                String.valueOf(request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)));

        String folderPath = FilenameUtils.getPath(restOfUrl);
        String name = FilenameUtils.getName(restOfUrl);

        if (request.getMethod().equalsIgnoreCase("PROPFIND")) {
            boolean includeChildren = (request.getHeader("depth") != null && request.getHeader("depth").equals("1"));
            return new ResponseEntity<>(webDavService.propFind(spaceId, restOfUrl, includeChildren), HttpStatus.MULTI_STATUS);
        }
        else if (request.getMethod().equalsIgnoreCase("OPTIONS")) {
            response.setHeader("allow", String.join(",", WebConfig.allowedMethods));
            return new ResponseEntity<>(HttpStatus.OK);
        }
        else if (request.getMethod().equalsIgnoreCase("GET")) {
            return this.getFileWebDav(spaceId, folderPath, name);
        }
        else if (request.getMethod().equalsIgnoreCase("MKCOL")) {
            return new ResponseEntity<>(webDavService.mkCol(spaceId, restOfUrl), HttpStatus.CREATED);
        }
        else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    private ResponseEntity<Object> getFileWebDav(UUID spaceId, String path, String name) {
        S3Object s3Data = documentSpaceService.getFile(spaceId, path, name);
        ObjectMetadata s3Meta = s3Data.getObjectMetadata();
        var response = new InputStreamResource(s3Data.getObjectContent());
        return ResponseEntity
                .ok()
                .contentType(MediaType.valueOf(s3Meta.getContentType()))
                .headers(DocumentSpaceController.createDownloadHeaders(name))
                .body(response);
    }
}
