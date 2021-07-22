package mil.tron.commonapi.health;

import org.springframework.boot.actuate.health.SimpleStatusAggregator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Overrides springs system status aggregator (partially) so we can
 * control how the app source statuses affect the OVERALL system status
 */
@Component
public class CustomStatusAggregator extends SimpleStatusAggregator {

    @Override
    public Status getAggregateStatus(Set<Status> statuses) {
        Status sysStatus = super.getAggregateStatus(statuses);

        // if an other-than-app-source component reports a less
        // than good status, report that as the system overall
        if (sysStatus.equals(Status.DOWN)
                || sysStatus.equals(Status.OUT_OF_SERVICE)
                || sysStatus.equals(Status.UNKNOWN)) {

            return sysStatus;
        }

        // otherwise we can look to see if any of the app sources
        //  are reporting a less than good status, and just degrade the system overall status with "WARNING"
        for (Status status : statuses) {
            if (status.getCode().equals(AppSourceHealthIndicator.APPSOURCE_DOWN)
                || status.getCode().equals(AppSourceHealthIndicator.APPSOURCE_ERROR)) {

                return new Status("WARNING");
            }
        }

        // otherwise return the spring calculated status
        return sysStatus;

    }
}
