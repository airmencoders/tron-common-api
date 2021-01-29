package mil.tron.commonapi.entity;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Role {
	@Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
	
	private String name;
	
	@Builder.Default
	@ManyToMany
	private Set<Privilege> privileges = new HashSet<>();
}
