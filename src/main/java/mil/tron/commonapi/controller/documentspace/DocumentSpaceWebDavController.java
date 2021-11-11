package mil.tron.commonapi.controller.documentspace;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import com.google.common.collect.Lists;
import mil.tron.commonapi.dto.dav.PropFindDto;
import mil.tron.commonapi.service.trace.ContentTrace;
import mil.tron.commonapi.service.webdav.WebDavService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.ResourceUrlProvider;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Enumeration;
import java.util.UUID;

@RestController
@RequestMapping("${api-prefix.v2}" + DocumentSpaceWebDavController.ENDPOINT)
public class DocumentSpaceWebDavController {
    protected static final String ENDPOINT = "/document-space-dav";

    // for debug only
    @Autowired
    private ContentTrace trace;

    @Autowired
    private WebDavService webDavService;

    private void printInfo(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaderNames();
        System.out.println("");
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            System.out.println(String.format("%s: %s", header, request.getHeader(header)));
        }
        System.out.println("-----");
        System.out.println("Method: " + request.getMethod());
        System.out.println("Body: " + trace.getRequestBody());
        System.out.println("");
    }

    // Note: In formal implementation WebDAV verbs will need to be "whitelisted" in a filter
    @RequestMapping(value = "/{spaceId}/**", produces = { "application/xml"})
    @ResponseBody public ResponseEntity<String> processWebDavCommand(@PathVariable UUID spaceId,
                                                                     HttpServletRequest request,
                                                                     HttpServletResponse response) throws Exception {
        XmlMapper xmlMapper = new XmlMapper();
        xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
        response.setContentType("application/xml");

        // only way it seems to get the rest-of-url into a variable..
        ResourceUrlProvider urlProvider = (ResourceUrlProvider) request
                .getAttribute(ResourceUrlProvider.class.getCanonicalName());
        String restOfUrl = urlProvider.getPathMatcher().extractPathWithinPattern(
                String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)),
                String.valueOf(request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)));

        if (request.getMethod().equals("PROPFIND")) {
            return new ResponseEntity<>(xmlMapper.writeValueAsString(webDavService.propFind(spaceId, restOfUrl)), HttpStatus.MULTI_STATUS);
        }
        else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
