package mil.tron.commonapi.entity.kpi;

public interface ServiceMetric {
	String getName();
	Long getAverageLatency();
	Long getResponseCount();
}
