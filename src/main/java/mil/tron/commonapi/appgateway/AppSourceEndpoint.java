package mil.tron.commonapi.appgateway;

import lombok.Value;
import org.springframework.web.bind.annotation.RequestMethod;

@Value
public class AppSourceEndpoint {

    String path;
    RequestMethod method;
}
