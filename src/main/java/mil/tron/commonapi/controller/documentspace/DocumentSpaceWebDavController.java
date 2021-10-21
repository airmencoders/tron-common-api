package mil.tron.commonapi.controller.documentspace;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("${api-prefix.v2}" + DocumentSpaceWebDavController.ENDPOINT)
public class DocumentSpaceWebDavController {
    protected static final String ENDPOINT = "/document-space-dav";

    // Note: In formal implementation WebDAV verbs will need to be "whitelisted" in a filter
    @RequestMapping(value = "/")
    public void getContents(HttpServletRequest request) {
        if (request.getMethod().equals("PROPFIND")) {

        }
    }
}
