package mil.tron.commonapi.exception.custom;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

public class TronCommonErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        final Map<String, Object> defaultErrorAttributes = super.getErrorAttributes(webRequest, false);
        final TronCommonAppError tronCommonAppError = TronCommonAppError.fromDefaultAttributeMap(defaultErrorAttributes);
        return tronCommonAppError.toAttributeMap();
    }
}
