package debie.harness;

import debie.particles.SensorUnit.SensorUnitState;
import debie.support.Dpu;
import debie.target.SensorUnitDev;

import static debie.harness.Harness.*;
import static debie.target.TcTmDev.*;
import static debie.telecommand.TcAddress.*;
import static debie.target.SensorUnitDev.NUM_SU;
import static debie.telecommand.TelecommandExecutionTask.MAX_QUEUE_LENGTH;

import debie.telecommand.TelecommandExecutionTask;
import debie.telecommand.TelemetryData;
import debie.telecommand.TelecommandExecutionTask.TC_State;

/** <p>These tests are constructed as a "nominal operation"
 * scenario, which incidentally tests the AcquisitionTask
 * and the Hit Trigger ISR, as well as other functions.</p>
 * The scenario is:
 * <ul>
 * <li/> TC to switch sensors ON
 *   <ul><li/> Run Monitoring task to drive the SU state transition.</ul>
 * <li/> TC to START ACQUISITION
 * <li/> Particle hits until the Science Data is full
 * <li/> The same number of particle hits with the Science Data full
 * <li/> Science Data TM
 *   <ul><li/> some particle hits during science TM</ul>
 * <li/> TC to enter STANDBY mode
 * <li/> TC to switch sensors OFF.
 * </ul>
 */
public class AcquisitionTest extends HarnessTest {
	
	private final int[] switchSUCmd = {			   
			SWITCH_SU_1,
			SWITCH_SU_2,
			SWITCH_SU_3,
			SWITCH_SU_4};
	/* The commands to switch Sensor Units ON or OFF. */

	public AcquisitionTest(HarnessSystem sys, TestLogger tl) {
		super(sys, tl);		
	}

	@Override
	public void runTests()
	{
		/* Test scenario for acquistion task: */
		testTurnSensorUnitsOn();
		testSwitchSuOnWhenOnFail();
		testStartAcquisition();
		testSwitchSuInAcquisitionFail();
		testStartAcqInAcqFail();
		testHitsSd();
		testHitsDuringTm();
		testSwitchToSelfTestDuringAcq();
		testStopAcq();
		testTurnSensorUnitsOff();
		reportTestResults("[AcquisitionTest]");
	}

	private void testTurnSensorUnitsOn() {
		testcase("Turn Sensor Units ON");

		/* Send the SWITCH ON commands: */

		for (int sen = 0; sen < SensorUnitDev.NUM_SU; sen ++)
		{
			checkEquals ("state=off", acqTask.getSensorUnitState(sen), SensorUnitState.off_e);

			execTC (switchSUCmd[sen], ON_VALUE, Prob4a);

			checkNoErrors ();

			checkEquals ("state=start-switching", acqTask.getSensorUnitState(sen), SensorUnitState.start_switching_e);
		}

		/* Prevent all errors in Monitoring: */

		system.adcSim.setADNominal ();
		system.adcSim.max_adc_hits = 0;
		system.adcSim.ad_random_failures   = 0;
		Dpu.setCheckCurrentErrors(0);
		system.suSim.v_down_errors        = 0;

		/* Run Health Monitoring to drive the SUs ON: */

		monitorHealth (Prob6a);

		for (int sen = 0; sen < SensorUnitDev.NUM_SU; sen ++)
			checkEquals("sensor state sen == switching_e", acqTask.getSensorUnitState(sen), SensorUnitState.switching_e);

		monitorHealth (Prob6a);

		for (int sen = 0; sen < SensorUnitDev.NUM_SU; sen ++)
			checkEquals("sensor state sen == on_e", acqTask.getSensorUnitState(sen), SensorUnitState.on_e);

	}		

	private void testSwitchSuOnWhenOnFail() {
		testcase("SWITCH_SU_ON when already ON, fail");

		execTC (SWITCH_SU_2, ON_VALUE, Prob4a);

		checkEquals ("error_status is TC_ERROR", tctmTask.getErrorStatus(), TC_ERROR);
		checkEquals ("sensor unit 1 is ON", acqTask.getSensorUnitState(1), SensorUnitState.on_e);

		clearErrors ();
	}

	private void testStartAcquisition() {
		testcase("Start Acquisition");

		checkMode(TelemetryData.STAND_BY);

		execTC (START_ACQUISITION, START_ACQUISITION, Prob4a);

		checkNoErrors ();
		checkMode(TelemetryData.ACQUISITION);

		for (int sen = 0; sen < SensorUnitDev.NUM_SU; sen ++)
			checkEquals("sensor state sen == acquisition_e", acqTask.getSensorUnitState(sen), SensorUnitState.acquisition_e);
	}

	private void testSwitchSuInAcquisitionFail() {
		testcase("TC = SWITCH_SU in ACQUISITION, fail");

		execTC (SWITCH_SU_1, ON_VALUE, Prob4a);

		checkTcError();

		clearErrors ();
	}

	private void testStartAcqInAcqFail() {
		testcase("TC = START_ACQUISITION in ACQUISITION, fail");

		execTC (START_ACQUISITION, START_ACQUISITION, Prob4a);

		checkTcError();
		checkMode(TelemetryData.ACQUISITION);

		clearErrors();

	}
	private void testHitsSd() {
		testcase("Hits with Science Data not full");

		system.adcSim.setADDelay(2);

		int hits = 0;
		while (tctmTask.hasFreeSlot())
		{
			hits ++;
			hmTask.getInternalTime().incr();
			acqTask.setHitBudgetLeft(10);

			system.suSim.randomEvent();

			acquireHit (Prob3a, Prob5a);
		}

		if(Harness.TRACE) {
			Harness.trace(String.format("Science Data filled with %d events after %d hits.",
					tctmTask.getMaxEvents(), hits));
		}
		reportEventHisto ();

		testcase("Hits with Science Data full");

		while (hits > 0)
		{
			hits --;
			hmTask.getInternalTime().incr();
			acqTask.setHitBudgetLeft(10);

			system.suSim.randomEvent();

			acquireHit (Prob3a, Prob5b);
		}

		reportEventHisto ();
	}

	public void testHitsDuringTm() {
		testcase("Science Data TM, full Science Data, some hits during TM");

		sendTC (SEND_SCIENCE_DATA_FILE, SEND_SCIENCE_DATA_FILE);

		checkNoErrors ();
		checkTcState(TC_State.SC_TM_e);

		handleTC (Prob4a);

		checkTcState(TC_State.SC_TM_e);

		/* Absorb TM until a TM_READY message is sent to the TC task,
		 * and simulate some particle hits at the same time:
		 */

		int hits = 0;
		/* We will make MAX_QUEUE_LENGTH + 4 hits. */

		acqTask.setHitBudgetLeft(MAX_QUEUE_LENGTH + 2);

		/* Ensure that some hits are rejected for budget exhaustion. */

		checkZero (tctmTask.getEventQueueLength());

		int octets = 0;
		while (system.tctmMailbox.getMailCount() == 0)
		{
			if (! tctmTask.telemetryIndexAtEnd())
			{
				if(Harness.INSTRUMENTATION) Harness.startProblem(Prob2a);
				tctmTask.tmInterruptService() ;
				if(Harness.INSTRUMENTATION) Harness.endProblem(Prob2a);
			}
			else
			{
				if(Harness.INSTRUMENTATION) Harness.startProblem(Prob2c);
				tctmTask.tmInterruptService() ;
				if(Harness.INSTRUMENTATION) Harness.endProblem(Prob2c);
			}

			octets += 2;
			checkTcState(TC_State.SC_TM_e);

			if (hits < MAX_QUEUE_LENGTH + 4)
			{
				/* Hit during Science Data TM: */

				hmTask.getInternalTime().incr();
				system.suSim.randomEvent();
				system.suSim.setEventFlag(Dpu.ACCEPT_EVENT);

				acquireHit (Prob3a, Prob5b);

				hits ++;

				if (hits <= MAX_QUEUE_LENGTH)
					checkEquals ("event queue length = hits", tctmTask.getEventQueueLength(), hits);
				else
					checkEquals ("event queue length = max queue length",
							tctmTask.getEventQueueLength(), 
							MAX_QUEUE_LENGTH);
			}
		}

		if(Harness.TRACE) Harness.trace(String.format("Science TM octets sent %d", octets));

		checkZero (acqTask.getHitBudgetLeft());

		checkEquals( "hits == MAX_QUEUE_LENGTH + 4", hits, MAX_QUEUE_LENGTH + 4);

		/* Handle the TM_READY message: */

		handleTC (Prob4a);

		checkTcState(TC_State.TC_handling_e);

		/* Check that the queued events have been recorded: */

		checkZero (tctmTask.getEventQueueLength());

		checkEquals ("tctmTask.getFreeSlotIndex() == MAX_QUEUE_LENGTH",
				      tctmTask.getFreeSlotIndex(), MAX_QUEUE_LENGTH);

	}
	
	public void testSwitchToSelfTestDuringAcq() {
		testcase("Switch to Self Test in Acquisition mode, fail");

		checkMode(TelemetryData.ACQUISITION);
		checkNoTcError();

		execTC (SWITCH_SU_2, SELF_TEST, Prob4a);

		checkTcError();
		
		execTC (ERROR_STATUS_CLEAR, ERROR_STATUS_CLEAR, Prob4a);

		checkNoTcError();

	}

	private void testStopAcq() {
		testcase("Stop acquisition");

		execTC (STOP_ACQUISITION, STOP_ACQUISITION, Prob4a);

		checkNoErrors ();
		checkMode(TelemetryData.STAND_BY);

		for (int sen = 0; sen < NUM_SU; sen ++)
			checkEquals("sensor state sen == on_e", acqTask.getSensorUnitState(sen), SensorUnitState.on_e);

	}

	private void testTurnSensorUnitsOff() {
		testcase("Turn Sensor Units OFF");

		/* Send the SWITCH OFF commands: */

		for (int sen = 0; sen < SensorUnitDev.NUM_SU; sen ++)
		{
			execTC (switchSUCmd[sen], OFF_VALUE, Prob4a);

			checkNoErrors ();
			checkEquals("sensor state sen == off_e", acqTask.getSensorUnitState(sen), SensorUnitState.off_e);
		}
	}
	/*-- Common Tests --*/
	
	private void acquireHit (
			int hit_problem,
			int acq_problem)
	/* Invoke HandleHitTrigger followed by HandleAcquisition if the hit 
	 * was accepted in the ISR. The problem parameters define the analysis
	 * problems for this test, separately for the Hit Trigger ISR and
	 * for the Acquisition task.
	 */
	{
		triggerHit (hit_problem);

		if (system.acqMailbox.getMailCount() > 0)
		{
			if(Harness.INSTRUMENTATION) startProblem(hit_problem);
			system.acqMailbox.waitMail();
			system.acqTask.handleAcquisition (system.acqMailbox.getMessage());
			if(Harness.INSTRUMENTATION) endProblem(hit_problem);
		}
	}
	
	private void reportEventHisto ()
	/* Report the collected event counts per SU and class. */
	{
	   int /* sensor_index_t */ sen;
	   int klass;

	   if(Harness.TRACE) {
		   for (sen = 0; sen < NUM_SU; sen ++)
			   for (klass = 0; klass < TelecommandExecutionTask.NUM_CLASSES; klass ++)
			   {
				   Harness.trace(String.format("Events from SU %d, class %d: %d",
						   sen, klass, tctmTask.science_data.getEventCounter(sen,klass)));
			   }
	   }
	}

}
