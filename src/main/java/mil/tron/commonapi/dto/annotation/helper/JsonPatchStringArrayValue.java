package mil.tron.commonapi.dto.annotation.helper;

import lombok.Getter;
import lombok.Setter;

public class JsonPatchStringArrayValue extends JsonPatchAnnotated {

    @Getter
    @Setter
    private String[] value;
}
