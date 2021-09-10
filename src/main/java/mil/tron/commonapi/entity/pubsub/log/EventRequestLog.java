package mil.tron.commonapi.entity.pubsub.log;

import java.util.Date;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.pubsub.events.EventType;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@EqualsAndHashCode
public class EventRequestLog {
	public static final String APP_CLIENT_USER_FIELD = "appClientUser";
	
	@Id
    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();
	
	@Getter
    @Setter
	@ManyToOne
	private AppClientUser appClientUser;
	
	@Getter
    @Setter
    @Enumerated(value = EnumType.STRING)
	private EventType eventType;
	
	@Getter
	@Setter
	private long eventCount;
	
	@Getter
    @Setter
	private boolean wasSuccessful;
	
	@Getter
	@Setter
	private String reason;
	
	@Getter
    @Setter
	private Date lastAttempted;
}
