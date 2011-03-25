package debie.harness;

import debie.particles.SensorUnit.SensorUnitState;
import debie.telecommand.TelemetryData;
import static debie.harness.Harness.*;
import static debie.target.SensorUnitDev.NUM_SU;
import static debie.target.SensorUnitDev.NO_SU;
import static debie.particles.AcquisitionTask.HIT_SELF_TEST_RESET;
import static debie.particles.AcquisitionTask.ADC_MAX_TRIES;
import static debie.particles.AcquisitionTask.HIT_ADC_ERROR;
import static debie.particles.AcquisitionTask.SU_NUMBER_MASK;

public class HitISRTest extends HarnessTest {

	private TelemetryData tmData;
	
	public HitISRTest(HarnessSystem sys, TestLogger tl) {
		super(sys, tl);
		tmData = system.getTelemetryData();
	}

	@Override
	public void runTests() {
		
		/* Reset the historical record: */

		tmData.hit_budget_exceedings = 0;
		
		testTriggerExhausted();
		testTriggerExhausted255();
		testTriggerBudgetLeft();
		testTriggerBudgetLeftSelfTestOk();
		testTriggerBudgetLeftSelfTestWrong();
		testTriggerBudgetLeftAtLimits();
		testTriggerBudgetLeftOneFailure();
		testTriggerBudgetLeftAnyFailures();
		
		reportTestResults("HitISRTest");
		
		/* More tests in SU_Self_Test_Tests. */
	}

	private void testTriggerExhausted() {		
		testcase("Hit Trigger, budget exhausted");

		checkEquals("Hit budget exceedings = 0", tmData.hit_budget_exceedings, 0);

		acqTask.setHitBudgetLeft(0);

		/* Once: */

		triggerSUHit (0, Prob3a);

		checkEquals("mail count of acq mailbox = 0", system.acqMailbox.getMailCount(), 0);

		checkEquals("Hit budget exceedings = 1", tmData.hit_budget_exceedings, 1);

		/* Once more: */

		triggerSUHit (1, Prob3a);

		checkEquals("mail count of acq mailbox = 0", system.acqMailbox.getMailCount(), 0);

		checkEquals("Hit budget exceedings = 2", tmData.hit_budget_exceedings, 2);
	}
	
	private void testTriggerExhausted255() {		
		testcase("Hit Trigger, budget exhausted for the 255th and 256th time");

		tmData.hit_budget_exceedings = 254;

		acqTask.setHitBudgetLeft(0);

		/* 255th time: */

		triggerSUHit (3, Prob3a);

		checkEquals("mail count of acq mailbox = 0", system.acqMailbox.getMailCount(), 0);

		checkEquals("Hit budget exceedings = 255", tmData.hit_budget_exceedings, 255);

		/* 256th time: */

		triggerSUHit (1, Prob3a);

		checkEquals("mail count of acq mailbox = 0", system.acqMailbox.getMailCount(), 0);

		checkEquals("Hit budget exceedings = 255", tmData.hit_budget_exceedings, 255);
	}
	
	private void testTriggerBudgetLeft() {
		testcase("Hit Trigger, budget left, no A/D errors");

		acqTask.setHitBudgetLeft(15);

		system.adcSim.setADDelay(2);
		system.adcSim.setADConvNum(0);

		for (int su = 0; su < NUM_SU; su ++) {
			triggerSUHit (su, Prob3a);

			checkEquals("mail count of acq mailbox = 1", system.acqMailbox.getMailCount(), 1);

			checkEquals("mail message of acq mailbox = su + 1", system.acqMailbox.getMessage(), su+1);

			system.acqMailbox.flushMail();
		}

		checkEquals("hit budget left = 15 - NUM_SU", acqTask.getHitBudgetLeft(), 15-NUM_SU);
	}
	
	private void testTriggerBudgetLeftSelfTestOk() {
		testcase("Hit Trigger, budget left, no A/D errors, SU self test ok");

		for (int su = 0; su < NUM_SU; su++) {
			/* Right self test pulse: */

			acqTask.self_test_SU_number = su + 1;

			acqTask.sensorUnitState[su] = SensorUnitState.self_test_trigger_e;

			triggerSUHit (su, Prob3a);

			checkEquals("mail count of acq mailbox = 1", system.acqMailbox.getMailCount(), 1);

			checkEquals("mail message of acq mailbox = self_test_SU_number",
					system.acqMailbox.getMessage(), acqTask.self_test_SU_number);

			checkEquals("SU_state[su] = self_test_e", acqTask.sensorUnitState[su], SensorUnitState.self_test_e);

			system.acqMailbox.flushMail();
		}		
	}
	
	private void testTriggerBudgetLeftSelfTestWrong() {
		testcase("Hit Trigger, budget left, no A/D errors, SU self test wrong");

		for (int su = 0; su < NUM_SU; su++) {
			/* Wrong self test pulse: */

			acqTask.self_test_SU_number = su + 1;

			acqTask.sensorUnitState[su] = SensorUnitState.self_test_e;

			triggerSUHit (su, Prob3a);

			checkEquals("mail count of acq mailbox = 1", system.acqMailbox.getMailCount(), 1);

			checkEquals("mail message of acq mailbox = self_test_SU_number | HIT_SELF_TEST_RESET",
					system.acqMailbox.getMessage(),
					acqTask.self_test_SU_number | HIT_SELF_TEST_RESET);

			checkEquals("SU_state[su] = self_test_e", acqTask.sensorUnitState[su], SensorUnitState.self_test_e);

			system.acqMailbox.flushMail();
		}
		
		/* Reset the SU states: */

		acqTask.self_test_SU_number = NO_SU;

		for (int su = 0; su < NUM_SU; su++)
			acqTask.sensorUnitState[su] = SensorUnitState.off_e;		
	}
	
	private void testTriggerBudgetLeftAtLimits() { 
		testcase("Hit Trigger, budget left, all A/D delays at limit but ok");

		acqTask.setHitBudgetLeft(15);

		system.adcSim.setADDelay(ADC_MAX_TRIES);

		system.adcSim.setADConvNum(0);

		for (int su = 0; su < NUM_SU; su ++) {
			
			triggerSUHit (su, Prob3b);

			checkEquals("mail count of acq mailbox = 1", system.acqMailbox.getMailCount(), 1);

			checkEquals("mail message of acq mailbox = su + 1",	system.acqMailbox.getMessage(),	su+1);
						// | HIT_ADC_ERROR));

			system.acqMailbox.flushMail();
		}

		checkEquals("hit budget left = 15 - NUM_SU", acqTask.getHitBudgetLeft(), 15-NUM_SU);
	}
	
	private void testTriggerBudgetLeftOneFailure() {
		testcase("Hit Trigger, budget left, one A/D failure, others at limit");
		
		acqTask.setHitBudgetLeft(15);

		system.adcSim.ad_conv_delay[0] = ADC_MAX_TRIES + 1;
		
		for (int su = 0; su < NUM_SU; su ++) {
			system.adcSim.setADConvNum(su);
			/* Offset starting index to make a different channel fail. */

			triggerSUHit (su, Prob3b);

			checkEquals("mail count of acq mailbox = 1", system.acqMailbox.getMailCount(), 1);

			checkEquals("mail message of acq mailbox = ((su + 1) | HIT_ADC_ERROR)",
					system.acqMailbox.getMessage(),	(su + 1) | HIT_ADC_ERROR);

			system.acqMailbox.flushMail();
		}

		checkEquals("hit budget left = 15 - NUM_SU", acqTask.getHitBudgetLeft(), 15-NUM_SU);
	}
	
	private void testTriggerBudgetLeftAnyFailures() {
		testcase("Hit Trigger, budget left, any number of A/D failures");

		acqTask.setHitBudgetLeft(80);

		int su = NUM_SU - 1;

		while (acqTask.getHitBudgetLeft() > 0) {
			system.adcSim.randomADDelay ();

			triggerSUHit (su, Prob3c);

			checkEquals("mail count of acq mailbox = 1", system.acqMailbox.getMailCount(), 1);

			checkEquals("mail message of acq mailbox & SU_NUMBER_MASK = su + 1",
					system.acqMailbox.getMessage() & SU_NUMBER_MASK, su + 1);

			system.acqMailbox.flushMail();

			if (su > 0)
				su --;
			else
				su = NUM_SU - 1;
		}
	}
}