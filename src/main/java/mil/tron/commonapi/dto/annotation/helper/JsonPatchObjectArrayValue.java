package mil.tron.commonapi.dto.annotation.helper;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

public class JsonPatchObjectArrayValue extends JsonPatchAnnotated {
	@Builder
	public JsonPatchObjectArrayValue(PatchOp op, String path, Object[] value) {
		super(op, path);
		this.value = value;
	}

    @Getter
    @Setter
    private Object[] value;
}
