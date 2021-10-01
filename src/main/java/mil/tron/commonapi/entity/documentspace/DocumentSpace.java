package mil.tron.commonapi.entity.documentspace;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class DocumentSpace {
	@Id
	@Builder.Default
	private UUID id = UUID.randomUUID();
	
	@NotBlank
	@NotNull
	@Size(max = 255)
	@Column(unique = true)
	private String name;
}
