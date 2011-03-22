package debie.harness;

import static debie.harness.Harness.*;
import static debie.health.HealthMonitoringTask.*;
import debie.particles.AcquisitionTask;
import debie.support.Dpu;
import debie.telecommand.TelecommandExecutionTask;

public class MonitoringTaskTest extends HarnessTest {

	private static final int max_errors = 20; 
	
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
	
		testMonitorNoErrorsNoHits();
		testMonitorNoErrorsOneHit();
		testMonitorNoErrorsManyHits();
		testMonitorSUErrorsNoHits();
		testMonitorWithErrorsAndHits();
		
		reportTestResults("MonitoringTaskTest");
	}

	private void testMonitorNoErrorsNoHits() {		
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
	
	private void testMonitorSUErrorsNoHits() {
		testcase("Monitoring with SU errors, no interrupting hits");
		
		system.adcSim.setADUnlimited();
		system.adcSim.max_adc_hits = 0;
		
		int tot_errors = 0;
		
		do {
			if (tot_errors == 4) Dpu.setCheckCurrentErrors(5);

			if (tot_errors == max_errors - 1) system.suSim.v_down_errors = 1;
			/* The V_DOWN error has a dramatic effect, so
			 * we save it for last.
			 */

			for (int sec = 1; sec <= 180; sec ++) {
				monitorHealth (Prob6d);
			}

			if (TelecommandExecutionTask.getTelemetryData().getErrorStatus() != 0) {
				tot_errors++;

				if (Harness.TRACE)
					Harness.trace(String.format("Monitoring (6d) error %d, error status %x",
							tot_errors, (int)TelecommandExecutionTask.getTelemetryData().getErrorStatus() & 0xff));
				clearErrors ();
			}
		} while (tot_errors < max_errors);

		checkNoErrors ();
	}
	
	private void testMonitorWithErrorsAndHits() {
		testcase("Monitoring with any kind of error and interrupting hit");

		system.adcSim.setADUnlimited();

		int tot_errors = 0;

		do {
			if (tot_errors == 4) Dpu.setCheckCurrentErrors(5);

			if (tot_errors == max_errors - 1) system.suSim.v_down_errors = 1;
			/* The V_DOWN error has a dramatic effect, so
			 * we save it for last.
			 */

			for (int sec = 1; sec <= 180; sec ++) {
				system.adcSim.max_adc_hits = AcquisitionTask.HIT_BUDGET_DEFAULT;
				if (sec > 2*tot_errors) system.adcSim.ad_random_failures = 2;
				monitorHealth (Prob6e);
			}

			if (TelecommandExecutionTask.getTelemetryData().getErrorStatus() != 0) {
				tot_errors ++;

				if (Harness.TRACE)
					Harness.trace(String.format("Monitoring (6e) error %d, error status %x",
							tot_errors, (int)TelecommandExecutionTask.getTelemetryData().getErrorStatus() & 0xff));
				clearErrors ();
			}
		} while (tot_errors < max_errors);

		checkNoErrors ();
	}
}
