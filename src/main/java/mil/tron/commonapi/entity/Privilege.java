package mil.tron.commonapi.entity;


import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
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
