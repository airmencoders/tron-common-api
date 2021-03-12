package mil.tron.commonapi.pubsub;

import mil.tron.commonapi.entity.pubsub.PubSubLedger;
import mil.tron.commonapi.pubsub.messages.PubSubMessage;

import java.util.Date;
import java.util.Map;

public interface EventManagerService {

    void recordEventAndPublish(PubSubMessage message);
    Iterable<PubSubLedger> getMessagesSinceDateTime(Date timeDateStamp);
    Map<String, Long> getEventTypeCounts();
}
