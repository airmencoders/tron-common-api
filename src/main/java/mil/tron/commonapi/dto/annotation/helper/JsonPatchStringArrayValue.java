package mil.tron.commonapi.dto.annotation.helper;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

public class JsonPatchStringArrayValue extends JsonPatchAnnotated {
	@Builder
	public JsonPatchStringArrayValue(PatchOp op, String path, String[] value) {
		super(op, path);
		this.value = value;
	}
	
    @Getter
    @Setter
    private String[] value;
}
