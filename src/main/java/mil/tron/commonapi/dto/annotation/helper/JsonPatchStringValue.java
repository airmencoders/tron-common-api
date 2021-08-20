package mil.tron.commonapi.dto.annotation.helper;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

public class JsonPatchStringValue extends JsonPatchAnnotated {
	@Builder
	public JsonPatchStringValue(PatchOp op, String path, String value) {
		super(op, path);
		this.value = value;
	}
	
    @Getter
    @Setter
    private String value;
}
