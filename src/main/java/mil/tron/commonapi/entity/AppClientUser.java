package mil.tron.commonapi.entity;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import mil.tron.commonapi.entity.appsource.App;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;

/**
 * Class that represents a Client User entity that is an Application
 *
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Table(name="app")
public class AppClientUser extends App {

	@Getter
	@Setter
	@Builder.Default
	@ManyToMany
	private Set<Privilege> privileges = new HashSet<>();

	/**
	 * List of App Client Developers that can see what app sources this client is connected to
	 * An app client can have many developers...
	 */
	@Getter
	@Setter
	@Builder.Default
	@ManyToMany
	private Set<DashboardUser> appClientDevelopers = new HashSet<>();

	@Getter
	@Setter
	@Builder.Default
	@OneToMany(mappedBy = "appClientUser")
	private Set<AppEndpointPriv> appEndpointPrivs = new HashSet<>();

}
