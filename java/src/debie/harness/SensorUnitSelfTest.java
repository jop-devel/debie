package debie.harness;

import debie.particles.AcquisitionTask;
import debie.particles.SensorUnit.SensorUnitState;
import debie.support.Dpu;
import debie.target.TcTmDev;
import debie.telecommand.TelecommandExecutionTask;
import debie.telecommand.TelemetryData;
import debie.health.HealthMonitoringTask.Round;
import static debie.harness.Harness.*;
import static debie.telecommand.TcAddress.*;
import static debie.particles.AcquisitionTask.HIT_SELF_TEST_RESET;
import static debie.target.SensorUnitDev.NO_SU;
import static debie.target.SensorUnitDev.NUM_SU;

public class SensorUnitSelfTest extends HarnessTest {

	private SensorUnitSim suSim;
	private AdcSim adcSim;
	
	private final int[] switchSUCmd = {			   
			SWITCH_SU_1,
			SWITCH_SU_2,
			SWITCH_SU_3,
			SWITCH_SU_4};
	/* The commands to switch Sensor Units ON or OFF. */

	public SensorUnitSelfTest(HarnessSystem sys, TestLogger tl) {
		super(sys, tl);
		suSim = sys.suSim;
		adcSim = sys.adcSim;
	}

	@Override
	public void runTests() {
		suSim.setSimSelfTest(1);
		
		testTurnOnForSelfTest();
		testSwitchSU2ToSelfTest();
		testSwitchSU3ToSelfTest();
		testRunSU2SelfTest();
		testRunSU2SelfTestFail();
		testHitTriggerSelfTestCorrect();
		testHitTriggerSelfTestIncorrect();
		testHitTriggerSelfTestOther();
		testTurnOffAfterSelfTest();
		
		reportTestResults("SensorUnitSelfTest");
	}
	
	private void testTurnOnForSelfTest() {
		testcase("Turn Sensor Units ON for Self Test");

		/* Send the SWITCH ON commands: */

		for (int sen = 0; sen < NUM_SU; sen ++) {
			checkEquals("sensor state sen == off_e",
					acqTask.getSensorUnitState(sen), SensorUnitState.off_e);

			execTC(switchSUCmd[sen], ON_VALUE, Prob4a);

			checkNoErrors ();
			checkEquals("sensor state sen == start_switching_e",
						acqTask.getSensorUnitState(sen), SensorUnitState.start_switching_e);
		}

		      /* Prevent all errors in Monitoring: */

		adcSim.setADNominal();
		adcSim.max_adc_hits = 0;
		adcSim.ad_random_failures = 0;
		Dpu.setCheckCurrentErrors(0);
		suSim.v_down_errors = 0;

		/* Run Health Monitoring to drive the SUs ON: */

		monitorHealth (Prob6a);

		for (int sen = 0; sen < NUM_SU; sen ++)
			checkEquals("sensor state sen == switching_e",
						acqTask.getSensorUnitState(sen), SensorUnitState.switching_e);

		monitorHealth (Prob6a);

		for (int sen = 0; sen < NUM_SU; sen ++)
			checkEquals("sensor state sen == on_e",
						acqTask.getSensorUnitState(sen), SensorUnitState.on_e);
	}
	
	private void testSwitchSU2ToSelfTest() {
		testcase("Switch SU2 to Self Test in Standby mode");
		
		checkMode(TelemetryData.STAND_BY);		
		checkNoErrors ();

		execTC (SWITCH_SU_2, SELF_TEST, Prob4a);

		checkNoErrors ();

		checkEquals("self_test_SU_number == 2", acqTask.self_test_SU_number, 2);
		checkEquals("sensor state 1 == self_test_mon_e",
				acqTask.getSensorUnitState(1), SensorUnitState.self_test_mon_e);
	}
	
	private void testSwitchSU3ToSelfTest() {
		testcase("Switch SU3 (also) to Self Test, fail");
		
		execTC (SWITCH_SU_3, SELF_TEST, Prob4a);

		TelemetryData tmData = tctmTask.getTelemetryData();
		
		checkNonZero (tmData.getErrorStatus() & TcTmDev.TC_ERROR);

		execTC (ERROR_STATUS_CLEAR, ERROR_STATUS_CLEAR, Prob4a);

		checkZero (tmData.getErrorStatus() & TcTmDev.TC_ERROR);

		checkEquals("self_test_SU_number == 2", acqTask.self_test_SU_number, 2);

		checkNoErrors ();
	}
	
	private void testRunSU2SelfTest() {
		testcase("Run Self Test for SU2");

		/* Run Monitoring up to but not including round_7_e: */

		while (hmTask.getHealthMonRound() != Round.round_7_e) {
			monitorHealth (Prob6a);
		}

		checkEquals("self_test_SU_number == 2", acqTask.self_test_SU_number, 2);
		checkEquals("sensor state 1 == self_test_mon_e",
				acqTask.getSensorUnitState(1), SensorUnitState.self_test_mon_e);

		/* Run round_7_e of Monitoring to start Self Test: */

		monitorHealth (Prob6a);

		checkEquals("self_test_SU_number == 2", acqTask.self_test_SU_number, 2);
		checkEquals("sensor state 1 == self_test_e",
				acqTask.getSensorUnitState(1), SensorUnitState.self_test_e);

		/* Run round_6_e of Monitoring to execute Self Test: */

		checkEquals("health_mon_round == round_6_e", hmTask.getHealthMonRound(), Round.round_6_e);

		monitorHealth (Prob6a);

		checkEquals("self_test_SU_number == NO_SU", acqTask.self_test_SU_number, NO_SU);
		checkEquals("sensor state 1 == on_e",
				acqTask.getSensorUnitState(1), SensorUnitState.on_e);

		checkNoErrors();
	}
	
	private void testRunSU2SelfTestFail() {
		testcase("Run Self Test for SU2, fail");
		
		checkMode(TelemetryData.STAND_BY);		
		checkNoErrors ();

		execTC (SWITCH_SU_2, SELF_TEST, Prob4a);

		checkNoErrors ();

		checkEquals("self_test_SU_number == 2", acqTask.self_test_SU_number, 2);
		checkEquals("sensor state 1 == self_test_mon_e",
				acqTask.getSensorUnitState(1), SensorUnitState.self_test_mon_e);
		
		suSim.setSimSelfTest(0);	
		/* Force self-test to fail. */

		/* Run Monitoring up to but not including round_7_e: */
		
		while (hmTask.getHealthMonRound() != Round.round_7_e) {
			monitorHealth (Prob6a);
		}

		checkEquals("self_test_SU_number == 2", acqTask.self_test_SU_number, 2);
		checkEquals("sensor state 1 == self_test_mon_e",
				acqTask.getSensorUnitState(1), SensorUnitState.self_test_mon_e);

		/* Run round_7_e of Monitoring to start Self Test: */

		monitorHealth (Prob6a);

		checkEquals("self_test_SU_number == 2", acqTask.self_test_SU_number, 2);
		checkEquals("sensor state 1 == self_test_e",
				acqTask.getSensorUnitState(1), SensorUnitState.self_test_e);

		/* Run round_6_e of Monitoring to execute Self Test: */

		checkEquals("health_mon_round == round_6_e", hmTask.getHealthMonRound(), Round.round_6_e);

		monitorHealth (Prob6a);

		checkEquals("self_test_SU_number == NO_SU", acqTask.self_test_SU_number, NO_SU);
		checkEquals("sensor state 1 == on_e",
				acqTask.getSensorUnitState(1), SensorUnitState.on_e);
		
		checkNonZero(tctmTask.getTelemetryData().getSensorUnitStatus(1) & TelecommandExecutionTask.SELF_TEST_ERROR);
		checkEquals("error status == 0x20", tctmTask.getTelemetryData().getErrorStatus(), 0x20);
		
		clearErrors();
	}
	
	private void testHitTriggerSelfTestCorrect() {		
		testcase("Hit Trigger, SU Self Test, correct pulse");

		acqTask.setHitBudgetLeft(15);

		adcSim.setADDelay(2);
		adcSim.setADConvNum(0);

		for (int sen = 0; sen < NUM_SU; sen ++) {
			acqTask.self_test_SU_number = sen + AcquisitionTask.SU1;

			acqTask.sensorUnitState[sen] = SensorUnitState.self_test_trigger_e;

			triggerSUHit (sen, Prob3a);

			checkEquals("SU_state[sen] = self_test_e", acqTask.sensorUnitState[sen], SensorUnitState.self_test_e);

			checkEquals("mail count of acq mailbox = 1", system.acqMailbox.getMailCount(), 1);

			checkEquals("mail message of acq mailbox = sen + SU1",
					system.acqMailbox.getMessage(), sen+AcquisitionTask.SU1);

			system.acqMailbox.flushMail();
		}

		checkEquals("hit budget left = 15 - NUM_SU", acqTask.getHitBudgetLeft(), 15-NUM_SU);
	}
	
	private void testHitTriggerSelfTestIncorrect() {		
		testcase("Hit Trigger, SU Self Test, incorrect pulse");

		acqTask.setHitBudgetLeft(15);

		adcSim.setADDelay(2);
		adcSim.setADConvNum(0);

		for (int sen = 0; sen < NUM_SU; sen ++) {
			acqTask.self_test_SU_number = sen + AcquisitionTask.SU1;

			acqTask.sensorUnitState[sen] = SensorUnitState.self_test_e;

			triggerSUHit (sen, Prob3a);

			checkEquals("SU_state[sen] = self_test_e", acqTask.sensorUnitState[sen], SensorUnitState.self_test_e);

			checkEquals("mail count of acq mailbox = 1", system.acqMailbox.getMailCount(), 1);

			checkEquals("mail message of acq mailbox = ((sen + 1) | HIT_SELF_TEST_RESET)",
					system.acqMailbox.getMessage(),	(sen + 1) | HIT_SELF_TEST_RESET);

			system.acqMailbox.flushMail();
		}

		checkEquals("hit budget left = 15 - NUM_SU", acqTask.getHitBudgetLeft(), 15-NUM_SU);
	}
	
	private void testHitTriggerSelfTestOther() {		
		testcase("Hit Trigger, SU Self Test, other pulse");

		acqTask.setHitBudgetLeft(15);

		adcSim.setADDelay(2);
		adcSim.setADConvNum(0);

		for (int sen = 0; sen < NUM_SU; sen ++) {
			acqTask.self_test_SU_number = sen + AcquisitionTask.SU1;
			
			acqTask.sensorUnitState[sen] = SensorUnitState.on_e;

			triggerSUHit (sen, Prob3a);

			checkEquals("SU_state[sen] = on_e", acqTask.sensorUnitState[sen], SensorUnitState.on_e);
			
			checkEquals("mail count of acq mailbox = 1", system.acqMailbox.getMailCount(), 1);

			checkEquals("mail message of acq mailbox = sen + SU1",
					system.acqMailbox.getMessage(), sen+AcquisitionTask.SU1);

			system.acqMailbox.flushMail();
		}
		
		checkEquals("hit budget left = 15 - NUM_SU", acqTask.getHitBudgetLeft(), 15-NUM_SU);
		
		checkNoErrors();
	}
			
	private void testTurnOffAfterSelfTest() {
		testcase("Turn Sensor Units OFF after Self Tests");

		/* Send the SWITCH OFF commands: */

		for (int sen = 0; sen < NUM_SU; sen ++) {
			execTC (switchSUCmd[sen], OFF_VALUE, Prob4a);

			checkNoErrors ();
			checkEquals("SU_state[sen] = off_e", acqTask.sensorUnitState[sen], SensorUnitState.off_e);
		}		
	}
}
