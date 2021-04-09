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
    private MeterValueRepository timerMeterValueRepo;

    @Override
    @Transactional
    public void publishToDatabase(List<List<Meter>> meters, Clock clock, TimeUnit baseTimeUnit) {
        Date date = new Date();
        for(List<Meter> batch : meters) {            
            // We only care about timers, so everything else does nothing.
            batch.stream()
                .filter(m -> m.getId().getName().startsWith("gateway"))
                .forEach(meter -> meter.use(
                    m -> {}, // gauge
                    counter -> createMeterValue(counter, date), // counter
                    m -> {}, // timer
                    m -> {}, // summary
                    m -> {}, // long task timer
                    m -> {}, // time gauge
                    m -> {}, // function counter
                    m -> {}, // function timer
                    m -> {})); // generic/custom meter 
        }
    }

    private void createMeterValue(Counter counter, Date date) {
        try {
            timerMeterValueRepo.save(buildMeterValue(counter, date));
        } catch (Exception e) {
            // log.warn(String.format("Unable to add meter value to %s.", timer.getId().getName()), e);
        }
    }

    private MeterValue buildMeterValue(Counter counter, Date date) {
        AppSource appSource = AppSource.builder().id(UUID.fromString(counter.getId().getTag("AppSource"))).build();
        AppEndpoint appEndpoint = AppEndpoint.builder().id(UUID.fromString(counter.getId().getTag("Endpoint"))).build();
        AppClientUser appClientUser = AppClientUser.builder().id(UUID.fromString(counter.getId().getTag("AppClient"))).build();

        return MeterValue.builder()
            .timestamp(date)
            .value(counter.count())
            .metricName(counter.getId().getName())
            .appSource(appSource)
            .appEndpoint(appEndpoint)
            .appClientUser(appClientUser)
            .build();  
    }
}
