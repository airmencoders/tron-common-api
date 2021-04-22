package mil.tron.commonapi.entity;

import java.util.UUID;

public interface CountMetric {
    UUID getId();
    String getName();
    Double getSum();
}
