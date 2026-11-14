package com.azentio.aml.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.azentio.aml.scenario.ScenarioEngine;

@Configuration
@EnableConfigurationProperties(ScenarioProperties.class)
public class ScenarioConfiguration {

	@Bean
	ScenarioEngine scenarioEngine(ScenarioProperties properties) {
		return new ScenarioEngine(properties.toThresholds());
	}
}
