/*------------------------------------------------------------------------------
 *
 *    Copyright (C) 1998 : Space Systems Finland Ltd.
 *
 * Space Systems Finland Ltd (SSF) allows you to use this version of
 * the DEBIE-I DPU software for the specific purpose and under the
 * specific conditions set forth in the Terms Of Use document enclosed
 * with or attached to this software. In particular, the software
 * remains the property of SSF and you must not distribute the software
 * to third parties without written and signed authorization from SSF.
 *
 *   Ported to Java by
 *   (C) 2011 : Benedikt Huber, Wolfgang Puffitsch, Martin Schoeberl 
 *
 *    System Name:   DEBIE DPU SW
 *    Based on the SSF file measure.c, rev 1.51, Wed Oct 13 19:48:50 1999.
 *      
 *- * --------------------------------------------------------------------------
 */
package debie.particles;

import debie.health.HealthMonitoringTask;
import debie.support.Dpu;
import debie.support.Mailbox;
import debie.support.TaskControl;
import debie.target.HwIf;
import debie.target.SensorUnit;
import debie.target.SensorUnit.Delays;
import debie.target.SensorUnit.SensorUnitTestLevel;
import debie.telecommand.EventRecord;
import debie.telecommand.TelecommandExecutionTask;
import joprt.SwEvent;


/** 
 * Purpose        : Implements the Acquisition task.
 * Interface      : inputs      - Acquisition task mailbox
 *                              - Mail from Hit Trigger interrupt service
 *                              - Buffer with sampled Peak detector outputs
 *                              - Housekeeping Telemetry registers
 *                  outputs     - Science data
 * Scheduling     : Event-triggered task, triggered by mail to the 
 *                  Acquisition task mailbox. */
public class AcquisitionTask {

	/* FIXME: Where to initialize the mailbox? */
//	   ACQ_mail.mailbox_number = ACQUISITION_MAILBOX;
//	   ACQ_mail.message        = &trigger_unit;
//	   ACQ_mail.timeout        = 0;

	public enum SuState {
		   off_e,               /* SU off state - power is Off.                  */
		   start_switching_e,   /* Transition to On state is starting.           */
		   switching_e,         /* Transition to On state is started.            */
		   on_e,                /* SU on state - power is On.                    */
		   self_test_mon_e,     /* Selt Test, Voltage and Temperature monitoring */
		   self_test_e,         /* Selt Test, test pulse setup.                  */
		   self_test_trigger_e, /* Self test, test pulse handling                */
		   acquisition_e        /* Power is On and Hit Events are accepted.      */
	}

	/* TODO:
	 * It would be nice if we had an interface for tasks in joprt.
	 * With the current class hierarchy, we have two options:
	 * (a) Fix tasks early to be either event or time-triggered (RtThread or SwEvent)
	 * (b) Create a wrapper class which delegates requests to the task implementation
	 * As (a) seems like a very bad choice, we went for the second option here.
	 * XXX: Maybe it would be worthwhile to add a MailHandler interface and a 
	 * MailboxEvent class [discuss]
	 */
	public class AcquisitionTaskSwEvent extends SwEvent {
		private AcquisitionTask task;

		/* Holds parameters for the mail waiting function. */
		private Mailbox ACQ_mailbox;

		public AcquisitionTaskSwEvent(HealthMonitoringTask healthMonitor,
									  Mailbox ACQ_mailbox,
									  int priority, int minTime) {
			super(priority, minTime);
			this.ACQ_mailbox = ACQ_mailbox; 
			this.task = new AcquisitionTask(healthMonitor);
		}
		
		@Override
		public void handle() {
			task.handleAcquisition(ACQ_mailbox.message);
		}
	}

	/*Maximum number of conversion start tries allowed in the HitTriggerTask*/ 
	public static final int ADC_MAX_TRIES = 25;

	public static final int HIT_BUDGET_DEFAULT = 20;
	/* Default limit for the events handled during one Health Monitoring */
	/* period. Valid values 1 .. 255.                                    */

	public static final int PEAK_RESET_MIN_DELAY = 1;
	/* Peak detector reset min delay: 1 * 10ms. */

	public static final int COUNTER_RESET_MIN_DELAY = 1;
	/* Delay counter reset min delay: 1 * 10 ms.   */
	/* NOTE that specifications would allow delay  */
	/* of 1ms, but minimum delay that is possible  */
	/* to be generated with RTX is one tick = 10ms */         

	public static final int SELF_TEST_DELAY = 4;
	/* This delay equals the length of 4 system cycles. */

	/*Sensor Unit numbers*/
	public static final int SU1 = 1;
	public static final int SU2 = 2;
	public static final int SU3 = 3;
	public static final int SU4 = 4;

	public static final int HIT_ADC_ERROR =          0x80;
	public static final int HIT_SELF_TEST_RESET =    0x40;
	public static final int SU_NUMBER_MASK =         0x07;

	/*--- Instance Variables ---*/
	public int /* sensor_number_t */ self_test_SU_number = SensorUnit.NO_SU;

	/* By default this variable indicates that no SU self test   */
	/* sequence is running.                                      */
	/* Number of SU being self tested (SU_1, SU_2, SU_3 or SU_4) */
	/* or NO_SU if no SU is being self tested.                   */

	private int /* unsigned char */ test_channel;
	/* Channel being tested in SU Self Test. Valid only if triggering SU */
	/* (indicated by self_test_SU) is in Self Test state.                */

	private SensorUnitTestLevel /* SU_test_level_t */ test_level;

	/* Test level being used in SU Self Test. */
	public SuState suState[] = { SuState.off_e, SuState.off_e, SuState.off_e, SuState.off_e };

	private char ADC_result[] = new char[SensorUnit.NUM_CH]; /* XXX: was unsigned short int */
	/*Used to temporarily store AD conversion results.                           */

	private /* unsigned char */ int confirm_hit_result;
	/*This variable indicates a hit with a high value.                           */

	private /* uint_least8_t */ int hit_budget       = HIT_BUDGET_DEFAULT;
	private  /* uint_least8_t */ int hit_budget_left  = HIT_BUDGET_DEFAULT;
	

	/*-- getter/setter --*/
	public int getHitBudgetLeft() {
		return this.hit_budget_left;
	}
	
	public void setHitBudgetLeft(int value) {
		this.hit_budget_left = value;
	}
	
	
	public SuState getSensorUnitState(int sen) {
		return suState[sen];
	}


	private HealthMonitoringTask healthMonitor;

	//void InitAcquisitionTask()
	/* Purpose        : Initialize the Acquisition task.
	 * Interface      : inputs      - none
	 *                  outputs     - ACQ_mail static fields.
	 * Preconditions  : none
	 * Postconditions : AcqusitionTask is operational.
	 * Algorithm      : - initialize task variables                              */
	public AcquisitionTask(HealthMonitoringTask healthMonitor)
	{
		this.healthMonitor = healthMonitor;
	}
	
	// FIXME: stub
	public void handelHitTrigger() {
		// TODO Auto-generated method stub
		
	}

	/**
	 * Purpose        : Acquires the data for one hit event.
	 * Interface      : inputs      - Acquisition task mailbox
	 *                              - Mail from Hit Trigger interrupt service
	 *                              - Buffer with sampled Peak detector outputs
	 *                              - Housekeeping Telemetry registers
	 *                  outputs     - Science data
	 * Preconditions  : none
	 * Postconditions : one message processed from Acquisition task mailbox
	 * Algorithm      : - wait for mail to Acquisition task mailbox
	 *                    [in the Java version, this is done in the SwEvent wrapper]
	 *                  - if mail is "SU_NUMBER"
	 *                    - get Peak Detector Outputs sampled by the interrupt
	 *                      service
	 *                    - measure Pulse Rise Time
	 *                    - measure delays between trigger signals
	 *                    - get measurement time
	 *                    - get sensor unit temperatures from Housekeeping
	 *                      Telemetry registers
	 *                    - calculate time difference between Plasma1- and
	 *                      Plasma1+ trigger signals
	 *                    - calculate quality number
	 *                    - call RecordEvent()                                   */
	public void handleAcquisition(int trigger_unit) {

		EventRecord event;
		/* Pointer to the new event record.                                       */

		Delays delay_counters = new Delays();
		/*This is a struct which stores the Delay Counter time data.              */

		int time_delay; /* XXX: was signed int */
		/*This variable is used to store the delay from plasma 1+ to plasma 1-.   */

		SuState state = SuState.off_e;   
		/* Used to store sensor unit state. */                                     

		//WaitMail(&ACQ_mail);
		/* XXX: this is done via a BoundAsynchronousEventHandler (in JOPRT: SwEvent)
		   mechanism */

		if ((trigger_unit & HIT_ADC_ERROR) != 0)
		{
			/* There has been an  error in AD conversion
			 * in Hit trigger processing.                */
			healthMonitor.setModeStatusError(TelecommandExecutionTask.ADC_ERROR);
		}

		if(trigger_unit == SensorUnit.SU_1 || 
		   trigger_unit == SensorUnit.SU_2 || 
		   trigger_unit == SensorUnit.SU_3 || 
		   trigger_unit == SensorUnit.SU_4)

		{
			state = suState[trigger_unit - SensorUnit.SU_1];

			if ((state == SuState.self_test_e || state == SuState.acquisition_e) 
					&& (Dpu.getEventFlag() == Dpu.ACCEPT_EVENT))
			{
				// XXX: should this really be a static method of telemetry?
				event = TelecommandExecutionTask.getFreeRecord();
				/* Get pointer to the new event record.
				 *Number of the Sensor Unit, which has been hit, is stored into
				 *Event Record.                                                  */
		       event.SU_number = (byte) (trigger_unit & 0xFF);

				/*Contents of a temporary buffer is stored into Event Record.    */
				event.plasma_1_plus = ADC_result[0];
				event.plasma_1_minus = ADC_result[1];
				event.piezo_1 = ADC_result[2];
				event.piezo_2 = ADC_result[3];
				event.plasma_2_plus = ADC_result[4];

				/*Rise time counter is read in to Event Record.                  */
				event.rise_time = HwIf.readRiseTimeCounter();

				/*Delay counters are read in to a struct.                        */
				HwIf.readDelayCounters(delay_counters);  

				/*Delay from plasma 1+ to PZT 1/2 is stored into Event Record.   */
				event.delay_2 = delay_counters.FromPlasma1Plus;

				/*Delay from plasma 1- to PZT 1/2 is stored into Event Record.   */
				event.delay_3 = delay_counters.FromPlasma1Minus; 

				/*Delay from plasma 1+ to plasma 1- is calculated and stored into
				 *Event Record.                                                  */

				time_delay = delay_counters.FromPlasma1Plus -
				             delay_counters.FromPlasma1Minus;  
		         if(time_delay > 127)
		         {
		            event.delay_1 = 127;
		            /*If the delay from plasma 1+ to plasma 1- is positive and
		 *doesn't fit into signed char 'event_record.delay_1', then
		 *the largest value for the signed char is stored instead.    */
		         }

		         else if(time_delay < -128)
		         {
		            event.delay_1 = -128;
		            /*If the delay from plasma 1+ to plasma 1- is negative and
		 *doesn't fit into signed char 'event_record.delay_1', then
		 *the smallest value for the signed char is stored instead.   */
		         }

		         else
		         {
		            event.delay_1 = (byte) time_delay;
		            /*Delay from plasma 1+ to plasma 1- is calculated and stored
		 *into Event Record.                                          */
		         }

		         /*Measurement time is stored into Event Record.                  */
		         event.hit_time = healthMonitor.getInternalTime().tval;

		         /*Unit temperatures are stored into Event Record.                */

		         event.SU_temperature_1 = 
		            TelecommandExecutionTask.getTelemetryData().getSensorUnitTemperatur(trigger_unit - SU1, 0);

		         event.SU_temperature_2 = 
			            TelecommandExecutionTask.getTelemetryData().getSensorUnitTemperatur(trigger_unit - SU1, 1);

		         event.classify();
		         /* New event is classified. */

		         /*--- XXX: refactored and moved into method */
		         event.calculateCheckSum();

		         /* After the event record is filled up, it is stored into science
		 * data.                                                         */
		         TelecommandExecutionTask.recordEvent();
		      }
		   }

		   else
		   {
		      /*The received mail contained an invalid Sensor unit number.        */
		   }

		   trigger_unit &= SU_NUMBER_MASK;
		   /* Delete possible error bits. */

		   TaskControl.waitTimeout(PEAK_RESET_MIN_DELAY);

		   HwIf.resetPeakDetector(trigger_unit);
		   /*Peak detector for this Sensor Unit is resetted. */       

		   TaskControl.waitTimeout(PEAK_RESET_MIN_DELAY);

		   HwIf.resetPeakDetector(trigger_unit);
		   /*Peak detector for this Sensor Unit is resetted again. */       

		   TaskControl.waitTimeout(COUNTER_RESET_MIN_DELAY);

		   HwIf.resetDelayCounters();
		   /*The Delay Counters are reset. */		
	}



}
