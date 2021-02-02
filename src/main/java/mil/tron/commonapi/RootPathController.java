package mil.tron.commonapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootPathController {

    @Value("${springdoc.swagger-ui.path}")
    private String swaggerPath;

    @GetMapping("/")
    public String index() {
        return "redirect:" + swaggerPath;
    }
}
