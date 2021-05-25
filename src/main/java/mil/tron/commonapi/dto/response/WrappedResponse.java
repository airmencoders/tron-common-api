package mil.tron.commonapi.dto.response;

import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Wraps data response in envelope
 * 
 * @param <T> The type of the data being returned
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WrappedResponse<T> {
	@Getter
	@Setter
	@NotNull
	private T data;
}

