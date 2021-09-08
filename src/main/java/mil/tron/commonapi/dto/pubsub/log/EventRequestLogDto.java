package mil.tron.commonapi.dto.pubsub.log;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mil.tron.commonapi.dto.appclient.AppClientSummaryDto;
import mil.tron.commonapi.entity.pubsub.events.EventType;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class EventRequestLogDto {
	@Getter
    @Setter
	private AppClientSummaryDto appClientUser;
	
	@Getter
    @Setter
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
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
	private Date lastAttempted;
}
