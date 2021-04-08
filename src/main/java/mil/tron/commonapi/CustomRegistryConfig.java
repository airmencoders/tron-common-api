package mil.tron.commonapi;

import io.micrometer.core.instrument.step.StepRegistryConfig;

public interface CustomRegistryConfig extends StepRegistryConfig {

	CustomRegistryConfig DEFAULT = k -> null;
    // private final Duration step = Duration.ofSeconds(10);
    
	@Override
	default String prefix() {
		return "gateway";
	}

	// @Override
	// default String get(String key) {
	// 	return null;
	// }


    // @Override
    // public Duration step() {
    //     return step;
    // }
}