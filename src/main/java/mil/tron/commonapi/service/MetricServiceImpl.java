package mil.tron.commonapi.service;

import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.util.MeterPartition;
import lombok.extern.slf4j.Slf4j;
import mil.tron.commonapi.dto.metrics.AppSourceMetricDto;
import mil.tron.commonapi.dto.metrics.EndpointMetricDto;
import mil.tron.commonapi.entity.appsource.AppEndpoint;

// @Service
// public class MetricServiceImpl implements MetricService {
//     private ConcurrentMap<UUID, Integer> metricMap;

//     private AppSourceRepository appSourceRepo;

//     @Autowired
//     public MetricServiceImpl(
//         AppSourceRepository appSourceRepo
//     ) {
//         metricMap = new ConcurrentHashMap<UUID, Integer>();
//         this.appSourceRepo = appSourceRepo;
//     }

//     @Override
//     public void increaseCount(AppEndpoint request) {
//         UUID id = request.getId();
//         Integer count = metricMap.get(id);
//         if (count == null) {
//             metricMap.put(id, 1);
//         } else {
//             metricMap.put(id, count + 1);
//         }
//     }

//     @Override
//     public Map<UUID, Integer> getStatusMetric() {
//         return metricMap;
//     }

//     /**
//      * Return metrics for given app endpoint UUID
//      * @param id UUID of the app endpoint
//      * @return EndpointMetricDto metric data
//      */
//     @Override
//     public EndpointMetricDto getMetricForEndpoint(UUID id) throws RecordNotFoundException {        
//         return EndpointMetricDto.builder()
//             .count(this.metricMap.get(id))
//             .endpoint(id)
//             .build();
//     }

//     /**
//      * Return metrics for all endpoints for given app source UUID
//      * @param id UUID of the app source
//      * @return Iterable<EndpointMetricDto> for all endpoints that belong to the app source
//      */
//     @Override
//     public AppSourceMetricDto getMetricsForAppSource(UUID id) throws RecordNotFoundException {
//         AppSource appSource = appSourceRepo.findById(id).orElseThrow(() -> new RecordNotFoundException("Could not find the app source with id " + id.toString()));
//         return AppSourceMetricDto.builder()
//             .id(id)
//             .endpoints(appSource.getAppEndpoints()
//                 .stream()
//                 .map(endpoint -> EndpointMetricDto.builder()
//                     .count(this.metricMap.get(endpoint.getId()))
//                     .endpoint(endpoint.getId())
//                     .build())
//                 .collect(Collectors.toList()))
//             .build();
//     }
// }

@EnableScheduling
@Service
@Slf4j
public class MetricServiceImpl implements MetricService {

  private MeterRegistry meterRegistry;
  private Clock clock;
  private final AtomicInteger atomicInt = new AtomicInteger(0);

  @Autowired
  public MetricServiceImpl(MeterRegistry meterRegistry, Clock clock) {
    this.meterRegistry = meterRegistry;
    this.clock = clock;
  }

  @Scheduled(fixedRateString = "1000", initialDelayString = "0")
  public void schedulingTask() {
    atomicInt.set(MetricServiceImpl.getRandomNumberInRange(0, 100));
  }

  @Scheduled(fixedRate = 60000)
  public void saveMetrics() {
    System.out.println("Save Metrics");
  }

  private static int getRandomNumberInRange(int min, int max) {
    if (min >= max) {
      throw new IllegalArgumentException("max must be greater than min");
    }

    Random r = new Random();
    return r.nextInt((max - min) + 1) + min;
  }

    @Override
    public void increaseCount(AppEndpoint endpoint) {
        atomicInt.set(getRandomNumberInRange(1, 10));
    }

    @Override
    public Map<UUID, Integer> getStatusMetric() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public EndpointMetricDto getMetricForEndpoint(UUID id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public AppSourceMetricDto getMetricsForAppSource(UUID id) {
        // TODO Auto-generated method stub
        return null;
    }

  private List<Meter> getMeters() {
    return this.meterRegistry.getMeters();
  }

  // @Scheduled(fixedRate = 10000)
	protected void publish() {
        System.out.println("~".repeat(150));
        System.out.println(getMeters().size());
        for(Meter meter : getMeters()) {
            System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
            System.out.println(meter.getId());
            for(Tag tag : meter.getId().getTags()) {
                System.out.println(String.format("%s: %s", tag.getKey(), tag.getValue()));
            }
            for(Measurement measurement : meter.measure()) {
                System.out.println(measurement.getValue());
                System.out.println(measurement.getStatistic());
            }
        }
		// getMeters().stream().forEach(meter -> System.out.println("Publishing " + meter.getId()));
	} 
}