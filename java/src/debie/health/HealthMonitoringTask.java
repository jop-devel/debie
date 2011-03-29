package debie.health;

import debie.Version;
import debie.harness.Harness;
import debie.particles.AcquisitionTask;
import debie.particles.SensorUnit;
import debie.particles.SensorUnitSettings;
import debie.particles.SensorUnit.SensorUnitState;
import debie.support.DebieSystem;
import debie.support.Dpu;
import debie.support.KernelObjects;
import debie.support.TaskControl;
import debie.support.Dpu.ResetClass;
import debie.support.Dpu.Time;
import debie.target.AdConverter;
import debie.target.HwIf;
import debie.target.SensorUnitDev;
import debie.target.SensorUnitDev.TriggerSet;
import debie.telecommand.TelecommandExecutionTask;
import debie.telecommand.TelemetryData;
import joprt.RtThread;

import static debie.telecommand.TelemetryData.MODE_BITS_MASK;
import static debie.target.SensorUnitDev.*;

/**
 * Health monitoring, invoked periodically.
 * 
 * XXX: This is a runnable (not a RTThread). ok?
 */
public class HealthMonitoringTask implements Runnable {

	/** Boot wait interval: 55 x 10 ms = 550 ms */
	private static final int BOOT_WAIT_INTERVAL = 55;

	/**
	 * Sets system time interval to 10 ms, assuming a clock
	 * frequency of exactly 11.0592 MHz.
	 * SYSTEM_INTERVAL is in units of the processor cycle,
	 * and is computed as follows, in principle:
	 *     10 ms * 11.0592 MHz / 12
	 * where 12 is the number of clock cycles per processor
	 * cycle.
	 * The above calculation gives the value 9216 precisely.
	 * In practice this value makes the DPU time (updated by
	 * the Health Monitoring Task) retard by about 0.2%.
	 * This is probably due to imprecise handling of timer
	 * ticks by RTX-51. As a primitive correction, we reduce
	 * the value by 0.2% to 9198. This correction will not
	 * be exact, especially under heavy interrupt load.
	 */
	private static final int SYSTEM_INTERVAL = 9198;
	
	/**
	 * Health Monitoring interval wait: 100 x 10 ms = 1s
	 * This wait time should be waited 10 times to get
	 * period of 10s.
	 * Alternatively the execution of the Health Monitoring
	 * Task can be divided to ten steps which are executed
	 * 1 second apart from each other and then all actions
	 * of the task would be executed once in 10 seconds
	 */
	private static final int HM_INTERVAL = 100;
	
	/**
	 * When temperatures are measured this macro defines the maximum
	 * amount of tries to be used in reading and handling an AD channel in
	 * 'Read_AD_Channel()'.
	 */ 
	private static final int ADC_TEMPERATURE_MAX_TRIES = 255;

	/**
	 * When temperatures are measured this macro defines the maximum
	 * amount of tries to be used when End Of Conversion indication is
	 * waited in function 'Convert_AD()'.
	 */ 
	private static final int CONVERSION_TEMPERATURE_MAX_TRIES = 255;

	/**
	 * When voltages are measured this macro defines the maximum
	 * amount of tries to be used in reading and handling an AD channel in
	 * 'Read_AD_Channel()'.
	 */
	private static final int ADC_VOLTAGE_MAX_TRIES = 255;

	/**
	 * When voltages are measured this macro defines the maximum
	 * amount of tries to be used when End Of Conversion indication is
	 * waited in function 'Convert_AD()'.
	 */
	private static final int CONVERSION_VOLTAGE_MAX_TRIES = 255;

	/* DPU Self Test voltage measurement channel selectors. */
	/* See MeasureVoltage function.                         */
	private static final int DPU_5V_SELECTOR = 3;
	private static final int SU_1_2_P5V_SELECTOR = 0;
	private static final int SU_1_2_M5V_SELECTOR = 4;

	/* Upper and lower limits for monitoring */
	/* SU and DPU supply voltages.           */
	private static final int SU_P5V_ANA_LOWER_LIMIT =  0xBA;
	private static final int SU_P5V_ANA_UPPER_LIMIT =  0xE4;
	private static final int SU_M5V_ANA_LOWER_LIMIT =  0x0D;
	private static final int SU_M5V_ANA_UPPER_LIMIT =  0x22;
	private static final int SU_P50V_LOWER_LIMIT =     0xA8;
	private static final int SU_P50V_UPPER_LIMIT =     0xE3;
	private static final int SU_M50V_LOWER_LIMIT =     0x0E;
	private static final int SU_M50V_UPPER_LIMIT =     0x2C;
	private static final int DPU_P5V_DIG_LOWER_LIMIT = 0xBA;
	private static final int DPU_P5V_DIG_UPPER_LIMIT = 0xE4;
	
	/* Used in SU self test. */
	private static final int SELF_TEST_DONE = 0;
	private static final int SELF_TEST_RUNNING = 1;

	private static class ADCParameters {
		/* channel_t */       int ADC_channel;
		/* uint_least8_t */   int ADC_max_tries;
		/* uint_least8_t */   int conversion_max_tries;
		/* unsigned int */    int unsigned_ADC;
		/* signed   int */    int signed_ADC;
		/* unsigned char */   int AD_execution_result;
		/* sensor_number_t */ int sensor_unit;
	};
	
	private static class Channel {
		static final int channel_0_e = 0;
		static final int channel_1_e = 1;
		static final int channel_2_e = 2;
		static final int channel_3_e = 3;
		static final int channel_4_e = 4;
		static final int channel_5_e = 5;
		static final int channel_6_e = 6;
	}
	
	private static final int RESULT_OK = 1;
	public static final int CONVERSION_ACTIVE = 0;
	private static final int HIT_OCCURRED = 2;
	
	/* === Configuration === */
	/** Health Monitoring loop count. */
	public static final int HEALTH_COUNT = 9;

	/** Voltage Measurement loop count. */
	public static final int VOLTAGE_COUNT = 17;

	/** Temperature measurement loop count. Its value must equal or greater than
	 * NUM_SU, because its value defines the SU whos temperatures are to be
	 * measured. */
	public static final int TEMP_COUNT = 5;

	/** Checksum loop count. */
	public static final int CHECK_COUNT = 59;

	/** Maximum temperature (0xFA = 90 C and 0xF4 = 85C) for a Sensor Unit. */
	private static final int MAX_TEMP_1 = 0xFA;
	private static final int MAX_TEMP_2 = 0xF4;

	/** Checksum is counted for code memory 547 bytes per check round. */
	private static final int CHECK_SIZE = 547;

	/** The last code memory address to be checked in function
	 * 'CalculateChecksum'.                                   
	 * 'CODE_MEMORY_END'  should have a value smaller         
	 * than 2^16 - 1. Otherwise it will affect a 'for'        
	 * loop in 'CalculateChecksum' function in a way          
	 * that makes this loop infinite.                         */
	private static final int CODE_MEMORY_END = 0x7FFF;

	/** Limiting values used in function 'CalculateChecksum'. */
	private static final int MAX_CHECKSUM_COUNT = 59;
	private static final int MIN_CHECKSUM_COUNT = 0;

	/* Ported SU_INDEX_t */
	private static final int SU_INDEX_1 = 0;
	private static final int SU_INDEX_2 = 1;
	private static final int SU_INDEX_3 = 2;
	private static final int SU_INDEX_4 = 3;

	/* === types === */
	public static class Round {
		public static final int round_0_e = 0;
		public static final int round_1_e = 1;
		public static final int round_2_e = 2;
		public static final int round_3_e = 3;
		public static final int round_4_e = 4;
		public static final int round_5_e = 5;
		public static final int round_6_e = 6;
		public static final int round_7_e = 7;
		public static final int round_8_e = 8;
		public static final int round_9_e = 9;
	};

	/* === instance variables === */
	/* XXX: porting notes
	 * uint_least8_t -> int
	 * unsigned char -> char
	 * dpu_time_t    -> int? Time? (was uint32)
	 */
	int  health_mon_round;
	int  temp_meas_count;
	int  voltage_meas_count;
	int  checksum_count;
	
	public int getHealthMonRound() {
		return health_mon_round;
	}
	public void setHealthMonRound(int round) {
		health_mon_round = round;
	}

	public int getTempMeasCount() {
		return temp_meas_count;
	}
	public void setTempMeasCount(int count) {
		temp_meas_count = count;
	}

	public int getVoltageMeasCount() {
		return voltage_meas_count;
	}
	public void setVoltageMeasCount(int count) {
		voltage_meas_count = count;
	}

	public int getChecksumCount() {
		return checksum_count;
	}
	public void setChecksumCount(int count) {
		checksum_count = count;
	}
	
	private int code_checksum;

	private Time internal_time = new Time();

	private DebieSystem system;
	private TelemetryData tmData;
	
	public HealthMonitoringTask(DebieSystem system) {
		this.system = system;
		this.tmData = system.getTelemetryData();
	}

	@Override
	public void run() {
		for(;;) {
			handleHealthMonitor();
			RtThread.currentRtThread().waitForNextPeriod();
		}
	}

	/**
	 * Purpose        : Initialize the health monitoring for DEBIE.
	 * Interface      : inputs      - none
	 *
	 *                  outputs     - telemetry_data
	 *
	 *                  subroutines - InitSystem()
	 *                                DPU_SelfTest()
	 *                                SetMode()
	 *
	 * Preconditions  : Debie is on
	 * Postconditions : See subroutines
	 * Algorithm      :
	 *                   - Executes InitSystem()
	 *                   - Executes DPU_SelfTest()
	 *                   - Enter stand_by mode
	 * 
	 * {@see debie1-c, health#InitHealthMonitoring}
	 */
	public void initHealthMonitoring() {
		/* Initializes the system.    */ 
		initSystem();
		
		/* Execute the DPU self test. */
		DPU_SelfTest();
		
		/* Switch to Standby mode */
		setMode(TelemetryData.STAND_BY);
	}

	/**
	 * Purpose        : Executes the DPU voltage self test.
	 * Interface      : inputs      - none
	 *                  outputs     - none
	 *                  subroutines - Monitor_DPU_Voltage
	 * Preconditions  : none
	 * Postconditions : - Chosen supply voltages are measured.
	 * Algorithm      : - Chosen supply voltages are measured and monitored with
	 *                    Monitor_DPU_Voltage
	 */
	private void DPU_SelfTest() {
		monitorDPUVoltage();
	}

	/**
	 * Purpose        : Executes Boot sequence
	 * Interface      : inputs      - failed_code_address
	 *                              - failed_data_address
	 *                  outputs     - intialized state of all variables
	 *                  subroutines - SetSensorUnitOff
	 *                                GetResetClass
	 *                                SignalMemoryErros
	 *                                ResetDelayCounters
	 *                                InitClassification
	 *                                ClearErrorStatus
	 *                                ClearSoftwareError
	 *                                Set_SU_TriggerLevels
	 * Preconditions  : Init_DPU called earlier, after reset.
	 *                  Keil C startup code executed; xdata RAM initialised.
	 *                  Tasks are not yet running.
	 * Postconditions : DAS variables initialised.
	 *                  All Sensor Units are in off state
	 *                  If boot was caused by power-up reset, TM data registers
	 *                  and Science Data File are initialized
	 *                  If boot was not caused by watchdog-reset, error counters
	 *                  are cleared
	 *                  DEBIE mode is DPU_SELF_TEST
	 * Algorithm      : see below.
	 */
	public void boot() {
		
		system.getSensorUnitDevice().SU_ctrl_register |= 0x0F;
		Dpu.setDataByte(SU_CONTROL, (byte)system.getSensorUnitDevice().SU_ctrl_register);
		/* Set all Peak detector reset signals to high */

		HwIf.resetDelayCounters();
		
		TelecommandExecutionTask tctmTask = system.getTelecommandExecutionTask();

		tctmTask.max_events = HwIf.MAX_EVENTS;
		
		tctmTask.setSensorUnitOff(SU_INDEX_1, new SensorUnit());
		/* Set Sensor Unit 1 to Off state */

		tctmTask.setSensorUnitOff(SU_INDEX_2, new SensorUnit());
		/* Set Sensor Unit 2 to Off state */

		tctmTask.setSensorUnitOff(SU_INDEX_3, new SensorUnit());
		/* Set Sensor Unit 3 to Off state */

		tctmTask.setSensorUnitOff(SU_INDEX_4, new SensorUnit());
		/* Set Sensor Unit 4 to Off state */

		AdConverter adcDev = system.getAdcDevice();
		adcDev.setADCChannelRegister(adcDev.getADCChannelRegister() | 0x80);
		adcDev.updateADCChannelReg(adcDev.getADCChannelRegister());
		
		ResetClass reset_class = Dpu.getResetClass();
		
		if (reset_class != ResetClass.warm_reset_e) {
			/* We are running the PROM code unpatched, either   */
			/* from PROM or from SRAM.                          */

			Dpu.reference_checksum = Dpu.INITIAL_CHECKSUM_VALUE;
			/* 'reference_checksum' is used as a reference when */
			/* the integrity of the code is checked by          */
			/* HealthMonitoringTask. It is set to  its initial  */
			/* value here, after program code is copied from    */
			/* PROM to RAM.                                     */
		}
		 
		if (reset_class == ResetClass.power_up_reset_e) { 
			/* Data RAM was tested and is therefore garbage. */
			/* Init TM data registers and Science Data File. */

			internal_time = new Dpu.Time();

			tmData.clearAll();
			
			system.getTelecommandExecutionTask().resetEventQueueLength();
			/* Empty event queue. */

			system.getTelecommandExecutionTask().clearEvents();
			/* Clears the event counters, quality numbers  */
			/* and free_slot_index of the event records in */
			/* the science data memory.                    */

			tmData.init();
			/* Initializes thresholds, classification levels and */
			/* min/max times related to classification.          */

			tmData.clearRTXErrors();
			/* RTX error indicating registers are initialized. */

		} else if (reset_class == ResetClass.watchdog_reset_e) {
			/* Record watchdog failure in telemetry. */

			tmData.setErrorStatusRaw((byte)(tmData.getErrorStatus() | TelecommandExecutionTask.WATCHDOG_ERROR));

			tmData.incrementWatchdogFailures();
		} else if (reset_class == ResetClass.checksum_reset_e) {
			/* Record checksum failure in telemetry. */	
			
			tmData.setErrorStatusRaw((byte)(tmData.getErrorStatus() | TelecommandExecutionTask.CHECKSUM_ERROR));

			tmData.incrementChecksumFailures();
	   } else {
		   /* Soft or Warm reset. */
		   /* Preserve most of telemetry_data; clear some parts. */
		 			
		   tmData.clearErrorStatus();
		   tmData.clearSUError();
           tmData.clearRTXErrors();
           tmData.clearSoftwareError();
		   
		   tmData.clearModeBits(~MODE_BITS_MASK);
		   tmData.resetWatchdogFailures();
		   tmData.resetChecksumFailures();
		   tmData.resetTCWord();	   
		   /* Clear error status bits, error status counters */
		   /* and Command Status register.                   */

		   system.getTelecommandExecutionTask().resetEventQueueLength();
		   /* Empty event queue. */

		   system.getTelecommandExecutionTask().clearEvents();
		   /* Clears the event counters, quality numbers  */
		   /* and free_slot_index of the event records in */
		   /* the science data memory.                    */

		   tmData.init();
		   /* Initializes thresholds, classification levels and */
		   /* min/max times related to classification.          */

		   system.getAcquisitionTask().self_test_SU_number = NO_SU;
		   /* Self test SU number indicating parameter */
		   /* is set to its default value.             */                       
	   }
		
		tmData.clearModeBits(MODE_BITS_MASK);
		tmData.setModeBits(TelemetryData.DPU_SELF_TEST);
		/* Enter DPU self test mode. */
	
		/* Software version information is stored in the telemetry data. */
		tmData.setSWVersion(Version.SW_VERSION);
		
		HwIf.signalMemoryErrors();
		/* Copy results of RAM tests to telemetry_data. */

		SensorUnitDev suDev = system.getSensorUnitDevice();
		
		suDev.setTestPulseLevel(DEFAULT_TEST_PULSE_LEVEL);
		/* Initializes test pulse level. */

		suDev.setSUTriggerLevels(SU_1, tmData.getSensorUnit1());
		suDev.setSUTriggerLevels(SU_2, tmData.getSensorUnit2());
		suDev.setSUTriggerLevels(SU_3, tmData.getSensorUnit3());
		suDev.setSUTriggerLevels(SU_4, tmData.getSensorUnit4());
	}
	
	
	/**
	 * Purpose        : Monitors DPU voltages
	 * Interface      : inputs      - telemetry_data, DPU voltages
	 *                  outputs     - telemetry_data.mode_status
	 *                                telemetry_data, supply voltages
	 *                  subroutines -  SetModeStatusError
	 *                                 ExceedsLimit
	 *                                 MeasureVoltage
	 * Preconditions  : none
	 * Postconditions : DPU voltages are measured and monitored.
	 * Algorithm      :
	 *                  - Channels are checked one by one and in case of an error
	 *                    corresponding error bit is set.
	 */
	private void monitorDPUVoltage() {
		measureVoltage(DPU_5V_SELECTOR);
		measureVoltage(SU_1_2_P5V_SELECTOR);
		measureVoltage(SU_1_2_M5V_SELECTOR);

		/* Result of voltage measurement from SU_1/2 +5V is compared against */
		/* limits.          							*/

		if (exceedsLimit(tmData.getSensorUnit1().plus_5_voltage,
				SU_P5V_ANA_LOWER_LIMIT,
				SU_P5V_ANA_UPPER_LIMIT)) {
			setModeStatusError(TelecommandExecutionTask.SUPPLY_ERROR);   
		}

		/* Result of voltage measurement from SU_1/2 -5V is compared against */
		/* limits.          							*/

		if (exceedsLimit(tmData.getSensorUnit1().minus_5_voltage,
				SU_M5V_ANA_LOWER_LIMIT,
				SU_M5V_ANA_UPPER_LIMIT)) {
			setModeStatusError(TelecommandExecutionTask.SUPPLY_ERROR);   
		}

		/* Result of voltage measurement from DIG +5V is compared against    */
		/* limits.          							*/

		if (exceedsLimit(tmData.DPU_plus_5_digital,
				DPU_P5V_DIG_LOWER_LIMIT,
				DPU_P5V_DIG_UPPER_LIMIT)) {
			setModeStatusError(TelecommandExecutionTask.SUPPLY_ERROR);   
		}
	}

	/**
	 * Purpose        : Measure secondary Sensor Unit voltages.
	 * Interface      : inputs      - Channel selector, values 0 - 6
	 *                  outputs     - telemetry_data.mode_status
	 *                  subroutines - Read_AD_Channel()
	 *                                VoltageFailure()
	 * Preconditions  : none
	 * Postconditions : - Measurement results are written to telemetry.
	 *                  - If a measurement has failed, measurement error
	 *                    indication bit in mode status register is set and
	 *                    'VoltageFailure()' function is called.i.e.
	 *                    SUs related to failed ADC channel are switched
	 *                    off, SU related Bit in the Error Status Register is set
	 *                    and LV error indicating bit in the SU_Status
	 *                    register is set.
	 * Algorithm      :    - Secondary SU supply voltages are measured from
	 *                       a given channel.
	 *                     - If measurement has failed,
	 *                       measurement error indication bit in mode status
	 *                       register is set and 'VoltageFailure()'
	 *                       function is called.
	 *                     - If no errors have occurred, results are stored
	 *                       in telemetry_data.
	 */
	void measureVoltage(int channel_selector) {

		ADCParameters AD_voltage_parameters = new ADCParameters();
		/* This struct is used to hold parameters for Reading AD channels.        */

		final int voltage_channel [] = {
				0x10,0x11,0x12,0x13,0x54,0x55,0x56};
		/* This array holds parameters for setting the ADC channel for the        */
		/* measurement.                                                           */

		AD_voltage_parameters.ADC_channel = voltage_channel[channel_selector];
		/* Select the channel to be measured:                                     */
		/* channel_selector ->  ADC channel                                       */
		/*                0 ->  0x10                                              */
		/*                1 ->  0x11                                              */
		/*                2 ->  0x12                                              */
		/*                3 ->  0x13                                              */
		/*                4 ->  0x54                                              */
		/*                5 ->  0x55                                              */
		/*                6 ->  0x56                                              */

		AD_voltage_parameters.ADC_max_tries = ADC_VOLTAGE_MAX_TRIES; 
		/* When voltages are measured this variable defines the maximum        */
		/* amount of tries to be used in reading and handling an AD channel in */
		/* 'Read_AD_Channel()'.                                                */ 

		AD_voltage_parameters.conversion_max_tries = 
			CONVERSION_VOLTAGE_MAX_TRIES;
		/* When voltages are measured this variable defines the maximum        */
		/* amount of tries to be used when End Of Conversion indication is     */
		/* waited in function 'Convert_AD()'.                                  */ 

		readADChannel(AD_voltage_parameters);
		/* Voltage channel is read.                                            */

		if (AD_voltage_parameters.AD_execution_result != RESULT_OK) {
			/* An anomaly has occurred during the measurement. */

			tmData.setSoftwareError(TelecommandExecutionTask.MEASUREMENT_ERROR);         
			/* Set measurement error indication bit in */
			/* software error status register.         */
		} else {

			switch (channel_selector) {
			/* Measurement result bits 8..15 from channels involving positive      */
			/* voltages are written to telemetry.                                  */

			case Channel.channel_0_e:

				tmData.getSensorUnit1().plus_5_voltage = 
					AD_voltage_parameters.unsigned_ADC >>> 8; 
			   
				tmData.getSensorUnit2().plus_5_voltage =	
					AD_voltage_parameters.unsigned_ADC >>> 8;

					break;

			case Channel.channel_1_e:

				tmData.getSensorUnit3().plus_5_voltage = 
					AD_voltage_parameters.unsigned_ADC >>> 8; 
			   
				tmData.getSensorUnit4().plus_5_voltage =	
					AD_voltage_parameters.unsigned_ADC >>> 8;

					break;

			case Channel.channel_2_e:
			   
				tmData.SU_plus_50 = (AD_voltage_parameters.unsigned_ADC >>> 8) & 0xff; 
		         
				break;

			case Channel.channel_3_e:

				tmData.DPU_plus_5_digital = 
					(byte)(AD_voltage_parameters.unsigned_ADC >>> 8);

				break;


				/* Measurement result bits 8..15 from channels involving negative      */
				/* voltages are written to telemetry.                                  */
				/* Note that even here, the "unsigned" or "raw" conversion result is   */
				/* used; this is a requirement.                                        */

			case Channel.channel_4_e:

				tmData.getSensorUnit1().minus_5_voltage = 
					AD_voltage_parameters.unsigned_ADC >>> 8; 
			   
				tmData.getSensorUnit2().minus_5_voltage =	
					AD_voltage_parameters.unsigned_ADC >>> 8;

					break;

			case Channel.channel_5_e:
			   
				tmData.getSensorUnit3().minus_5_voltage = 
					AD_voltage_parameters.unsigned_ADC >>> 8; 
			   
				tmData.getSensorUnit4().minus_5_voltage =	
            	   AD_voltage_parameters.unsigned_ADC >>> 8;

            	   break;

			case Channel.channel_6_e:

				tmData.SU_minus_50 =
					(AD_voltage_parameters.unsigned_ADC >>> 8) & 0xff;
		      
				break;
			}
		}   
	}
	
	/**
	 * Purpose        : Delay for a (brief) duration.
	 * Interface      : inputs      - delay duration, in ShortDelay() units.
	 *                  outputs     - none.
	 *                  subroutines - ShortDelay()
	 * Preconditions  : none.
	 * Postconditions : at least "duration" time units have passed.
	 * Algorithm      : Call ShortDelay() as many times as necessary to delay
	 *                  for at least the desired duration.
	 */
	private void delayAWhile(int duration) {
	   while (duration > TaskControl.MAX_SHORT_DELAY) { // @WCA loop <= 3
		  system.getTaskControl().shortDelay(TaskControl.MAX_SHORT_DELAY);
	      duration = duration - TaskControl.MAX_SHORT_DELAY;
	      /* Since ShortDelay() has a positive constant delay term, the  */
	      /* actual total delay will be a little larger than 'duration'. */
	   }

	   if (duration > 0) {
	      /* Some delay left after the loop above. */
		  system.getTaskControl().shortDelay (duration);
	   }
	}

	/**
	 * Purpose        : Reading an ADC channel
	 * Interface      : inputs      - Address of a struct which contains
	 *                                parameters for this function.
	 *                  outputs     - Results are stored to the previously
	 *                                mentioned structure.
	 *                  subroutines - Convert()
	 * Preconditions  : Health monitoring is on.
	 *                  ADC_parameters.ADC_max_tries > 0.
	 * Postconditions : AD channels are measured and results written to a given
	 *                  structure.
	 * Algorithm      :
	 *                  while
	 *                     - Hit trigger interrupt indicating flag is resetted.
	 *                     - Handling of the given channel is started in a while
	 *                       loop.
	 *                     - Channel to be converted is selected by setting bits
	 *                       0 - 6 from ADC Channel register to value of channel.
	 *                       Channel value includes BP_UP bit to select
	 *                       unipolar/bipolar mode.t
	 *                     - Convert_AD() function executes the conversion and
	 *                       stores the results in the given struct mentioned
	 *                       earlier.
	 *                     - If Hit trigger interrupt flag indicates that no
	 *                       hit trigger interrupts have occurred during the
	 *                       channel reading, exit the while loop.
	 *                       Else continue loop from beginning, if
	 *                       predefined limit for executing the loop has not been
	 *                       reached.
	 *                     - If all the conversion tries have been used up and
	 *                       Hit trigger interrupt has corrupted the results,
	 *                       only a zero is stored with an indication of this
	 *                       occurred anomaly.
	 *                  End of loop
	 */
	private void readADChannel (ADCParameters ADC_parameters) {
	   int tries_left;
	   /* Number of attempts remaining to try conversion without */
	   /* interference from a particle hit.                      */

	   int delay_limit;
	   /* Delay between channel selection and start of conversion in */
	   /* ShortDelay() units.                                        */

	   delay_limit = system.getTaskControl().delayLimit(2000);
	   /* Set delay limit to 2ms. */

	   tries_left = ADC_parameters.ADC_max_tries;
	   /* Limits the number of conversion attempts repeated because */
	   /* of particle hit interrupts. Assumed to be at least 1.     */

	   AdConverter adcDev = system.getAdcDevice();
		  
	   while (tries_left > 0) {
		  adcDev.setConfirmHitResult(0);
	      /* Clear interrupt indicating flag.                                    */

		  adcDev.setADCChannelRegister((adcDev.getADCChannelRegister() & 0x80) | ADC_parameters.ADC_channel);
	      adcDev.updateADCChannelReg(adcDev.getADCChannelRegister());
	      /* AD Channel register is set. */
	      
	      adcDev.startConversion();
	      /* Initiate dummy cycle to set AD mode to unipolar or bipolar.         */

	      delayAWhile(delay_limit);
	      /* Wait for analog signal and MUX to settle. */

	      convertAD(ADC_parameters);
	      /* Start conversion and measurement.                                   */

	      tries_left--;
	      /* Repeat while-loop until the max number of tries. */

	      if (adcDev.getConfirmHitResult() == 0)
	      {
	         /* Conversion has NOT been interrupted by a hit trigger interrupt.  */
	         /* Exit from the while-loop.                                        */

	    	 // XXX: confuses loop bound analysis
	         // tries_left = 0;
	    	 // XXX: okay for loop bound analysis
	    	 break;
	      }
	   }

	   if (adcDev.getConfirmHitResult() != 0) {
	      /* Conversion has been interrupted by a hit trigger interrupt. Discard */
	      /* corrupted results.                                                  */
	              
	      ADC_parameters.unsigned_ADC = 0;
	      ADC_parameters.signed_ADC   = 0;

	      ADC_parameters.AD_execution_result = HIT_OCCURRED;
	      /* Store indication of an unsuccessful measurement.                  */

	   } else if (ADC_parameters.AD_execution_result == CONVERSION_ACTIVE 
			   && adcDev.getConfirmHitResult() == 0) {
	      setModeStatusError(TelecommandExecutionTask.ADC_ERROR);
	      /* ADC error indication is set because a time-out has          */
	      /* occurred during AD conversion and no hit trigger interrupt  */
	      /* has occurred.                                               */
	   }

	   /* Either RESULT_OK or CONVERSION_ACTIVE indications are already */
	   /* stored in the 'ADC_parameters -> AD_execution_result' field   */
	   /* as a result from conversion in the Convert() function.        */  
	}
	
	/**
	 * Purpose        : Monitors SU voltages
	 * Interface      : inputs      - self_test_SU_index
	 *                                telemetry_data, sensor voltages
	 *                  outputs     - telemetry_data.SU_error
	 *                  subroutines -  ExceedsLimit
	 *                                 Set_SU_Error
	 * Preconditions  : SU voltages are measured
	 * Postconditions : SU voltages are monitored.
	 * Algorithm      :
	 *                  - Channels are checked one by one and in case of an error
	 *                    corresponding error bit is set.
	 */
	private void monitorSUVoltage(int self_test_SU_index)
	{
		
	   switch (self_test_SU_index) {
	      case SU_INDEX_1:
	      case SU_INDEX_2:

	         /* Result of voltage measurement from SU_1/2 +5V is compared against */
	         /* limits.          						      */

	         if (exceedsLimit(tmData.getSensorUnit1().plus_5_voltage,
	               SU_P5V_ANA_LOWER_LIMIT,
	               SU_P5V_ANA_UPPER_LIMIT)) {
	        	 tmData.setSUError(self_test_SU_index, (byte)TelecommandExecutionTask.LV_LIMIT_ERROR);   
	         }

	         /* Result of voltage measurement from SU_1/2 -5V is compared against */
	         /* limits.          						      */
	         if (exceedsLimit(tmData.getSensorUnit1().minus_5_voltage,
	               SU_M5V_ANA_LOWER_LIMIT,
	               SU_M5V_ANA_UPPER_LIMIT)) {
	        	 tmData.setSUError(self_test_SU_index, (byte)TelecommandExecutionTask.LV_LIMIT_ERROR);   
	         }
	        
	         break;

	      case SU_INDEX_3:
	      case SU_INDEX_4:

	         /* Result of voltage measurement from SU_3/4 +5V is compared against */
	         /* limits.          						      */

	         if (exceedsLimit(tmData.getSensorUnit3().plus_5_voltage,
	               SU_P5V_ANA_LOWER_LIMIT,
	               SU_P5V_ANA_UPPER_LIMIT)) {
	        	 tmData.setSUError(self_test_SU_index, (byte)TelecommandExecutionTask.LV_LIMIT_ERROR);   
	         }

	         /* Result of voltage measurement from SU_3/4 -5V is compared against */
	         /* limits.          						      */

	         if (exceedsLimit(tmData.getSensorUnit3().minus_5_voltage,
	               SU_M5V_ANA_LOWER_LIMIT,
	               SU_M5V_ANA_UPPER_LIMIT)) {
	        	 tmData.setSUError(self_test_SU_index, (byte)TelecommandExecutionTask.LV_LIMIT_ERROR);   
	         }

	         break;
	   }

	   /* Result of voltage measurement from SU +50V is compared against */
	   /* limits.          						     */

	   if (exceedsLimit(tmData.SU_plus_50,
	         SU_P50V_LOWER_LIMIT,
	         SU_P50V_UPPER_LIMIT)) {
	      tmData.setSUError(self_test_SU_index, (byte)TelecommandExecutionTask.HV_LIMIT_ERROR);
	   }

	   /* Result of voltage measurement from SU -50V is compared against */
	   /* limits.          						     */

	   if (exceedsLimit(tmData.SU_minus_50,
	         SU_M50V_LOWER_LIMIT,
	         SU_M50V_UPPER_LIMIT)) {
		   tmData.setSUError(self_test_SU_index, (byte)TelecommandExecutionTask.HV_LIMIT_ERROR);
	   }
	}
	
	/**
	 * Purpose        : Execute SU self tests
	 * Interface      : inputs      - self_test_SU_index
	 *                  outputs     - none
	 *                  subroutines -  LowVoltageCurrent()
	 *                                 MeasureVoltage()
	 *                                 MeasureTemperature()
	 *                                 HighVoltageCurrent()
	 * Preconditions  : none
	 * Postconditions : Part of the Self Test sequence regarding temperatures,
	 *                  voltages and overcurrents is completed.
	 * Algorithm      : - V_DOWN is checked
	 *                  - Voltage channels are checked one by one and in case of
	 *                    an error corresponding error bit is set.
	 *                  - SU Temperatures and HV Status Register is checked.
	 */
	private void selfTestSU(int self_test_SU_index) {

		lowVoltageCurrent();
		/* V_DOWN is checked. */

		highVoltageCurrent(self_test_SU_index);
		/* HV Status register is checked. */

		/* SU voltages are measured */
		for (int i = Channel.channel_0_e; i <= Channel.channel_6_e; i++)
		{
			measureVoltage(i);
			/* All voltage channels are measured. */
		}

		monitorSUVoltage(self_test_SU_index);
		/* Voltage measurement results are monitored against limits. */

		measureTemperature(self_test_SU_index);
		/* SU temperatures are measured and monitored. */
	}
	
	/**
	 * Purpose        : Conversion is executed on a selected AD channel
	 * Interface      : inputs      - Address of a struct for storing the ADC
	 *                                results.
	 *                  outputs     - ADC results are stored to the given struct
	 *                  subroutines - none
	 * Preconditions  : none
	 * Postconditions : ADC results are written to a struct
	 * Algorithm      : - AD Conversion is started on the selected channel.
	 *                  - End of conversion is polled, EOC bit at I/O port 1,
	 *                    as long as the preset limit is not exceeded.
	 *                  - If limit has not been exeeded, resulting MSB and LSB of
	 *                    the conversion are read from the HW registers and
	 *                    combined into one word.
	 *                    Else end of conversion can no longer be waited. No
	 *                    results are gained, instead a zero is stored with an
	 *                    indication of this occurred anomaly.
	 */
	private void convertAD (ADCParameters ADC_parameters) {
	   int conversion_count;

	   /* Counts the amount of end of conversion polls.                          */

	   int  msb, lsb;                        
	   /* MSB and LSB of the conversion result                                   */

	   int word;
	   /*This variable is used to combine MSB and LSB bytes into one word.       */
	 
	   AdConverter adcDev = system.getAdcDevice();
	   adcDev.startConversion();
	 
	   conversion_count = 0;
	 
	   while(conversion_count < ADC_parameters.conversion_max_tries  
	          && (adcDev.endOfADC() != CONVERSION_ACTIVE)) {
	      /* Previous conversion is still active.                                */
	      conversion_count++;
	   }

	   /* There is a slight chance for the following occurrence:                 */
	   /* Conversion has ended after max_tries has been reached but before the   */
	   /* following condition loop is entered. As a result measurement is failed */
	   /* even if conversion has ended in time. The effect is dimished if the    */
	   /* max_tries is a large value i.e. end of conversion has been waited long */
	   /* enough.                                                                */

	   if (conversion_count < ADC_parameters.conversion_max_tries) {
	      /* Conversion has ended. Read AD result.                               */

	      msb = system.getAdcDevice().getResult();
	      lsb = system.getAdcDevice().getResult();

	      word = msb*256+lsb; 
	      /* Combine MSB and LSB as type 'unsigned int' in the given struct.    */

	      ADC_parameters.signed_ADC = word^0xffff8000; // XXX: is this really correct?
	      /* Store result as of type 'signed int'.                            */

	      ADC_parameters.unsigned_ADC = word;
	      /* Store result as of type 'unsigned int'.                             */

	      ADC_parameters.AD_execution_result = RESULT_OK;
	      /* Store indication of an successful measurement.                       */
	   
	   } else {
	      
	      /* Conversion has not ended in time. No results gained, store zero.    */
	      ADC_parameters.unsigned_ADC = 0;
	      ADC_parameters.signed_ADC = 0;

	      /* Store indication of an unsuccesful measurement.                     */
	      ADC_parameters.AD_execution_result = CONVERSION_ACTIVE;
	   }
	}

	/**
	 * Purpose        : Takes care of resulting actions in case of a failed
	 *                  measurement.
	 * Interface      : inputs      - 'SU_index' which contains sensor unit
	 *                                index number related to failed measurement
	 *                                or overheated SU.
	 *                  outputs     - telemetry_data.error_status register
	 *                                telemetry_data.SU_status register
	 *                  subroutines - SetSensorUnitOff()
	 * Preconditions  : There has been an anomaly
	 *                  during an ADC temperature channel measurement or
	 *                  measurement has revealed that the given SU is
	 *                  overheated.
	 * Postconditions : Selected Sensor unit is switched off and error indication
	 *                  bit is set in error_status register and SU_status
	 *                  register.
	 * Algorithm      : Following actions are taken,
	 *                     - switch off given sensor unit
	 *                     - Set Error status bit of the related SU in error
	 *                       status register.
	 *                     - Set high temperature error indicating bit in the
	 *                       SU_Status register.
	 */
	private void temperatureFailure(int SU_index)
	{
	   /* Temperature measurement has failed, actions are taken accordingly. */

	   system.getTelecommandExecutionTask().setSensorUnitOff(SU_index, new SensorUnit());
	   /* Switch off given sensor unit. */    

	   tmData.setErrorStatus((byte)(TelecommandExecutionTask.ERROR_STATUS_OFFSET << SU_index));
	   /* Set Error status bit of the related SU in error status register. */

	   tmData.setSUError(SU_index, (byte)TelecommandExecutionTask.TEMPERATURE_ERROR);
	   /* Set high temperature error indicating bit in the SU_Status */
	   /* register.                                                  */
	}	
	
	/**
	 * Purpose        : Initialize system after RTX system is started.
	 * Interface      : inputs      - none
	 *                  outputs     - none
	 *                  subroutines - SetTimeSlice()
	 *                                WaitInterval()
	 *                                CreateTask()
	 * Preconditions  : RTX is on.
	 * Postconditions : System initialization duties are carried out i.e. rtx
	 *                  tasks are activated, system clock interval is set.
	 * Algorithm      :    - Set system clock interval
	 *                     - Wait for automatic A/D converter calibration.
	 *                     - Activate the Telecommand Execution task.
	 *                     - Activate the Acquisition task.
	 *                     - Activate Hit Trigger Interrupt Service task.
	 */
	private void initSystem() {
		
		TaskControl tc = system.getTaskControl();
		
		tc.setTimeSlice(SYSTEM_INTERVAL);
//		/* Set system clock interval */

		tc.waitInterval(BOOT_WAIT_INTERVAL);
		/* Wait for automatic A/D converter calibration */

//		new_task.rtx_task_number    = TC_TM_INTERFACE_TASK;
//		new_task.task_main_function = TC_task;
		tc.createTask(KernelObjects.TC_TM_INTERFACE_TASK);
		/* Activate the Telecommand Execution task */

//		new_task.rtx_task_number    = ACQUISITION_TASK;
//		new_task.task_main_function = acq_task;
		tc.createTask(KernelObjects.ACQUISITION_TASK);
		/* Activate the Acquisition task */

//		new_task.rtx_task_number    = HIT_TRIGGER_ISR_TASK;
//		new_task.task_main_function = hit_task;
		tc.createTask(KernelObjects.HIT_TRIGGER_ISR_TASK);
		/* Activate Hit Trigger Interrupt Service task */	
	}

	/**
	 * Purpose        : Calculates memory checksum.
	 * Interface      : inputs      - 'checksum_count' gives the current
	 *                                 checksum loop cycle value,
	 *                                 MIN_CHECKSUM_COUNT <= 'checksum_count'
	 *                                                    <=  MAX_CHECKSUM_COUNT
	 *
	 *                              - 'reference_checksum' variable to verify
	 *                                correct codememory.
	 *                              - global variable 'code_not_patched'
	 *                  outputs     - 'code_checksum' is modified.
	 *                              - value of global variable
	 *                                'code_not_patched' is conditionally set.
	 *                  subroutines - Reboot()
	 * Preconditions  : none
	 * Postconditions : Changes values of variable 'code_checksum' and
	 *                  on first round variable 'code_not_patched'.
	 * 
	 *                  - In case of a checksum error,
	 *                    Soft reset is executed, if 'code_not_patched'
	 *                    indication enables this.
	 * 
	 * Algorithm      : - Health monitoring calculates checksum with XOR for
	 *                    CHECK_SIZE amount of codememory bytes at a time. At
	 *                    the end of the check cycle, i.e. 'checksum_count' = 0,
	 *                    the variable 'code_checksum'
	 *                    should be equal with 'reference_checksum', if no
	 *                    errors have occurred and code has not been patched
	 *                    during the checksum cycle.
	 *
	 *                  - If an error is detected in the code memory, Reboot()
	 *                    function is called.
	 *
	 *                  - if no anomalies have been encountered there will be no
	 *                    changes and checksum loop starts from the beginning
	 *                    i.e. on 'checksum_count' = 59, 'code_not_patched'
	 *                    is set and 'code_checksum' is initialised.
	 */
	private void calculateChecksum(int checksum_count) 
	{
	   int check_start;
	   /* This variable is used for determining the start address of a given     */
	   /* check.                                                                 */

	   int check_end;
	   /* This variable is used for determining the end address of a given check.*/

	   check_start = checksum_count * CHECK_SIZE;
	 
	   if (checksum_count == MAX_CHECKSUM_COUNT) {
	      /* This piece ends at the top memory address. */
	      check_end = CODE_MEMORY_END;
	   } else {
	      check_end = check_start + (CHECK_SIZE - 1);
	   }

	   if (checksum_count == MAX_CHECKSUM_COUNT) {  
	      
	     code_checksum = 0;
	     /* This global variable is used to store the result from each XOR     */
	     /* calculation during a code memory checksum round. It is cleared     */
	     /* here at the beginning of a new cycle and checked at the end of     */
	     /* each cycle against 'reference_checksum'.                           */

	     Dpu.code_not_patched = 1;
	     /* This global variable shows whether code is patched during a code   */
	     /* memory checksum cycle. It is set here at the beginning of a new    */
	     /* cycle and checked at the end of each cycle whether it has been     */
	     /* cleared as an indication of an executed code patching.             */
	   }

	   for (int i = check_start; i <= check_end; i++) {
	     /* It is assumed that 'CODE_MEMORY_END'  < 2^16 - 1 */
	     /* Otherwise variable 'i' can never have a value    */
	     /* larger than 'check_end' and this loop will never */
	     /* stop.                                            */             

	      code_checksum ^= Dpu.getCodeByte(i); 
	      /* XOR is counted for code memory byte under check. */
	   }

	   if (    (checksum_count == MIN_CHECKSUM_COUNT)
	        && (code_checksum  != Dpu.reference_checksum)   )
	   {
	      /* Checksum mismatch due to a memory error or */
	      /* code memory patch.                         */
	 
	      if (Dpu.code_not_patched != 0)
	      {
	         /* An anomaly has been detected in the code memory  */
	         /* area. Code has not been patched during this code */
	         /* memory checksum cycle.                           */

	         Dpu.reboot (ResetClass.checksum_reset_e);
	         /* Soft reset is executed, as global variable       */
	         /* 'code_not_patched' enables it. Note that         */
	         /* Reboot() does not return here.                   */
	      }
	   }
	   
	}   
	
	/**
	 * Purpose        : One round of health monitoring for DEBIE.
	 * Interface      : inputs      - telemetry_data
	 *
	 *                  outputs     - telemetry_data
	 *
	 *                  subroutines - UpdateTime()
	 *                                Monitor()
	 *                                UpdatePeriodCounter()
	 *                                WaitInterval()
	 *
	 * Preconditions  : Debie is on
	 * Postconditions : See subroutines
	 * Algorithm      :
	 *                  - Updates sensor unit states
	 *                  - Executes UpdateTime()
	 *                  - Calls Monitor() function
	 *                  - UpdatePeriodCounter() function advances the
	 *                    health monitoring counter
	 * Note:
	 *   In the C version, WaitInterval() is called at the end of checkHealth,
	 *   in the Java version at the end of the loop in Runnable#run
	 *   
	 * {@see debie1-c, health#HandleHealthMonitoring}
	 */
	public void handleHealthMonitor() {
		TelecommandExecutionTask tcTask = system.getTelecommandExecutionTask();
		
		tcTask.updateSensorUnitState(0);
		tcTask.updateSensorUnitState(1);
		tcTask.updateSensorUnitState(2);
		tcTask.updateSensorUnitState(3);

		updateTime();
		/* Update telemetry registers. */
		
		monitor(health_mon_round);
		/* Execute current Health Monitoring Round.                            */
		
		health_mon_round = updatePeriodCounter(health_mon_round, HEALTH_COUNT);
		/* Decrease or reset health monitor loop counter depending on its      */
		/* current and limiting values.                                        */
		
		system.getTaskControl().waitInterval(HM_INTERVAL);    
		/* Wait for next activation */
	}

	/**
	 * Purpose        : Monitors DEBIE's vital signs
	 * Interface      : inputs      - Health Monitoring Round count
	 *                  outputs     - none
	 *                  subroutines - MeasureTemperature()
	 *                                CheckCurrent()
	 *                                MeasureVoltage()
	 *                                CalculateChecksum()
	 * Preconditions  : Health monitoring is on.
	 * Postconditions : Health monitoring duties are carried out.
	 * Algorithm      : 
	 *                     - Executes the given health monitor loop round.
	 *                     - Starts three loops:
	 *                         - voltage measurement loop      180 secs
	 *                         - temperature measurement loop  60 secs
	 *                         - checksum count loop           60 secs
	 *                     The values of these counters are decreased after each
	 *                     loop cycle.
	 *
	 *                     Health monitoring loop which lasts 10 secs and is
	 *                     divided into 10 individual rounds. On each round
	 *                     some specific Health Monitoring duties are carried
	 *                     out. For example the Watchdog counter is resetted.
	 *
	 *                     Temperature measurement loop lasts 60 secs and
	 *                     consists of 6 Health Monitoring loop cycles each
	 *                     lasting 10 secs. It is executed partly on each 10 sec
	 *                     Health Monitoring cycle by measuring temperatures of
	 *                     one SU on each cycle. Measurement starts on the second
	 *                     Health Monitoring cycle and is completed after six
	 *                     cycles.
	 *
	 *                     Voltage measurement loop lasts 180 secs and consists
	 *                     of 18 Health Monitoring loop cycles each lasting 10
	 *                     secs. On each cycle some of its duties are carried
	 *                     out.
	 *
	 *                     Checksum calculation loop lasts 60 secs and consists
	 *                     of 6 Health Monitoring loop cycles each lasting 10
	 *                     secs. It is executed  partly on each Health Monitoring
	 *                     round.
	 * 
	 *    Illustration of the process:
	 * <pre>
	 *              M
	 *     _________|_________
	 *    |                   |
	 *    |R-R-R-R-R-R-R-R-R-R> : Health Monitoring loop: M
	 *    <---------------------<   Round: R = 1 sec
	 *                              M = 10*R = 10 secs
	 *
	 *          T
	 *     _____|_____
	 *    |           |
	 *    |M-M-M-M-M-M>   Temperature Measurement loop: T
	 *    <-----------<   Health Monitoring loop: M = 10 sec
	 *                    T = 6*M = 60 secs
	 *
	 *                  C 
	 *     _____________|____________     Checksum count loop: C
	 *    |                          |
	 *    |R-R-R-R-R-R-R-R-R-R>
	 *      >R-R-R-R-R-R-R-R-R-R>
	 *       >R-R-R-R-R-R-R-R-R-R>
	 *        >R-R-R-R-R-R-R-R-R-R>
	 *         >R-R-R-R-R-R-R-R-R-R>
	 *          >R-R-R-R-R-R-R-R-R-R> 
	 *                                Health Monitoring loop cycle: R = 1 sec
	 *                                C = 60*R = 60 secs
	 *
	 *                      V
	 *     _________________|__________________
	 *    |                                   |
	 *    |M-M-M-M-M-M-M-M-M-M-M-M-M-M-M-M-M-M>   Voltage Measurement loop: V
	 *    <-----------------------------------<   Health Monitoring loop: M
	 *                                            V = 18*M = 180 secs
	 * </pre>
	 */
	private void monitor(int health_mon_round) {
		calculateChecksum(checksum_count);
		/* A 1/60th part of the memory checksum is calculated.                 */

		checksum_count = updatePeriodCounter(checksum_count, CHECK_COUNT);
		/* Decrease or reset checksum counter                                  */
		/* depending on its current and limiting values.                       */

		AcquisitionTask acqTask = system.getAcquisitionTask();
		SensorUnitDev suDev = system.getSensorUnitDevice();

		switch (health_mon_round) {

		case Round.round_0_e:
			highVoltageCurrent(health_mon_round);
			/* Overcurrent indicating bits related to sensor unit 1 in HV       */
			/* status register are checked.                                     */

			temp_meas_count = updatePeriodCounter(temp_meas_count, TEMP_COUNT);
			voltage_meas_count = updatePeriodCounter(voltage_meas_count, VOLTAGE_COUNT);
			/* Decrease or reset temperature, checksum and voltage counters     */
			/* depending on their current and limiting values.                  */		   

			acqTask.setHitBudgetLeft(acqTask.getHitBudget());
			/* Health Monitoring period ends and new hit budget can be started. */

			if (suDev.getHitTriggerFlag() == 0) {
				/* Hit budget was exceeded during this ending Health Monitoring */
				/* period.                                                      */

				HwIf.resetPeakDetector(SU_1);
				HwIf.resetPeakDetector(SU_2);
				HwIf.resetPeakDetector(SU_3);
				HwIf.resetPeakDetector(SU_4);
				/* Reset all Peak detectors */

				system.getTaskControl().waitTimeout(AcquisitionTask.COUNTER_RESET_MIN_DELAY);

				suDev.enableHitTrigger();
				/* Allows a later falling edge on T2EX to cause */
				/* a Hit Trigger interrupt (i.e. to set EXF2).  */

				HwIf.resetDelayCounters();
				/* Resets the SU logic that generates Hit Triggers.    */
				/* Brings T2EX to a high level, making a new falling   */
				/* edge possible.                                      */
				/* This statement must come after the above "enable",  */
				/* since T2EX edges are not remembered by the HW from  */
				/* before the "enable", unlike normal interrupt enable */
				/* and disable masking.                                */
			}
			break;

		case Round.round_1_e:
			highVoltageCurrent(health_mon_round);
			/* Overcurrent indicating bits related to sensor unit 2 in HV       */
			/* status register are checked.                                     */
			break;

		case Round.round_2_e:
			highVoltageCurrent(health_mon_round);
			/* Overcurrent indicating bits related to sensor unit 3 in HV       */
			/* status register are checked.                                     */
			break;

		case Round.round_3_e:
			highVoltageCurrent(health_mon_round);
			/* Overcurrent indicating bits related to sensor unit 4 in HV       */
			/* status register are checked.                                     */		   
			break;

		case Round.round_4_e:
			lowVoltageCurrent();
			/* 'V_DOWN' indicator bit is checked.                               */
			break;

		case Round.round_5_e:
			if (voltage_meas_count < 7) {
				/* Seven Secondary voltage channels are measured starting when   */
				/* 'voltage_meas_count' reaches a value of 6. Last measurement is*/
				/*  executed on voltage_meas_count value 0.                      */

				measureVoltage(voltage_meas_count);
			}
			break;

		case Round.round_6_e:		   

			if ((acqTask.self_test_SU_number != NO_SU) &&
					acqTask.sensorUnitState[acqTask.self_test_SU_number - AcquisitionTask.SU1]
					                        == SensorUnit.SensorUnitState.self_test_e) {
				/* SU self test sequence continues   */

				selfTestChannel(acqTask.self_test_SU_number - AcquisitionTask.SU1);
				/* SU channels are monitored in this round. */

				acqTask.self_test_SU_number = NO_SU;
				/* SU self test sequence ends here  */			   
			}
			break;

		case Round.round_7_e:

			if (acqTask.self_test_SU_number != NO_SU) {
				/* SU self test sequence has started   */

				acqTask.self_test_flag = SELF_TEST_RUNNING;
				/* Indication of a started test. */

				selfTestSU(acqTask.self_test_SU_number - AcquisitionTask.SU1);
				/* Supply voltages and SU temperatures are monitored in this round. */

				if (acqTask.self_test_SU_number != NO_SU) {
					acqTask.sensorUnitState[acqTask.self_test_SU_number - AcquisitionTask.SU1]
					                        = SensorUnit.SensorUnitState.self_test_e;
				}   
			}	   
			break;

		case Round.round_8_e:

			//	         SET_WD_RESET_HIGH;

			/* The Watch Dog time out signal state is reset HIGH state, as it is*/
			/* falling edge active.                                             */		   
			break;

		case Round.round_9_e:

			if (temp_meas_count < NUM_SU) {
				/* Two channels of one sensor unit are measured when             */
				/* 'temp_meas_count' reaches 3 -> 2 -> 1 -> 0. I.e. measuring    */
				/*  begins after 10 secs and is finished after 50 secs.          */

				measureTemperature(temp_meas_count);
			}

			//		   SET_WD_RESET_LOW;

			/* The Watch Dog timer is reset by setting WD_RESET bit low at I/O  */
			/* port 1. This is done here with 10 sec interval. The watch dog    */
			/* time-out is 12.1 secs.                                           */
			break;
		}
	}
	   
	/**
	 * Purpose        : Measures and monitors SU temperatures
	 * Interface      : inputs      - SU_index, sensor unit index  (0 - 3)
	 *                                telemetry_data
	 *                  outputs     - telemetry_data
	 *                  subroutines - Read_AD_Channel()
	 *                                TemperatureFailure()
	 * Preconditions  : none
	 * Postconditions : - ADC temperature channels are measured.
	 *                  - In case of an overheated SU,
	 *                    'TemperatureFailure()' function is called.i.e.
	 *                    all secondary supply voltages to that SU are switched
	 *                    off, SU related Bit in the Error Status Register is set
	 *                    and temperature error indicating bit in the SU_Status
	 *                    register is set.
	 *                  - If a measurement has failed, in addition to overheating
	 *                    response also measurement error indication bit in mode
	 *                    status register is set
	 * Algorithm      : - Temperatures of a given sensor unit are measured.
	 *                  - If measured temperature is large enough, it is stored
	 *                    into telemetry.
	 *
	 *                  - Else measured temperature value is too small to be
	 *                    stored. Zero value is stored into telemetry.
	 *
	 *                  - If temperature of any of the Sensor Units is over
	 *                    MAX_TEMP, 'TemperatureFailure()' function is
	 *                    called.
	 *
	 *                  - If temperature measurement of a Sensor Unit has failed,
	 *                    temperature error indicating bit in the SU_Status
	 *                    register is set and TemperatureFailure()'
	 *                    function is called.
	 */
	private void measureTemperature(int SU_index) {
	
		ADCParameters AD_temperature_parameters = new ADCParameters();
		/* This struct is used to hold parameters for Reading AD channels.        */
	 
	   int temp_limit_value;

	   for (int j=0; j < TelecommandExecutionTask.NUM_TEMP; j++) {
		   AD_temperature_parameters.ADC_channel = 
			   5 + (SU_index&1)*8 + (SU_index&2)*12 + j;
		   /* Select the channel to be measured.                                  */

		   AD_temperature_parameters.ADC_max_tries = ADC_TEMPERATURE_MAX_TRIES; 
		   /* When temperatures are measured this variable defines the maximum    */
		   /* amount of tries to be used in reading and handling an AD channel in */
		   /* 'Read_AD_Channel()'.                                                */

		   AD_temperature_parameters.conversion_max_tries = 
			   CONVERSION_TEMPERATURE_MAX_TRIES;
		   /* When temperatures are measured this variable defines the maximum    */
		   /* amount of tries to be used when End Of Conversion indication is     */
		   /* waited in function 'Convert_AD()'.                                  */

		   readADChannel(AD_temperature_parameters);
		   /* Get ADC temperature measurement result.                             */

		   if ((AD_temperature_parameters.unsigned_ADC & 0x8000) != 0) {
			   /* Temperature is stored in the telemetry.                          */

			   tmData.setSensorUnitTemperature(SU_index, j, 
					   (AD_temperature_parameters.unsigned_ADC & 0x7FFF) >>> 7);
			   /* Store bits 7 .. 14 */
		   } else {                  

			   tmData.setSensorUnitTemperature(SU_index, j, 0);
			   /* Temperature too small -> store zero */
		   }

		   temp_limit_value = ( j==0 ? MAX_TEMP_1 : MAX_TEMP_2 );

		   if (tmData.getSensorUnitTemperature(SU_index, j) > temp_limit_value) {
			   /* Temperature has exeeded a predefined limit                       */

			   temperatureFailure(SU_index);
			   /* Given SU is switched off, error and SU status registers are      */
			   /* updated in telemetry.                                            */
		   }	

		   if (AD_temperature_parameters.AD_execution_result != RESULT_OK) {
			   /* An anomaly has occurred during the measurement.                  */

			   tmData.setSoftwareError(TelecommandExecutionTask.MEASUREMENT_ERROR);         
			   /* Set measurement error indication bit in   */
			   /* software error status register.           */   

			   temperatureFailure(SU_index);
			   /* Given SU is switched off and error and SU status registers are   */
			   /* updated in telemetry.                                            */
		   }
	   }
	}
	
	private int check_current_errors;
	
	public int getCheckCurrentErrors() {
		return check_current_errors;
	}
	public void setCheckCurrentErrors(int errors) {
		check_current_errors = errors;
	}
	
	private int checkCurrent (int bits) {
		if (Harness.TRACE) Harness.trace(String.format("[HealthMonitoringTask] Check_Current 0x%x", bits));
			
		int val;
	
		switch (bits) {
		case   3: val =  1; break;
		case  12: val =  4; break;
		case  48: val = 16; break;
		case 192: val = 64; break;
		default : val =  0;
		if (Harness.TRACE) Harness.trace(String.format("[HealthMonitoringTask] Check_Current param error"));
		break;
		}

		if (check_current_errors > 0) {
			val = ~val;  /* Wrong value => alarm. */
			check_current_errors --;
		}

		return val;
	}

	/**
	 * Purpose        : Monitors overcurrent indicating bits in the HV Status
	 *                  register for a given sensor unit.
	 * Interface      : inputs      - SU_index, sensor unit index (0 - 3)
	 *                                telemetry_data
	 *                                HV_status register
	 *                  outputs     - telemetry_data
	 *                  subroutines - SetErrorStatus()
	 *                                Set_SU_Error()
	 * Preconditions  : none
	 * Postconditions : following registers are updated in case of an error,
	 *                  - Sensor Unit or units are switched off
	 *                  - Error Status register updated
	 *                  - SU Status register updated
	 * Algorithm      :
	 *                  - if any of the HV_Status bits indicate a short
	 *                    circuit or overload, the corresponding Error Status
	 *                    Bits in the Error Status and SU_Status Registers are
	 *                    set.
	 */
	private void highVoltageCurrent(int SU_index) {
		final int SU_current_mask[] = {3,12,48,192};
		/* This array holds parameters for checking the HV status register.       */

		final int valid_value[] = {1,4,16,64};
		/* This array holds comparison parameters for checking the HV status      */
		/* register.                                                              */
	 
		if (checkCurrent(SU_current_mask[SU_index]) != 
			valid_value[SU_index]) {
			/* Overcurrent is detected.                                            */

			tmData.setErrorStatus((byte)(TelecommandExecutionTask.ERROR_STATUS_OFFSET << SU_index));
			/* Set high corresponding bit for the SU in Error Status Register.     */

			tmData.setSUError(SU_index, (byte)TelecommandExecutionTask.HV_SUPPLY_ERROR);
			/* Set high HV supply error indicating bit in the SU_Status register*/
		}  
	}

	/**
	 * Purpose        : Monitors low voltage currents in Sensor Units.
	 * Interface      : inputs      - telemetry_data
	 *                                        V_DOWN bit
	 *                  outputs     - telemetry_data
	 *                  subroutines - SetSensorUnitOff()
	 * Preconditions  : none
	 * Postconditions : Following actions are taken in case of an error,
	 *                  - Sensor Unit or units are switched off
	 *                  - SU Status register updated
	 *                  - Error Status register updated
	 * Algorithm      :
	 *                  - If V_DOWN bit in I/O port 1 is LOW indicating that
	 *                    +-5 V DC/DC converter(s) is(are) limiting the output
	 *                    current, all SU supply voltages are
	 *                    switched off and corresponding bits in the Error and
	 *                    mode Status Register are set.
	 */
	private void lowVoltageCurrent()
	{
		if (system.getSensorUnitDevice().VDown() == Dpu.LOW) {
			/*  An error is detected, output current is limited.                   */

			for (int i = 0; i < NUM_SU; i++) {
				/* Switch off all Sensor Units. */

				system.getTelecommandExecutionTask().setSensorUnitOff(i, new SensorUnit());
				/* Switch off given sensor unit.                                 */

				tmData.setSUError(i, (byte)TelecommandExecutionTask.LV_SUPPLY_ERROR);
				/* Set high LV supply error indicating bit in the SU_Status      */
				/* register.                                                     */
			}
	      
			tmData.setErrorStatus((byte)TelecommandExecutionTask.OVERALL_SU_ERROR);
			/* Set all SU error status bits in 'error status register' at telemetry.*/
	   }
	}

	/**
	 * Purpose        : This function will be called always when
	 *                  error bit(s) in the mode status register are set.
	 * Interface      : inputs      - mode status register
	 *                              - mode_status_error, which specifies what
	 *                                bit(s) are to be set in
	 *                                mode status register. Value is one of
	 *                                the following,
	 *
	 *                                  SUPPLY_ERROR
	 *                                  DATA_MEMORY_ERROR
	 *                                  PROGRAM_MEMORY_ERROR
	 *                                  MEMORY_WRITE_ERROR
	 *                                  ADC_ERROR
	 *
	 *                  outputs     - mode status register
	 *                  subroutines - none
	 * Preconditions  : none
	 * Postconditions : none
	 * Algorithm      : - Disable interrupts
	 *                  - Write to Mode Status register
	 *                  - Enable interrupts
	 */
	public void setModeStatusError(int mode_status_error) {
		TaskControl tc = system.getTaskControl();
		
		tc.disableInterruptMaster();

		tmData.setModeBits(mode_status_error & ~MODE_BITS_MASK);
		   /* The mode bits are secured against unintended modification by */
		   /* clearing those bits in 'mode_status_error' before "or":ing   */
		   /* its value to 'telemetry_data.mode_status'.                   */

		tc.enableInterruptMaster();
    }

	/**
	 * Purpose        : This function will be called always when
	 *                  mode in the mode status register is set.
	 * Interface      : inputs      - mode status register
	 *                                mode_bits, which specify the mode to be
	 *                                stored in the mode status register.
	 *                                Value is on one of the following:
	 *                                   DPU self test
	 *                                   stand by
	 *                                   acquisition
	 * 
	 *                  outputs     - mode status register
	 *                  subroutines - none
	 * Preconditions  : none
	 * Postconditions : none
	 * Algorithm      : - Disable interrupts
	 *                  - Write to Mode Status register
	 *                  - Enable interrupts
	 */
	public void setMode(int mode)	{
		TaskControl tc = system.getTaskControl();
		
		tc.disableInterruptMaster();

		tmData.clearModeBits(MODE_BITS_MASK);
		tmData.setModeBits(mode & MODE_BITS_MASK);
	   /* First mode status bits are cleared, and then the given mode is set. */

		tc.enableInterruptMaster();
	}

	/**
	 * Purpose        : Tests given value against given limits.
	 * Interface      : inputs      - value
	 *                                lower_limit
	 *                                upper_limit
	 *                  outputs     - boolean value
	 *                  subroutines - none
	 * Preconditions  : none
	 * Postconditions : Given value is tested.
	 * Algorithm      : See below, self explanatory.
	 */
	private boolean exceedsLimit(int value, int lowerLimit, int upperLimit) {
		return (value < lowerLimit || value > upperLimit);
	}
	
	
	/**
	 * Purpose        : Starts channel tests
	 * Interface      : inputs      - self_test_SU_index
	 *                  outputs     - none
	 *                  subroutines -  SetTestPulseLevel
	 *                                 SetTriggerLevel
	 *                                 RestoreSettings
	 *                                 ExecuteChannelTest
	 *                                 DisableAnalogSwitch
	 * Preconditions  : none
	 * Postconditions : SU channels are self tested.
	 *                  voltages and overcurrents is completed.
	 * Algorithm      : - Threshold level is set high.
	 *                  - Test pulse level for a given channel is set high.
	 *                  - Channels are tested.
	 *                  - Threshold level is set low.
	 *                  - Test pulse level for a given channel is set low .
	 *                  - Channels are tested.
	 *                  - A pseudo trigger is generated in order to reset peak
	 *                    detector and delay counter in AcquisitionTask.
	 *                  - Threshold levels are restored to the level prior to
	 *                    the test. SU state for the SU under test is restored
	 *                    to ON.
	 */
	private void selfTestChannel(int self_test_SU_index) {
		
	   TriggerSet test_threshold = new TriggerSet();

	   SensorUnitDev suDev = system.getSensorUnitDevice();
	
	   suDev.disableHitTrigger();

	   /* Initial parameters for SetTriggerLevel function. */
	   test_threshold.sensor_unit = system.getAcquisitionTask().self_test_SU_number;
	   test_threshold.level       = MAX_PLASMA_SELF_TEST_THRESHOLD;
	   test_threshold.channel     = PLASMA_1_PLUS;
	   suDev.setTriggerLevel(test_threshold);
	   test_threshold.channel     = PLASMA_1_MINUS;
	   suDev.setTriggerLevel(test_threshold);
	   test_threshold.channel     = PLASMA_2_PLUS;
	   suDev.setTriggerLevel(test_threshold);

	   test_threshold.level       = MAX_PIEZO_SELF_TEST_THRESHOLD;
	   test_threshold.channel     = PZT_1_2;
	   suDev.setTriggerLevel(test_threshold);


	   /* Set initial test pulse value to 0. Test pulse value is also zeroed    */
	   /* before returning from ExecuteChannelTest procedure also.              */
	   suDev.setTestPulseLevel(0);

	   /* Test threshold level is set before each channel test for every channel*/
	   /* and value is set back to the maximum threshold level before returning */
	   /* from the following ExecuteChannelTest procedure calls.                */

	   test_threshold.channel     = PLASMA_1_PLUS;
	   test_threshold.level       = HIGH_PLASMA_1_PLUS_SELF_TEST_THRESHOLD;
	   suDev.setTriggerLevel(test_threshold);
	   executeChannelTest(self_test_SU_index, PLASMA_1_PLUS, PLASMA_1_PLUS_HIGH);

	   test_threshold.channel     = PLASMA_1_MINUS;
	   test_threshold.level       = HIGH_PLASMA_SELF_TEST_THRESHOLD;
	   suDev.setTriggerLevel(test_threshold);
	   executeChannelTest(self_test_SU_index, PLASMA_1_MINUS, PLASMA_1_MINUS_HIGH);

	   executeChannelTest(self_test_SU_index, PLASMA_2_PLUS, PLASMA_2_PLUS_HIGH);

	   test_threshold.channel     = PZT_1_2;
	   test_threshold.level       = HIGH_PIEZO_SELF_TEST_THRESHOLD;
	   suDev.setTriggerLevel(test_threshold);
	   executeChannelTest(self_test_SU_index, PZT_1, PZT_1_HIGH);

	   test_threshold.channel     = PZT_1_2;
	   test_threshold.level       = HIGH_PIEZO_SELF_TEST_THRESHOLD;
	   suDev.setTriggerLevel(test_threshold);
	   executeChannelTest(self_test_SU_index, PZT_2, PZT_2_HIGH);

	   test_threshold.channel     = PLASMA_1_PLUS;
	   test_threshold.level       = LOW_PLASMA_SELF_TEST_THRESHOLD;
	   suDev.setTriggerLevel(test_threshold);
	   executeChannelTest(self_test_SU_index, PLASMA_1_PLUS, PLASMA_1_PLUS_LOW);

	   test_threshold.channel     = PLASMA_1_MINUS;
	   test_threshold.level       = LOW_PLASMA_SELF_TEST_THRESHOLD;
	   suDev.setTriggerLevel(test_threshold);
	   executeChannelTest(self_test_SU_index, PLASMA_1_MINUS, PLASMA_1_MINUS_LOW);

	   executeChannelTest(self_test_SU_index, PLASMA_2_PLUS, PLASMA_2_PLUS_LOW); 

	   test_threshold.channel     = PZT_1_2;
	   test_threshold.level       = LOW_PIEZO_SELF_TEST_THRESHOLD;
	   suDev.setTriggerLevel(test_threshold);
	   executeChannelTest(self_test_SU_index, PZT_1, PZT_1_LOW);

	   test_threshold.channel     = PZT_1_2;
	   test_threshold.level       = LOW_PIEZO_SELF_TEST_THRESHOLD;
	   suDev.setTriggerLevel(test_threshold);
	   executeChannelTest(self_test_SU_index, PZT_2, PZT_2_LOW);

	   suDev.enableHitTrigger();

//	   SET_HIT_TRIGGER_ISR_FLAG;
	   /* A pseudo trigger is generated in order to reset peak   */
	   /* detector and delay counter in AcquisitionTask.         */  
	   /* No event is recorded i.e. event processing is disabled */
	   /* because SU state is 'self_test_e'.                     */

	   restoreSettings(self_test_SU_index);
	}
	
	/**
	 * Purpose        : Execute SU self tests
	 * Interface      : inputs      - self_test_SU_index, test_channel
	 *                  outputs     - telemetry_data.SU# Status
	 *                  subroutines -  SelectSelfTestChannel
	 *                                 SelectStartSwitchLevel
	 *                                 WaitTimeout
	 *                                 DelayAwhile
	 *                                 SelectTriggerSwitchLevel
	 * Preconditions  : none
	 * Postconditions : Self test trigger siggnal is generated.
	 *                  voltages and overcurrents is completed.
	 * Algorithm      : - Self test channel is selected.
	 *                  - Analog switch starting level is selected depending on
	 *                    the channel.
	 *                  - A pseudo trigger is generated in order to reset peak
	 *                    detector and delay counter in AcquisitionTask.
	 *                  - Hit trigger processing is waited for 40 ms.
	 *                  - Hit trigger processing for this self test pulse is
	 *                    enabled by setting SU state to 'self_test_trigger_e'
	 *                  - Analog switch triggering level is selected depending
	 *                    on the channel (rising or falliing edge).
	 *                  - If self test trigger pulse did not cause an interrupt,
	 *                    set SELF_TEST_ERROR indication in SU status register
	 *                    and restore SU state to 'self_test_e'.
	 */
	private void executeChannelTest(int self_test_SU_index,	int test_channel, int test_pulse_level) {
		int delay_limit;
		TriggerSet test_threshold = new TriggerSet();

		TaskControl tc = system.getTaskControl();
		SensorUnitDev suDev = system.getSensorUnitDevice();
		AcquisitionTask acqTask = system.getAcquisitionTask();
		
		if (test_channel == PLASMA_1_PLUS || 
				test_channel == PLASMA_1_MINUS || 
				test_channel == PLASMA_2_PLUS) {
			
			suDev.selectSelfTestChannel(test_channel);

			suDev.enableAnalogSwitch(self_test_SU_index);

			tc.waitTimeout(1);

			HwIf.resetPeakDetector(self_test_SU_index + AcquisitionTask.SU1);

			tc.waitTimeout(1);

			HwIf.resetPeakDetector(self_test_SU_index + AcquisitionTask.SU1);

			tc.waitTimeout(1);

			tc.clearHitTriggerISRFlag();

			HwIf.resetDelayCounters();

			acqTask.sensorUnitState[self_test_SU_index] = SensorUnitState.self_test_trigger_e;
			/* Enable hit trigger processing for this self test pulse. */

			suDev.enableHitTrigger();

			suDev.setTestPulseLevel(test_pulse_level);

			/* Set at least 1ms test pulse    */
			delay_limit = tc.delayLimit(1000);
			delayAWhile(delay_limit);

			/* For plasma 1i channel triggering must take place in 1ms after */
			/* rising edge.                                                  */
			if (test_channel == PLASMA_1_MINUS &&
					acqTask.sensorUnitState[self_test_SU_index] == SensorUnitState.self_test_trigger_e)
			{
				/* Self test trigger pulse did not cause an interrupt. */    
				tmData.setSUError(self_test_SU_index, (byte)TelecommandExecutionTask.SELF_TEST_ERROR);

				acqTask.sensorUnitState[self_test_SU_index] = SensorUnitState.self_test_e;
				/* Triggering of a self test pulse is disabled by restoring */
				/* the self_test_e state.                                   */
			}

			/* Test pulse is always at least 3ms (=1ms+2ms) */
			delay_limit = tc.delayLimit(2000);
			delayAWhile(delay_limit);

			suDev.setTestPulseLevel(0);

			if (test_channel == PLASMA_2_PLUS)
			{
//				SET_HIT_TRIGGER_ISR_FLAG;
			}

			/* If channel is plasma 1e or 2e then wait at least 1ms after */
			/* falling edge.                                              */
			if (test_channel != PLASMA_1_MINUS)
			{
				delay_limit = tc.delayLimit(1000);
				/* Set at least 1ms test pulse    */
				delayAWhile(delay_limit);
			}

			suDev.disableHitTrigger();

			if (test_channel != PLASMA_2_PLUS)
			{
				test_threshold.sensor_unit = self_test_SU_index + AcquisitionTask.SU1;
				test_threshold.channel     = test_channel;
				test_threshold.level       = MAX_PLASMA_SELF_TEST_THRESHOLD;
				suDev.setTriggerLevel(test_threshold);
			}

			suDev.disableAnalogSwitch(self_test_SU_index);
		}
		else
		{
			suDev.selectSelfTestChannel(test_channel);

			suDev.setTestPulseLevel(test_pulse_level);

			tc.waitTimeout(1);

			HwIf.resetPeakDetector(self_test_SU_index + AcquisitionTask.SU1);

			tc.waitTimeout(1);

			HwIf.resetPeakDetector(self_test_SU_index + AcquisitionTask.SU1);

			tc.waitTimeout(1);

			tc.clearHitTriggerISRFlag();

			HwIf.resetDelayCounters();

			acqTask.sensorUnitState[self_test_SU_index] = SensorUnitState.self_test_trigger_e;
			/* Enable hit trigger processing for this self test pulse. */

			suDev.enableHitTrigger();

			suDev.enableAnalogSwitch(self_test_SU_index);

			/* Set at least 1ms test pulse    */
			delay_limit = tc.delayLimit(1000);
			delayAWhile(delay_limit);

			suDev.disableHitTrigger();

			suDev.setTestPulseLevel(0);

			suDev.disableAnalogSwitch(self_test_SU_index);

			test_threshold.sensor_unit = self_test_SU_index + AcquisitionTask.SU1;
			test_threshold.level       = MAX_PIEZO_SELF_TEST_THRESHOLD;
			test_threshold.channel     = PZT_1_2;
			suDev.setTriggerLevel(test_threshold);
		}

		if (acqTask.sensorUnitState[self_test_SU_index] == SensorUnitState.self_test_trigger_e)
		{
			/* Self test trigger pulse did not cause an interrupt. */    
			tmData.setSUError(self_test_SU_index, (byte)TelecommandExecutionTask.SELF_TEST_ERROR);

			acqTask.sensorUnitState[self_test_SU_index] = SensorUnitState.self_test_e;
			/* Triggering of a self test pulse is disabled by restoring */
			/* the self_test_e state.                                   */
		}
	}

	/**
	 * Purpose        : Restores settings after SU self tests.
	 * Interface      : inputs      - self_test_SU_index,
	 *                                telemetry_data, SU threshold levels
	 *                  outputs     - HW registers, thresholds
	 *                                SU state
	 *                  subroutines -  SetTriggerLevel
	 *                                 Switch_SU_State
	 * Preconditions  : none
	 * Postconditions : - Threshold levels are restored to the level prior to
	 *                    the test. SU state for the SU under test is restored
	 *                    to ON.
	 * Algorithm      : - Original threshold levels are copied from
	 *                    telemetry_data and written in to HW registers with
	 *                    SetTriggerLevel.
	 *                  - SU state is restored to ON with Switch_SU_State.
	 */
	private void restoreSettings(int self_test_SU_index) {
		
		final SensorUnitSettings setting_map_c[] = {
				tmData.getSensorUnit1(),
				tmData.getSensorUnit2(),
				tmData.getSensorUnit3(),
				tmData.getSensorUnit4()
		};
		/* Pointers to Sensor Unit configuration data in telemetry */
		/* data area.                                              */

		SensorUnitSettings SU_setting;
		/* Pointer to configuration data of the Sensor Unit being */
		/* Self Tested.                                           */

		SensorUnit SU_switch = new SensorUnit();
		TriggerSet threshold = new TriggerSet();
		/* Parameters for subroutines. */

		SensorUnitDev suDev = system.getSensorUnitDevice();
		
		SU_setting = setting_map_c[self_test_SU_index];

		threshold.sensor_unit = system.getAcquisitionTask().self_test_SU_number; 

		threshold.level   = 
			SU_setting.plasma_1_plus_threshold;
		threshold.channel = PLASMA_1_PLUS;
		suDev.setTriggerLevel(threshold);
		/* Restore Plasma 1 Plus trigger threshold. */

		threshold.level   = 
			SU_setting.plasma_1_minus_threshold;
		threshold.channel = PLASMA_1_MINUS;
		suDev.setTriggerLevel(threshold);
		/* Restore Plasma 1 Minus trigger threshold. */

		threshold.level   = 
			SU_setting.piezo_threshold;
		threshold.channel = PZT_1_2;
		suDev.setTriggerLevel(threshold);
		/* Restore Piezo trigger threshold. */

		SU_switch.number                = system.getAcquisitionTask().self_test_SU_number;
		SU_switch.state                 = SensorUnitState.on_e;
		SU_switch.expected_source_state = SensorUnitState.self_test_e;
		system.getTelecommandExecutionTask().switchSensorUnitState(SU_switch);
		/* Switch SU State back to On. */
	}
	
	/**
	 * Purpose        : advances time in the telemetry
	 * Interface      : inputs      - telemetry_data.time
	 *                  outputs     - telemetry_data.time
	 *                  subroutines - none
	 * Preconditions  : none
	 * Postconditions : Time updated in telemetry_data.
	 * Algorithm      :
	 *                  Time in the telemetry_data.time is advanced until
	 *                  maximum value for the variable is reached, after that it 
	 *                  is implicitely wrapped-around on overflow.
	 */
	private void updateTime() {
		TaskControl tc = system.getTaskControl();

		tc.disableInterruptMaster();
		/* Disable all interrupts.                                             */

		internal_time.incr();
		/* Increment internal time. */
		                                           
		tc.enableInterruptMaster();
		/* Enable all interrupts.                                              */
	}
	
	
	/**
	 * Purpose        : Advances counters
	 * Interface      : inputs      - address to counter variable
	 *                  outputs     - none
	 *                  subroutines - none
	 * Preconditions  : none
	 * Postconditions : counter value is adjusted
	 * Algorithm      : - If a given counter is not already zero, its value is
	 *                    decreased.
	 *                  - Else it set to its initialization value.
	 * XXX: C version does update via a pointer, in Java we need to assign the return value!
	 */
	private int updatePeriodCounter(int counter, int full_counter_value) {
		if (counter != 0) {
			return counter-1;
			/* Decrease temperature measurement counter. */
		} else {
			return full_counter_value;
			/* Reset temperature measurement counter. */
		}
	}
	
	public Dpu.Time getInternalTime() {
		return internal_time;
	}

	public int getCodeChecksum() {
		return code_checksum;
	}
	
}
