package mil.tron.commonapi.service.fieldauth;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Builder
public class EntityFieldAuthResponse<T> {
	@Getter
	private T modifiedEntity;
	
	@Getter
	@Builder.Default
	private List<String> deniedFields = new ArrayList<>();
}
