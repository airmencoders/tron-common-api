package mil.tron.commonapi.controller.documentspace;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;

import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.documentspace.DocumentDto;
import mil.tron.commonapi.service.documentspace.DocumentSpaceService;

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
	
	@PostMapping("/files")
    public Map<String, String> upload(@RequestPart("file") MultipartFile file) {
		documentSpaceService.uploadFile(file);
		 
        Map<String, String> result = new HashMap<>();
        result.put("key", file.getOriginalFilename());
        return result;
    }

    @GetMapping("/file/{keyName}")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable("keyName") String keyName) {
        S3Object s3Data = documentSpaceService.downloadFile(keyName);
        ObjectMetadata s3Meta = s3Data.getObjectMetadata();
        
        var response = new InputStreamResource(s3Data.getObjectContent());
        
        return ResponseEntity
                .ok()
                .contentType(MediaType.valueOf(s3Meta.getContentType()))
                .headers(createDownloadHeaders(keyName))
                .body(response);
    }
    
    @GetMapping("/files/{keyNames}")
    public ResponseEntity<StreamingResponseBody> downloadFiles(@PathVariable("keyNames") Set<String> keyNames) {
        StreamingResponseBody response = out -> documentSpaceService.downloadAndWriteCompressedFiles(keyNames, out);
        
        return ResponseEntity
                .ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .headers(createDownloadHeaders("files.zip"))
                .body(response);
    }
    
    @DeleteMapping("/files/{keyName}")
    public void delete(@PathVariable("keyName") String keyName) {
    	documentSpaceService.deleteFile(keyName);
    }

    @GetMapping("/files")
    public List<DocumentDto> listObjects() {
        return documentSpaceService.listFiles();
    }
}
