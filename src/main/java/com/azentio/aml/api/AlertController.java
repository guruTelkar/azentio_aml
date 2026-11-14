package com.azentio.aml.api;

import java.time.LocalDate;
import java.util.UUID;

import com.azentio.aml.alert.AlertQueryService;
import com.azentio.aml.alert.AlertSource;
import com.azentio.aml.scenario.ScenarioCode;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

	private final AlertQueryService alertQueryService;

	public AlertController(AlertQueryService alertQueryService) {
		this.alertQueryService = alertQueryService;
	}

	@GetMapping
	public PageResponse<AlertListItem> search(
			@RequestParam(required = false) ScenarioCode scenario,
			@RequestParam(required = false) AlertSource source,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return alertQueryService.search(scenario, source, from, to, page, size);
	}

	@GetMapping("/{id}")
	public AlertDetail find(@PathVariable UUID id) {
		return alertQueryService.find(id);
	}
}
