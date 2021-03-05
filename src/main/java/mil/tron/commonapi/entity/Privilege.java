package mil.tron.commonapi.entity;


import lombok.*;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Privilege {
	@Id
    @GeneratedValue(strategy = GenerationType.AUTO)
	@Getter
	@Setter
    private Long id;
	
	@Getter
	@Setter
	private String name;
}
