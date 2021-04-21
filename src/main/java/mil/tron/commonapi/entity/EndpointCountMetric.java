package mil.tron.commonapi.entity;

import org.springframework.web.bind.annotation.RequestMethod;

public interface EndpointCountMetric extends CountMetric {
    RequestMethod getMethod();
}
