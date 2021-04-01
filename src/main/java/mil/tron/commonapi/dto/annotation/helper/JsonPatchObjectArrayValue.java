package mil.tron.commonapi.dto.annotation.helper;

import lombok.Getter;
import lombok.Setter;

public class JsonPatchObjectArrayValue extends JsonPatchAnnotated {

    @Getter
    @Setter
    private Object[] value;
}
