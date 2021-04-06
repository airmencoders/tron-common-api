package mil.tron.commonapi.dto.annotation.helper;

import lombok.Getter;
import lombok.Setter;

public class JsonPatchObjectValue extends JsonPatchAnnotated {

    @Getter
    @Setter
    private Object value;
}
