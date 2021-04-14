package mil.tron.commonapi;

import io.micrometer.core.instrument.step.StepRegistryConfig;

public interface CustomRegistryConfig extends StepRegistryConfig {

	CustomRegistryConfig DEFAULT = k -> null;
    
	@Override
	default String prefix() {
		return "gateway";
	}
}