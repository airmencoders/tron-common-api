package mil.tron.commonapi.controller.documentspace;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import mil.tron.commonapi.dto.dav.*;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;

@RestController
@RequestMapping("${api-prefix.v2}" + DocumentSpaceWebDavController.ENDPOINT)
public class DocumentSpaceWebDavController {
    protected static final String ENDPOINT = "/document-space-dav";

    // Note: In formal implementation WebDAV verbs will need to be "whitelisted" in a filter
    @RequestMapping(value = "", produces = { "application/xml"})
    @ResponseBody public ResponseEntity<String> getContents(HttpServletRequest request, HttpServletResponse response) throws Exception {
        XmlMapper xmlMapper = new XmlMapper();
        xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
        response.setContentType("application/xml");
        if (request.getMethod().equals("PROPFIND")) {
            String resultString = "<?xml version=\"1.0\" encoding=\"utf-8\" ?><D:multistatus xmlns:D=\"DAV:\">\n" +
                    "<D:response><D:href>//</D:href><D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>\n" +
                    "<D:response><D:href>/My%20Private%20Files</D:href><D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>\n" +
                    "<D:response><D:href>/On%20My%20iPhone</D:href><D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>\n" +
                    "<D:response><D:href>/Photo%20Library</D:href><D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>\n" +
                    "</D:multistatus>";
            return new ResponseEntity<>(resultString, HttpStatus.MULTI_STATUS);
        }
        return new ResponseEntity<>(PropFindDto.builder().build().toString(), HttpStatus.NOT_FOUND);
    }
}
