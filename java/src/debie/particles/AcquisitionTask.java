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

import debie.harness.HarnessSystem;
import debie.health.HealthMonitoringTask;
import debie.particles.SensorUnit.SensorUnitState;
import debie.support.DebieSystem;
import debie.support.Dpu;
import debie.support.KernelObjects;
import debie.support.Mailbox;
import debie.support.TaskControl;
import debie.target.AdConverter;
import debie.target.HwIf;
import debie.target.SensorUnitDev;
import debie.target.SensorUnitDev.Delays;
import debie.target.SensorUnitDev.SensorUnitTestLevel;
import debie.telecommand.TelecommandExecutionTask;
import joprt.SwEvent;

/**
 * Purpose : Implements the Acquisition task. Interface : inputs - Acquisition
 * task mailbox - Mail from Hit Trigger interrupt service - Buffer with sampled
 * Peak detector outputs - Housekeeping Telemetry registers outputs - Science
 * data Scheduling : Event-triggered task, triggered by mail to the Acquisition
 * task mailbox.
 */
public class AcquisitionTask {

	/* FIXME: Where to initialize the mailbox? */
	// ACQ_mail.mailbox_number = ACQUISITION_MAILBOX;
	// ACQ_mail.message = &trigger_unit;
	// ACQ_mail.timeout = 0;

	/*
	 * TODO: It would be nice if we had an interface for tasks in joprt. With
	 * the current class hierarchy, we have two options: (a) Fix tasks early to
	 * be either event or time-triggered (RtThread or SwEvent) (b) Create a
	 * wrapper class which delegates requests to the task implementation As (a)
	 * seems like a very bad choice, we went for the second option here. XXX:
	 * Maybe it would be worthwhile to add a MailHandler interface and a
	 * MailboxEvent class [discuss]
	 */
	public class AcquisitionTaskSwEvent extends SwEvent {
		private AcquisitionTask task;

		/* Holds parameters for the mail waiting function. */
		private Mailbox acqMailbox;

		public AcquisitionTaskSwEvent(DebieSystem system, int priority, int minTime) {
			super(priority, minTime);
			this.acqMailbox = system.getAcqMailbox();
			this.task = new AcquisitionTask(system);
		}

		@Override
		public void handle() {
			task.handleAcquisition(acqMailbox.message);
		}
	}

	/* Maximum number of conversion start tries allowed in the HitTriggerTask */
	public static final int ADC_MAX_TRIES = 25;

	public static final int HIT_BUDGET_DEFAULT = 20;
	/* Default limit for the events handled during one Health Monitoring */
	/* period. Valid values 1 .. 255. */

	public static final int PEAK_RESET_MIN_DELAY = 1;
	/* Peak detector reset min delay: 1 10ms. */

	public static final int COUNTER_RESET_MIN_DELAY = 1;
	/* Delay counter reset min delay: 1 10 ms. */
	/* NOTE that specifications would allow delay */
	/* of 1ms, but minimum delay that is possible */
	/* to be generated with RTX is one tick = 10ms */

	public static final int SELF_TEST_DELAY = 4;
	/* This delay equals the length of 4 system cycles. */

	/* Sensor Unit numbers */
	public static final int SU1 = 1;
	public static final int SU2 = 2;
	public static final int SU3 = 3;
	public static final int SU4 = 4;

	public static final int HIT_ADC_ERROR = 0x80;
	public static final int HIT_SELF_TEST_RESET = 0x40;
	public static final int SU_NUMBER_MASK = 0x07;

	/*--- Instance Variables ---*/

	/* FIXME: Static because used in TelecommandExecutionTask */
	public static int /* sensor_number_t */self_test_SU_number = SensorUnitDev.NO_SU;

	public static int self_test_flag;

	/* By default this variable indicates that no SU self test */
	/* sequence is running. */
	/* Number of SU being self tested (SU_1, SU_2, SU_3 or SU_4) */
	/* or NO_SU if no SU is being self tested. */

	private int /* unsigned char */test_channel;
	/* Channel being tested in SU Self Test. Valid only if triggering SU */
	/* (indicated by self_test_SU) is in Self Test state. */

	private SensorUnitTestLevel /* SU_test_level_t */test_level;

	/* Test level being used in SU Self Test. */
	/* FIXME: Made static because of the code in EventRecord */
	/* FIXME: This does not fit here well */
	public static SensorUnitState sensorUnitState[] = { SensorUnitState.off_e,
			SensorUnitState.off_e, SensorUnitState.off_e, SensorUnitState.off_e };

	private char ADC_result[] = new char[SensorUnitDev.NUM_CH]; /*
																 * XXX: was
																 * unsigned
																 * short int
																 */
	/* Used to temporarily store AD conversion results. */

	public static /* unsigned char */int confirm_hit_result;
	/* This variable indicates a hit with a high value. */

	private/* uint_least8_t */int hit_budget = HIT_BUDGET_DEFAULT;
	private/* uint_least8_t */int hit_budget_left = HIT_BUDGET_DEFAULT;

	/*-- getter/setter --*/
	public int getHitBudgetLeft() {
		return this.hit_budget_left;
	}
	public void setHitBudgetLeft(int value) {
		this.hit_budget_left = value;
	}

	public int getHitBudget() {
		return this.hit_budget;
	}
	public void setHitBudget(int value) {
		this.hit_budget = value;
	}

	public SensorUnitState getSensorUnitState(int sen) {
		return sensorUnitState[sen];
	}

	/* reference to the DEBIE system */
	private DebieSystem system;

	// void InitAcquisitionTask()
	/*
	 * Purpose : Initialize the Acquisition task. Interface : inputs - none
	 * outputs - ACQ_mail static fields. Preconditions : none Postconditions :
	 * AcqusitionTask is operational. Algorithm : - initialize task variables
	 */
	public AcquisitionTask(DebieSystem system) {
		this.system = system;
	}

	public void handleHitTrigger()
	/* Purpose        : Wait for and handle one Hit Trigger interrupt            */
	/* Interface      : inputs  - Five analog outputs from Peak Detectors        */
	/*                  outputs - Acquisition task mailbox                       */
	/*                          - Sampled ADC_result                             */
	/* Preconditions  : none                                                     */
	/* Postconditions : Message holding the number of triggering Sensor Unit is  */
	/*                  sent to Aqcuisition task.                                */
	/* Algorithm      : - wait for Hit Trigger interrupt                         */
	/*                  - Read Peak Detector outputs from hardware registers.    */
	/*                  - Sample and store these into a buffer.                  */
	/*                  - Send number of triggering Sensor Unit to Aqcuisition   */
	/*                    task mailbox.                                          */

	{
	   int /*unsigned char*/ initial_delay;
	   /* Delay before the first AD channel is selected in */
	   /* ShortDelay() units.                              */

	   int /*unsigned char*/ delay_limit;
	   /* Delay between channel selection and start of conversion in */
	   /* ShortDelay() units.                                        */

	   int /* sensor_number_t */ trigger;
	   /*Used to store Sensor Unit number, which has beem hit.                   */

	   int /* channel_t */ CH_base;
	   /* First ADC channel number for the relevant Sensor Unit.                 */

	   int /* uint_least8_t */ i;
	   /* Used in a for -loop, which reads the peak sensor outputs.              */

	   int /* unsigned char */ lsb, msb;
	   /*These variables are used to combine two bytes into one word.            */

	   int /* uint_least8_t */ conversion_try_count;
	   /*This variable stores the number of failed conversion starts.            */

	   initial_delay = TaskControl.DELAY_LIMIT(100);
	   /* Initial delay before converting first channel. */

	   delay_limit = TaskControl.DELAY_LIMIT(100);
	   /* The signal settling delay is 100 microseconds. */

	   TaskControl.waitInterrupt (KernelObjects.HIT_TRIGGER_ISR_SOURCE, 255);
	   /* Interrupt arrival is awaited.    */
	   /* Execution result is not handled. */

	   // FIXME: implement
	   // CLEAR_HIT_TRIGGER_ISR_FLAG;

	   /* Acknowledge the interrupt.            */
	   /* This bit must be cleared by software. */

	   if (hit_budget_left == 0)
	   {
	      /* Too many hit triggers during one Health Monitoring period. */

	      if (TelecommandExecutionTask.getTelemetryData().hit_budget_exceedings < 255)
	      {
	    	  TelecommandExecutionTask.getTelemetryData().hit_budget_exceedings++;
	      }
	      // FIXME: no link to sensor unit available
	      // SensorUnitSim.disableHitTrigger();

	      /* No more hit triggers will be handled before next Health */
	      /* Monitoring period starts (or DEBIE is reset).           */
	   }
	   else
	   {
	      /* Some hit budget left; this hit will be handled. */

	      hit_budget_left--;

	      confirm_hit_result = 1;
	      /*This variable indicates a hit with a high value.                  */

	      system.getHealthMonitoringTask().ADCChannelRegister &= AdConverter.BP_DOWN;
	      system.getAdcDevice().updateADCChannelReg(system.getHealthMonitoringTask().ADCChannelRegister);   
	      /*AD converter is set to unipolar mode                              */

	      system.getAdcDevice().startConversion();
	      /*Dummy cycle to set unipolar mode.                                 */

	      conversion_try_count = 0;

	      // FIXME: unimplemented
//	      while (   conversion_try_count < ADC_MAX_TRIES
//	             && END_OF_ADC != CONVERSION_ACTIVE )
//	      {
//	         conversion_try_count++;
//	         /*Conversion try counter is increased. If this counter exeeds the*/
//	         /*maximum number of conversion start tries the conversion will be*/
//	         /*dropped.                                                       */
//	      }       

	      if (self_test_SU_number != SensorUnitDev.NO_SU)
	      {
	         /* Some Sensor Unit is being Self Tested. */
	         trigger = self_test_SU_number;

	         if (sensorUnitState[self_test_SU_number - SU1] == SensorUnitState.self_test_e)
	         {
	            /* Some Sensor Unit is being Self Tested but this is */
	            /* not the right self test pulse.                    */

	            trigger |= HIT_SELF_TEST_RESET	;
	            /* Self test pulse is incorrect and an indication     */
	            /* of this is stored in to 'trigger' variable.        */
	            /* The AcquisitionTask will adjust its operation      */
	            /* based on this indication result.                   */
	         }

	         else if (sensorUnitState[self_test_SU_number - SU1] == SensorUnitState.self_test_trigger_e)
	         {
	            /* Some Sensor Unit is being Self Tested  and this is the correct. */
	            /* self test pulse.                                                */

	        	 sensorUnitState[self_test_SU_number - SU1] = SensorUnitState.self_test_e;
	            /* Indication of a succesfully received self test pulse */
	         }
	      }

	      else
	      {
	         /* There is no Sensor Unit Self Test in progress. */
	    	 // FIXME: no access to sensor units here
	    	 trigger = SU1;
//	         trigger = (    sensorUnitDev.trigger_source_0 
//	                      + 2 
//	                      * sensorUnitDev.trigger_source_1) 
//	                   + SU1;
	         /* Sensor Unit which caused the hit trigger is resolved. */
	      }   

	      CH_base = 
	         ((trigger - SensorUnitDev.SU_1)&2) * 12 + ((trigger - SensorUnitDev.SU_1)&1) * 8; 
	      /* First channel address for the given SU is calculated. */

	      TaskControl.shortDelay(initial_delay);
	      /* Delay before converting first channel. */

	      system.getHealthMonitoringTask().ADCChannelRegister =
	    	  system.getHealthMonitoringTask().ADCChannelRegister | CH_base;
	      system.getAdcDevice().updateADCChannelReg(system.getHealthMonitoringTask().ADCChannelRegister);   
	      /* First channel is selected. */

	      TaskControl.shortDelay(delay_limit);
	      /* Delay of 100 microseconds (+ function call overhead). */


	      for (i = 0; i < SensorUnitDev.NUM_CH; i++)
	      {

	    	  TaskControl.shortDelay(delay_limit);
	         /* Delay of 100 microseconds (+ function call overhead). */

	         system.getAdcDevice().startConversion();
	         /* AD conversion for the selected channel is started. */

	         system.getHealthMonitoringTask().ADCChannelRegister =
	        	 (system.getHealthMonitoringTask().ADCChannelRegister & 0xC0) | (CH_base + i + 1);
	         system.getAdcDevice().updateADCChannelReg(system.getHealthMonitoringTask().ADCChannelRegister);   
	         /* Next channel is selected. */

	         conversion_try_count = 0;

	         // FIXME: implement
//	         while (   conversion_try_count < ADC_MAX_TRIES
//	                && END_OF_ADC != CONVERSION_ACTIVE )
//	         {
//	            conversion_try_count++;
//	            /*Conversion try counter is increased. If this counter exeeds */
//	            /*the maximum number of conversion start tries the conversion */
//	            /*will be dropped.                                            */
//	         }       

	         if (conversion_try_count < ADC_MAX_TRIES)
	         {
	            msb = system.getAdcDevice().getResult();
	            /*Most significant byte is read from ADC result address.      */

	            lsb = system.getAdcDevice().getResult();
	            /*Least significant byte is read from ADC result address.     */

	            ADC_result[i] = 
	               (char)((msb << 8) | lsb);
	            /*Msb and lsb are combined into one word.                     */
	         } 

	         else
	         {
	            trigger |= HIT_ADC_ERROR;
	            /*Conversion has failed and an indication of this is stored in*/
	            /*to 'trigger' variable by setting the Most Significant Bit   */
	            /*(MSB) high. The AcquisitionTask will adjust its operation   */
	            /*based on this indication result.                            */

	            ADC_result[i] = 0;
	         }     

	      }

	      TaskControl.getMailbox(KernelObjects.ACQUISITION_MAILBOX).sendTaskMail((char)trigger, (byte)0);
	      /*The number of the Sensor unit that has caused the hit trigger     */
	      /*interrupt is sent to a mailbox for the acquisition task.          */

	   }  /* end if (hit budget left) */

	}

	/**
	 * Purpose : Acquires the data for one hit event. Interface : inputs -
	 * Acquisition task mailbox - Mail from Hit Trigger interrupt service -
	 * Buffer with sampled Peak detector outputs - Housekeeping Telemetry
	 * registers outputs - Science data Preconditions : none Postconditions :
	 * one message processed from Acquisition task mailbox Algorithm : - wait
	 * for mail to Acquisition task mailbox [in the Java version, this is done
	 * in the SwEvent wrapper] - if mail is "SU_NUMBER" - get Peak Detector
	 * Outputs sampled by the interrupt service - measure Pulse Rise Time -
	 * measure delays between trigger signals - get measurement time - get
	 * sensor unit temperatures from Housekeeping Telemetry registers -
	 * calculate time difference between Plasma1- and Plasma1+ trigger signals -
	 * calculate quality number - call RecordEvent()
	 */
	public void handleAcquisition(int trigger_unit) {

		EventRecord event;
		/* Pointer to the new event record. */

		Delays delay_counters = new Delays();
		/* This is a struct which stores the Delay Counter time data. */

		int time_delay; /* XXX: was signed int */
		/* This variable is used to store the delay from plasma 1+ to plasma 1-. */

		SensorUnitState state = SensorUnitState.off_e;
		/* Used to store sensor unit state. */

		// WaitMail(&ACQ_mail);
		/*
		 * XXX: this is done via a BoundAsynchronousEventHandler (in JOPRT:
		 * SwEvent) mechanism
		 */

		if ((trigger_unit & HIT_ADC_ERROR) != 0) {
			/*
			 * There has been an error in AD conversion in Hit trigger
			 * processing.
			 */
			system.getHealthMonitoringTask()
			      .setModeStatusError(TelecommandExecutionTask.ADC_ERROR);
		}

		if (trigger_unit == SensorUnitDev.SU_1
				|| trigger_unit == SensorUnitDev.SU_2
				|| trigger_unit == SensorUnitDev.SU_3
				|| trigger_unit == SensorUnitDev.SU_4)

		{
			state = sensorUnitState[trigger_unit - SensorUnitDev.SU_1];

			if ((state == SensorUnitState.self_test_e || state == SensorUnitState.acquisition_e)
					&& (system.getSensorUnitDevice().getEventFlag() == Dpu.ACCEPT_EVENT)) {

				// XXX: should this really be a method of tce task?
				event = system.getTelecommandExecutionTask().getFreeRecord();
				/*
				 * Get pointer to the new event record.Number of the Sensor
				 * Unit, which has been hit, is stored intoEvent Record.
				 */
				event.SU_number = (byte) (trigger_unit & 0xFF);

				/* Contents of a temporary buffer is stored into Event Record. */
				event.plasma_1_plus = ADC_result[0];
				event.plasma_1_minus = ADC_result[1];
				event.piezo_1 = ADC_result[2];
				event.piezo_2 = ADC_result[3];
				event.plasma_2_plus = ADC_result[4];

				/* Rise time counter is read in to Event Record. */
				event.rise_time = HwIf.readRiseTimeCounter();

				/* Delay counters are read in to a struct. */
				HwIf.readDelayCounters(delay_counters);

				/* Delay from plasma 1+ to PZT 1/2 is stored into Event Record. */
				event.delay_2 = delay_counters.FromPlasma1Plus;

				/* Delay from plasma 1- to PZT 1/2 is stored into Event Record. */
				event.delay_3 = delay_counters.FromPlasma1Minus;

				/*
				 * Delay from plasma 1+ to plasma 1- is calculated and stored
				 * intoEvent Record.
				 */

				time_delay = delay_counters.FromPlasma1Plus
						- delay_counters.FromPlasma1Minus;
				if (time_delay > 127) {
					event.delay_1 = 127;
					/*
					 * If the delay from plasma 1+ to plasma 1- is positive and
					 * doesn't fit into signed char 'event_record.delay_1', then
					 * the largest value for the signed char is stored instead.
					 */
				}

				else if (time_delay < -128) {
					event.delay_1 = -128;
					/*
					 * If the delay from plasma 1+ to plasma 1- is negative and
					 * doesn't fit into signed char 'event_record.delay_1', then
					 * the smallest value for the signed char is stored instead.
					 */
				}

				else {
					event.delay_1 = (byte) time_delay;
					/*
					 * Delay from plasma 1+ to plasma 1- is calculated and
					 * storedinto Event Record.
					 */
				}

				/* Measurement time is stored into Event Record. */
				event.hit_time = system.getInternalTime().getTag();

				/* Unit temperatures are stored into Event Record. */

				event.SU_temperature_1 = TelecommandExecutionTask
						.getTelemetryData().getSensorUnitTemperature(
								trigger_unit - SU1, 0);

				event.SU_temperature_2 = TelecommandExecutionTask
						.getTelemetryData().getSensorUnitTemperature(
								trigger_unit - SU1, 1);

				event.classify();
				/* New event is classified. */

				/*--- XXX: refactored and moved into method */
				event.calculateCheckSum();

				/*
				 * After the event record is filled up, it is stored into
				 * science data.
				 */
				system.getTelecommandExecutionTask().recordEvent();
			}
		}

		else {
			/* The received mail contained an invalid Sensor unit number. */
		}

		trigger_unit &= SU_NUMBER_MASK;
		/* Delete possible error bits. */

		TaskControl.waitTimeout(PEAK_RESET_MIN_DELAY);

		HwIf.resetPeakDetector(trigger_unit);
		/* Peak detector for this Sensor Unit is resetted. */

		TaskControl.waitTimeout(PEAK_RESET_MIN_DELAY);

		HwIf.resetPeakDetector(trigger_unit);
		/* Peak detector for this Sensor Unit is resetted again. */

		TaskControl.waitTimeout(COUNTER_RESET_MIN_DELAY);

		HwIf.resetDelayCounters();
		/* The Delay Counters are reset. */
	}

}
