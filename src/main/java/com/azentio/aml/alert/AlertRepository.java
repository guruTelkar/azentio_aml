package com.azentio.aml.alert;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.azentio.aml.scenario.ScenarioCode;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<AlertEntity, UUID> {

	Optional<AlertEntity> findByAccountNumberAndScenarioCodeAndAlertDate(
			String accountNumber,
			ScenarioCode scenarioCode,
			LocalDate alertDate);

	@Query("""
			select alert from AlertEntity alert
			where (:scenarioCode is null or alert.scenarioCode = :scenarioCode)
			and (:source is null or alert.source = :source)
			and (:fromDate is null or alert.alertDate >= :fromDate)
			and (:toDate is null or alert.alertDate <= :toDate)
			""")
	Page<AlertEntity> search(
			@Param("scenarioCode") ScenarioCode scenarioCode,
			@Param("source") AlertSource source,
			@Param("fromDate") LocalDate fromDate,
			@Param("toDate") LocalDate toDate,
			Pageable pageable);
}
