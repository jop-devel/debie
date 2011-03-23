package debie.harness;

import debie.particles.AcquisitionTask;
import debie.particles.SensorUnit.SensorUnitState;
import debie.support.Dpu;
import debie.target.SensorUnitDev;

/** ported from harness.c:1182-1415 */
public class SensorUnitSim extends SensorUnitDev {

	/* Variables simulating the event sensors: */

	private /* unsigned char */ int hit_enabled       = 0;
	private /* unsigned char */ int trigger_flag      = 1;
	private /* unsigned char */ int event_flag        = Dpu.ACCEPT_EVENT;
	private /* unsigned char */ int trigger_source_0  = 0;
	private /* unsigned char */ int trigger_source_1  = 0;

	private /* unsigned char */ int msb_counter       = 134;
	private /* unsigned char */ int lsb1_counter      = 77;
	private /* unsigned char */ int lsb2_counter      = 88;
	private /* unsigned char */ int rise_time_counter = 102;

	private /* unsigned char */ int sim_self_test = 0;
	/* Whether to simulate (successful) SU Self Test. */

	private /* unsigned char */ int self_test_pulse = 0;
	/* The level of the SU Self Test pulse. */

	public void setSimSelfTest(int value) {
		sim_self_test = value;
	}
	
	private HarnessSystem system;

	public SensorUnitSim(HarnessSystem sys) {
		this.system = sys;
	}

	void simSelfTestTrigger () /* Sim_Self_Test_Trigger */
	/* If conditions are such that a Self Test Hit Trigger should
	 * be generated, simulate this occurrence by modifying the
	 * SU_state for the SU being tested as the Hit Trigger ISR would do.
	 * This supports the testing of the SU Self Test sequences in the
	 * Monitoring task.
	 *
	 * The simulation does not actually invoke the Hit Trigger ISR
	 * nor does it process the Self Test event. These DEBIE functions
	 * are tested by calling Hit Trigger ISR directly, not from the
	 * SU Self Test sequences.
	 */
	{
	   if ((sim_self_test   == 1)
	   &&  (hit_enabled     == 1)
	   &&  (self_test_pulse >  0)
	   &&  (system.acqTask.self_test_SU_number != NO_SU)
	   &&  (system.acqTask.sensorUnitState[system.acqTask.self_test_SU_number  - AcquisitionTask.SU1] == SensorUnitState.self_test_trigger_e))
	   {
		   if(Harness.TRACE) Harness.trace("[SensorUnitSim] SU Self Test trigger!");
		   system.acqTask.sensorUnitState[system.acqTask.self_test_SU_number - AcquisitionTask.SU1] = SensorUnitState.self_test_e;
	   }
	}


	/** Roving index for ad_random, for randomizing the event data. */
	RandomSim /* unsigned int */ rand = new RandomSim(0);


	void randomEvent () /* Random_Event */
	/* Sets random data in the event sensors. */
	{
	   /* unsigned char */ int val;

	   val = rand.nextRand();

	   trigger_flag     = (val >> 7) & 1;
	   event_flag       = (val >> 6) & 1;
	   trigger_source_0 = (val >> 5) & 1;
	   trigger_source_1 = (val >> 4) & 1;

	   msb_counter       = rand.nextRand();
	   lsb1_counter      = rand.nextRand();
	   lsb2_counter      = rand.nextRand();
	   rise_time_counter = rand.nextRand();
	}


	void setTriggerSU(/* sensor_index_t */ int unit) /* Set_Trigger_SU  */
	/* Sets the given SU in trigger_source_0/1. */
	{
		if(Harness.TRACE) Harness.trace(String.format("[SensorUnitSim] Set Trigger SU index %d", unit));

		trigger_source_0 =  unit       & 1;
		trigger_source_1 = (unit >> 1) & 1;
	}


	public void enableHitTrigger  () /* Enable_Hit_Trigger */
	{
		if(Harness.TRACE) Harness.trace("[SensorUnitSim] Enable_Hit_Trigger");
 	    hit_enabled = 1;
	    simSelfTestTrigger ();
	}

	public void disableHitTrigger () /* Disable_Hit_Trigger  */
	{
		if(Harness.TRACE) Harness.trace("[SensorUnitSim] Disable_Hit_Trigger");
 	    hit_enabled = 0;
	    simSelfTestTrigger ();
	}

	public int getHitTriggerFlag ()	{
		if(Harness.TRACE) Harness.trace("[SensorUnitSim] Hit_Trigger_Flag");
		return trigger_flag;
		/* 1 means hit trigger ITs are enabled
		 * 0 means they are disabled.
		 */
	}

	public int getMSBCounter() {
		if (Harness.TRACE) Harness.trace("[SensorUnitSim] Get_MSB_Counter");
		return msb_counter;
	}

	public int getLSB1Counter() {
		if (Harness.TRACE) Harness.trace("[SensorUnitSim] Get_LSB1_Counter");
		return lsb1_counter;
	}

	public int getLSB2Counter() {
		if (Harness.TRACE) Harness.trace("[SensorUnitSim] Get_LSB2_Counter");
		return lsb2_counter;
	}

	public int getRiseTimeCounter() {
		if (Harness.TRACE) Harness.trace("[SensorUnitSim] Rise_Time_Counter"); 
		return rise_time_counter;
	}

	public int triggerSource0() {
		if (Harness.TRACE) Harness.trace("[SensorUnitSim] Trigger_Source_0"); 
		return trigger_source_0;
	}

	public int triggerSource1() {
		if (Harness.TRACE) Harness.trace("[SensorUnitSim] Trigger_Source_1"); 
		return trigger_source_1;
	}

	/** Set the SU Self Test Channel selectors. */
	public void setSUSelfTestCh (int value) {
		if (Harness.TRACE) Harness.trace(String.format("[SensorUnitSim] Set SU Self-Test Channel %x", value)); 
	}

	/** Set the SU Self Test pulse level. */
	public void setTestPulseLevel(int level) {
		if (Harness.TRACE) Harness.trace(String.format("[SensorUnitSim] Set SU Self-Test Pulse Level %d", level));

		self_test_pulse = level;
		simSelfTestTrigger();
	}

	@Override
	public int getEventFlag() {
		if (Harness.TRACE) Harness.trace("[SensorUnitSim] Event_Flag ");
		return event_flag;
	}

	void setEventFlag(int value) {
		event_flag = value;
	}

	/** The number of consecutive error results to
	 * be returned from the next calls of V_Down.
	 */
	int v_down_errors = 0;

	public int VDown () 
	{
	   int result;

	   if (v_down_errors > 0)
	   {
	      result = 0;  /* Bad. */
	      v_down_errors --;
	   }
	   else
	      result = 1;  /* Good. */

	   if (Harness.TRACE) Harness.trace(String.format("[SensorUnitSim] V_Down %d", result));

	   return result;
	}

	public void signalPeakDetectorReset(
	   int low_reset_value,
	   int high_reset_value) {
		if (Harness.TRACE)
			Harness.trace(String.format("[SensorUnitSim] SignalPeakDetectorReset low %d, high %d",
										low_reset_value, high_reset_value));
	}
	
}
