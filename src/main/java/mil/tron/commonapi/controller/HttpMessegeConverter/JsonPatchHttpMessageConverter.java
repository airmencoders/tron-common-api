package mil.tron.commonapi.controller.HttpMessegeConverter;

//import javax.json.Json;
//import javax.json.JsonPatch;
//import javax.json.JsonReader;
import org.apache.commons.lang3.NotImplementedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;
import com.github.fge.jsonpatch.JsonPatch;

import java.io.InputStream;


@Component
public class JsonPatchHttpMessageConverter extends AbstractHttpMessageConverter<JsonPatch> {

    final protected ObjectMapper mapper = new ObjectMapper();

    public JsonPatchHttpMessageConverter() {
        super(MediaType.valueOf("application/json-patch+json"));
    }

    @Override
    protected JsonPatch readInternal(Class<? extends JsonPatch> clazz, HttpInputMessage inputMessage) throws HttpMessageNotReadableException {

        try (InputStream in = inputMessage.getBody()) {
            return mapper.readValue(in, JsonPatch.class);
        } catch (Exception e) {
            throw new HttpMessageNotReadableException(e.getMessage(), inputMessage);
        }
    }

    @Override
    protected void writeInternal(JsonPatch jsonPatch, HttpOutputMessage outputMessage) throws HttpMessageNotWritableException {
        throw new NotImplementedException("The write Json patch is not implemented");
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return JsonPatch.class.isAssignableFrom(clazz);
    }

}