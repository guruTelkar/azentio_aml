package com.azentio.aml.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aml.batch")
public record BatchProperties(int gridSize, int chunkSize, int fetchSize) {

	public BatchProperties {
		if (gridSize < 1 || gridSize > 64) {
			throw new IllegalArgumentException("gridSize must be between 1 and 64");
		}
		if (chunkSize < 1) {
			throw new IllegalArgumentException("chunkSize must be positive");
		}
		if (fetchSize < 1) {
			throw new IllegalArgumentException("fetchSize must be positive");
		}
	}
}
