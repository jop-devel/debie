package debie.harness;

import debie.particles.AcquisitionTask;
import debie.particles.AcquisitionTask.SuState;
import debie.support.Dpu;
import debie.target.SensorUnit;

/** ported from harness.c:1182-1415 */
public class SensorUnitSim implements SensorUnit {

	/* Variables simulating the event sensors: */

	private /* unsigned char */ int hit_enabled       = 0;
	private /* unsigned char */ int trigger_flag      = 1;
	/* unsigned char */ int event_flag        = Dpu.ACCEPT_EVENT;
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
	   &&  (system.acqTask.suState[system.acqTask.self_test_SU_number  - AcquisitionTask.SU1] == SuState.self_test_trigger_e))
	   {
		   if(Harness.TRACE) Harness.trace("SU Self Test trigger!\n");
		   system.acqTask.suState[system.acqTask.self_test_SU_number - AcquisitionTask.SU1] = SuState.self_test_e;
	   }
	}


	/* unsigned int */ RandomSim rand = new RandomSim();
	/* Roving index for ad_random, for randomizing the event data. */


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
		if(Harness.TRACE) Harness.trace(String.format("Set Trigger SU index %d\n", unit));

		trigger_source_0 =  unit       & 1;
		trigger_source_1 = (unit >> 1) & 1;
	}


	void enableHitTrigger  () /* Enable_Hit_Trigger */
	{
		if(Harness.TRACE) Harness.trace("Enable_Hit_Trigger\n");
 	    hit_enabled = 1;
	    simSelfTestTrigger ();
	}

	void disableHitTrigger () /* Disable_Hit_Trigger  */
	{
		if(Harness.TRACE) Harness.trace("Disable_Hit_Trigger\n");
 	    hit_enabled = 0;
	    simSelfTestTrigger ();
	}



//	unsigned char Hit_Trigger_Flag (void)
//	{
//	#if defined(TRACE_HARNESS)
//	   printf ("Hit_Trigger_Flag\n");
//	#endif
//	   return trigger_flag;
//	  /* 1 means hit trigger ITs are enabled
//	   * 0 means they are disabled.
//	   */
//	}
//
//
//	unsigned char Event_Flag (void)
//	{
//	#if defined(TRACE_HARNESS)
//	   printf ("Event_Flag \n");
//	#endif
//	   return event_flag;
//	}
//
//
//	unsigned char Get_MSB_Counter (void)
//	{
//	#if defined(TRACE_HARNESS)
//	   printf ("Get_MSB_Counter\n");
//	#endif
//	   return msb_counter;
//	}
//
//
//	unsigned char Get_LSB1_Counter  (void)
//	{
//	#if defined(TRACE_HARNESS)
//	   printf ("Get_LSB1_Counter\n");
//	#endif
//	   return lsb1_counter;
//	}
//
//
//	unsigned char Get_LSB2_Counter  (void)
//	{
//	#if defined(TRACE_HARNESS)
//	   printf ("Get_LSB2_Counter\n");
//	#endif
//	   return lsb2_counter;
//	}
//
//
//	unsigned char Rise_Time_Counter (void)
//	{
//	#if defined(TRACE_HARNESS)
//	   printf ("Rise_Time_Counter\n");
//	#endif
//	   return rise_time_counter;
//	}
//
//
//	unsigned char Trigger_Source_0 (void)
//	{
//	#if defined(TRACE_HARNESS)
//	   printf ("Trigger_Source_0\n");
//	#endif
//	   return trigger_source_0;
//	}
//
//
//	unsigned char Trigger_Source_1 (void)
//	{
//	#if defined(TRACE_HARNESS)
//	   printf ("Trigger_Source_1\n");
//	#endif
//	   return trigger_source_1;
//	}
//
//
//	void Set_SU_Self_Test_Ch (unsigned char value)
//	/* Set the SU Self Test Channel selectors. */
//	{
//	#if defined(TRACE_HARNESS)
//	   printf ("Set SU Self-Test Channel %x\n", value);
//	#endif
//	}
//
//
//	void Set_Test_Pulse_Level (unsigned char level)
//	/* Set the SU Self Test pulse level. */
//	{
//	#if defined(TRACE_HARNESS)
//	   printf ("Set SU Self-Test Pulse Level %d\n", level);
//	#endif
//	   self_test_pulse = level;
//	   Sim_Self_Test_Trigger ();
//	}
//
//
//	static unsigned int v_down_errors = 0;
//	/* The number of consecutive error results to
//	 * be returned from the next calls of V_Down.
//	 */
//
//
//	unsigned char V_Down (void) 
//	{
//	   unsigned char result;
//
//	   if (v_down_errors > 0)
//	   {
//	      result = 0;  /* Bad. */
//	      v_down_errors --;
//	   }
//	   else
//	      result = 1;  /* Good. */
//
//	#if defined(TRACE_HARNESS)
//	   printf ("V_Down %d\n", result);
//	#endif
//
//	   return result;
//	}
//
//
//	void SignalPeakDetectorReset(
//	   unsigned char low_reset_value,
//	   unsigned char high_reset_value)
//	{
//	#if defined(TRACE_HARNESS)
//	   printf ("SignalPeakDetectorReset low %d, high %d\n",
//	      low_reset_value, high_reset_value);
//	#endif
//	}
	
}
