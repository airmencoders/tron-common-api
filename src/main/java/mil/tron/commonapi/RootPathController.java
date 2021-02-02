package mil.tron.commonapi;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootPathController {
    @GetMapping("/")
    public String index() {
        return "redirect:/api-docs/index";
    }
}
