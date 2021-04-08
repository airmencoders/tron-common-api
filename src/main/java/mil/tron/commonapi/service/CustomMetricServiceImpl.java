package mil.tron.commonapi.service;

import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.lang.Nullable;

@Service
public class CustomMetricServiceImpl implements CustomMetricService {

    @Override
    public void publishToDatabase(List<List<Meter>> meters, Clock clock, TimeUnit baseTimeUnit) {
        long time = clock.wallTime();
        for (List<Meter> batch : meters) {
            System.out.println("Sending Metrics Batch to Server: " + time);

        }
    }
}
