package mil.tron.commonapi.pubsub;

import mil.tron.commonapi.dto.EventInfoDto;
import mil.tron.commonapi.dto.pubsub.PubSubLedgerEntryDto;
import mil.tron.commonapi.pubsub.messages.PubSubMessage;

import java.util.Date;
import java.util.List;

public interface EventManagerService {

    void recordEventAndPublish(PubSubMessage message);
    Iterable<PubSubLedgerEntryDto> getMessagesSinceDateTime(Date timeDateStamp);
    Iterable<PubSubLedgerEntryDto> getMessagesSinceEventCountByType(List<EventInfoDto> events);
    Iterable<EventInfoDto> getEventTypeCounts();
}
