package debie.harness;

import static debie.harness.Harness.*;
import static debie.health.HealthMonitoringTask.*;
import debie.particles.AcquisitionTask;

public class MonitoringTaskTest extends HarnessTest {

	public MonitoringTaskTest(HarnessSystem sys, TestLogger tl) {
		super(sys, tl);
	}

	@Override
	public void runTests() {
		
		/* Reset all cycles: */

		hmTask.setHealthMonRound(HEALTH_COUNT);
		hmTask.setTempMeasCount(TEMP_COUNT);
		hmTask.setVoltageMeasCount(VOLTAGE_COUNT);
		hmTask.setChecksumCount(CHECK_COUNT);
		
		system.adcSim.setADNominal();
		/* To avoid monitoring alarms. */
	
		testMonitorNoErrorsOrHits();
		testMonitorNoErrorsOneHit();
		testMonitorNoErrorsManyHits();
		
		reportTestResults("MonitoringTaskTest");
	}

	private void testMonitorNoErrorsOrHits() {		
		testcase("Monitoring without errors or interrupting hits");
		
		/* A/D conversions ready on second poll: */
		system.adcSim.setADDelay(2);
		
		checkEquals("health_mon_round = HEALTH_COUNT", hmTask.getHealthMonRound(), HEALTH_COUNT);
		checkEquals("temp_meas_count = TEMP_COUNT", hmTask.getTempMeasCount(), TEMP_COUNT);
		checkEquals("voltage_meas_count = VOLTAGE_COUNT", hmTask.getVoltageMeasCount(), VOLTAGE_COUNT);
		checkEquals("checksum_count = CHECK_COUNT", hmTask.getChecksumCount(), CHECK_COUNT);
		
	      /* The first 9 seconds: */

	      for (int sec = 1; sec <= 9; sec ++) {
	    	  monitorHealth (Prob6a);

	    	  checkEquals("health_mon_round = HEALTH_COUNT - sec", hmTask.getHealthMonRound(), HEALTH_COUNT - sec);
	    	  checkEquals("temp_meas_count = TEMP_COUNT", hmTask.getTempMeasCount(), TEMP_COUNT);
	    	  checkEquals("voltage_meas_count = VOLTAGE_COUNT", hmTask.getVoltageMeasCount(), VOLTAGE_COUNT);
	    	  checkEquals("checksum_count = CHECK_COUNT - sec", hmTask.getChecksumCount(), CHECK_COUNT - sec);
	      }

	      /* The 10th second: */

	      monitorHealth (Prob6a);

	      checkEquals("health_mon_round = HEALTH_COUNT", hmTask.getHealthMonRound(), HEALTH_COUNT);
	      checkEquals("temp_meas_count = TEMP_COUNT - 1", hmTask.getTempMeasCount(), TEMP_COUNT - 1);
	      checkEquals("voltage_meas_count = VOLTAGE_COUNT - 1", hmTask.getVoltageMeasCount(), VOLTAGE_COUNT - 1);
	      checkEquals("checksum_count = CHECK_COUNT - 10", hmTask.getChecksumCount(), CHECK_COUNT - 10);

	      /* The remaining 170 seconds of the full period: */

	      for (int sec = 11; sec <= 180; sec ++) {
	         monitorHealth (Prob6a);
	      }

	      checkEquals("health_mon_round = HEALTH_COUNT", hmTask.getHealthMonRound(), HEALTH_COUNT);
	      checkEquals("temp_meas_count = TEMP_COUNT", hmTask.getTempMeasCount(), TEMP_COUNT);
	      checkEquals("voltage_meas_count = VOLTAGE_COUNT", hmTask.getVoltageMeasCount(), VOLTAGE_COUNT);
	      checkEquals("checksum_count = CHECK_COUNT", hmTask.getChecksumCount(), CHECK_COUNT);

	      checkNoErrors();
	}
	
	private void testMonitorNoErrorsOneHit() {		
		testcase("Monitoring without errors, at most one interrupting hit");
		
		system.adcSim.total_adc_hits = 0;
		
		for (int sec = 1; sec <= 180; sec ++) {
			system.adcSim.max_adc_hits = 1;
			monitorHealth (Prob6b);
		}

		if (Harness.TRACE) Harness.trace(String.format("Total hits %d", system.adcSim.total_adc_hits));
		
		checkNonZero(system.adcSim.total_adc_hits);
		
		checkEquals("health_mon_round = HEALTH_COUNT", hmTask.getHealthMonRound(), HEALTH_COUNT);
		checkEquals("temp_meas_count = TEMP_COUNT", hmTask.getTempMeasCount(), TEMP_COUNT);
		checkEquals("voltage_meas_count = VOLTAGE_COUNT", hmTask.getVoltageMeasCount(), VOLTAGE_COUNT);
		checkEquals("checksum_count = CHECK_COUNT", hmTask.getChecksumCount(), CHECK_COUNT);

		checkNoErrors();
	}
	
	private void testMonitorNoErrorsManyHits() {		
		testcase("Monitoring without errors, many interrupting hits");
		
		system.adcSim.total_adc_hits = 0;
		
		for (int sec = 1; sec <= 180; sec ++) {
			system.adcSim.max_adc_hits = AcquisitionTask.HIT_BUDGET_DEFAULT;
			monitorHealth (Prob6c);
		}

		if (Harness.TRACE) Harness.trace(String.format("Total hits %d", system.adcSim.total_adc_hits));
		
		checkNonZero(system.adcSim.total_adc_hits);
		
		checkEquals("health_mon_round = HEALTH_COUNT", hmTask.getHealthMonRound(), HEALTH_COUNT);
		checkEquals("temp_meas_count = TEMP_COUNT", hmTask.getTempMeasCount(), TEMP_COUNT);
		checkEquals("voltage_meas_count = VOLTAGE_COUNT", hmTask.getVoltageMeasCount(), VOLTAGE_COUNT);
		checkEquals("checksum_count = CHECK_COUNT", hmTask.getChecksumCount(), CHECK_COUNT);

		checkNoErrors();
	}
}
