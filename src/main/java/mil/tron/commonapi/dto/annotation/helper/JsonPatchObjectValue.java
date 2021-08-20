package mil.tron.commonapi.dto.annotation.helper;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

public class JsonPatchObjectValue extends JsonPatchAnnotated {
	@Builder
	public JsonPatchObjectValue(PatchOp op, String path, Object value) {
		super(op, path);
		this.value = value;
	}
	
    @Getter
    @Setter
    private Object value;
}
