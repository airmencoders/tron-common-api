package mil.tron.commonapi.service;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.MeterValue;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.repository.MeterValueRepository;

@Service
public class CustomMetricServiceImpl implements CustomMetricService {

    @Autowired
    private MeterValueRepository meterValueRepo;

    @Override
    @Transactional
    public void publishToDatabase(List<List<Meter>> meters, Clock clock, TimeUnit baseTimeUnit) {
        Date date = new Date();
        for(List<Meter> batch : meters) {            
            // We only care about timers, so everything else does nothing.
            batch.stream()
                .filter(m -> m.getId().getName().startsWith("gateway"))
                .forEach(meter -> meter.use(
                    g -> {}, // gauge
                    counter -> buildMeterValue(counter, date), // counter
                    t -> {}, // timer
                    d -> {}, // distribution summary
                    l -> {}, // long task timer
                    t -> {}, // time gauge
                    f -> {}, // function counter
                    f -> {}, // function timer
                    m -> {})); // generic/custom meter 
        }
    }

    private void buildMeterValue(Counter counter, Date date) {
        Meter.Id counterId = counter.getId();
        AppSource appSource = AppSource.builder()
            .id(UUID.fromString(counterId.getTag("AppSource")))
            .build();
        AppEndpoint appEndpoint = AppEndpoint.builder()
            .id(UUID.fromString(counterId.getTag("Endpoint")))
            .build();
        AppClientUser appClientUser = AppClientUser.builder()
            .id(UUID.fromString(counterId.getTag("AppClient")))
            .build();

        meterValueRepo.save(MeterValue.builder()
            .id(UUID.randomUUID())
            .timestamp(date)
            .value(counter.count())
            .metricName(counterId.getName())
            .appSource(appSource)
            .appEndpoint(appEndpoint)
            .appClientUser(appClientUser)
            .build());  
    }
}
