package debie.health;

import debie.Version;
import debie.particles.AcquisitionTask;
import debie.particles.EventClassifier;
import debie.particles.SensorUnit;
import debie.support.DebieSystem;
import debie.support.Dpu;
import debie.support.TaskControl;
import debie.support.Dpu.ResetClass;
import debie.support.Dpu.Time;
import debie.target.HwIf;
import debie.target.SensorUnitDev;
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

	private static int self_test_flag = SELF_TEST_DONE;

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
	private enum Round {
		round_0_e, round_1_e, round_2_e, round_3_e, round_4_e,
		round_5_e, round_6_e, round_7_e, round_8_e, round_9_e
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
	
	static int code_checksum;

	private Time internal_time = new Time();

	private DebieSystem system;

	public static int ADCChannelRegister;
	
	public HealthMonitoringTask(DebieSystem system) {
		this.system = system;
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
		
		HwIf.SUCtrlRegister |= 0x0F;
		Dpu.setDataByte(SU_CONTROL, (byte)HwIf.SUCtrlRegister);
		/* Set all Peak detector reset signals to high */

		TelecommandExecutionTask.max_events = HwIf.MAX_EVENTS;
		
		HwIf.resetDelayCounters();
		
		TelecommandExecutionTask.setSensorUnitOff(SU_INDEX_1, new SensorUnit());
		/* Set Sensor Unit 1 to Off state */

		TelecommandExecutionTask.setSensorUnitOff(SU_INDEX_2, new SensorUnit());
		/* Set Sensor Unit 2 to Off state */

		TelecommandExecutionTask.setSensorUnitOff(SU_INDEX_3, new SensorUnit());
		/* Set Sensor Unit 3 to Off state */

		TelecommandExecutionTask.setSensorUnitOff(SU_INDEX_4, new SensorUnit());
		/* Set Sensor Unit 4 to Off state */

		ADCChannelRegister |= 0x80;
		system.getAdcDevice().updateADCChannelReg(ADCChannelRegister);
		
		ResetClass reset_class = HwIf.getResetClass();
		
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

			TelecommandExecutionTask.getTelemetryData().clearAll();
			
			system.getTelecommandExecutionTask().resetEventQueueLength();
			/* Empty event queue. */

			system.getTelecommandExecutionTask().clearEvents();
			/* Clears the event counters, quality numbers  */
			/* and free_slot_index of the event records in */
			/* the science data memory.                    */

			EventClassifier.initClassification();
			/* Initializes thresholds, classification levels and */
			/* min/max times related to classification.          */

			TelecommandExecutionTask.getTelemetryData().clearRTXErrors();
			/* RTX error indicating registers are initialized. */

		} else if (reset_class == ResetClass.watchdog_reset_e) {
			/* Record watchdog failure in telemetry. */

			TelemetryData tmData = TelecommandExecutionTask.getTelemetryData();
			tmData.setErrorStatus((byte)(tmData.getErrorStatus() | TelecommandExecutionTask.WATCHDOG_ERROR));

			tmData.incrementWatchdogFailures();
		} else if (reset_class == ResetClass.checksum_reset_e) {
			/* Record checksum failure in telemetry. */	
			
			TelemetryData tmData = TelecommandExecutionTask.getTelemetryData();
			tmData.setErrorStatus((byte)(tmData.getErrorStatus() | TelecommandExecutionTask.CHECKSUM_ERROR));

			tmData.incrementChecksumFailures();
	   } else {
		   /* Soft or Warm reset. */
		   /* Preserve most of telemetry_data; clear some parts. */
		 			
		   TelemetryData tmData = TelecommandExecutionTask.getTelemetryData();
		 
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

		   EventClassifier.initClassification();
		   /* Initializes thresholds, classification levels and */
		   /* min/max times related to classification.          */

		   AcquisitionTask.self_test_SU_number = NO_SU;
		   /* Self test SU number indicating parameter */
		   /* is set to its default value.             */                       
	   }
		
		TelemetryData tmData = TelecommandExecutionTask.getTelemetryData();
		tmData.clearModeBits(MODE_BITS_MASK);
		tmData.setModeBits(TelemetryData.DPU_SELF_TEST);
		/* Enter DPU self test mode. */
	
		/* Software version information is stored in the telemetry data. */
		tmData.setSWVersion(Version.SW_VERSION);
		
		HwIf.signalMemoryErrors();
		/* Copy results of RAM tests to telemetry_data. */

		system.getSensorUnitDevice().setTestPulseLevel(DEFAULT_TEST_PULSE_LEVEL);
		/* Initializes test pulse level. */

		system.getSensorUnitDevice().setSUTriggerLevels(SU_1, tmData.getSensorUnit1());
		system.getSensorUnitDevice().setSUTriggerLevels(SU_2, tmData.getSensorUnit2());
		system.getSensorUnitDevice().setSUTriggerLevels(SU_3, tmData.getSensorUnit3());
		system.getSensorUnitDevice().setSUTriggerLevels(SU_4, tmData.getSensorUnit4());
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

		if (exceedsLimit(TelecommandExecutionTask.getTelemetryData().getSensorUnit1().plus_5_voltage,
				SU_P5V_ANA_LOWER_LIMIT,
				SU_P5V_ANA_UPPER_LIMIT)) {
			setModeStatusError(TelecommandExecutionTask.SUPPLY_ERROR);   
		}

		/* Result of voltage measurement from SU_1/2 -5V is compared against */
		/* limits.          							*/

		if (exceedsLimit(TelecommandExecutionTask.getTelemetryData().getSensorUnit1().minus_5_voltage,
				SU_M5V_ANA_LOWER_LIMIT,
				SU_M5V_ANA_UPPER_LIMIT)) {
			setModeStatusError(TelecommandExecutionTask.SUPPLY_ERROR);   
		}

		/* Result of voltage measurement from DIG +5V is compared against    */
		/* limits.          							*/

		if (exceedsLimit(TelecommandExecutionTask.getTelemetryData().DPU_plus_5_digital,
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
		// TODO port
		
//		   ADC_parameters_t EXTERNAL AD_voltage_parameters;
//		   /* This struct is used to hold parameters for Reading AD channels.        */
//
//		   unsigned char EXTERNAL voltage_channel[] = {
//		      0x10,0x11,0x12,0x13,0x54,0x55,0x56};
//		   /* This array holds parameters for setting the ADC channel for the        */
//		   /* measurement.                                                           */
//
//		   AD_voltage_parameters.ADC_channel = voltage_channel[channel_selector];
//		   /* Select the channel to be measured:                                     */
//		   /* channel_selector ->  ADC channel                                       */
//		   /*                0 ->  0x10                                              */
//		   /*                1 ->  0x11                                              */
//		   /*                2 ->  0x12                                              */
//		   /*                3 ->  0x13                                              */
//		   /*                4 ->  0x54                                              */
//		   /*                5 ->  0x55                                              */
//		   /*                6 ->  0x56                                              */
//
//		   AD_voltage_parameters.ADC_max_tries = ADC_VOLTAGE_MAX_TRIES; 
//		   /* When voltages are measured this variable defines the maximum        */
//		   /* amount of tries to be used in reading and handling an AD channel in */
//		   /* 'Read_AD_Channel()'.                                                */ 
//
//		   AD_voltage_parameters.conversion_max_tries = 
//		      CONVERSION_VOLTAGE_MAX_TRIES;
//		   /* When voltages are measured this variable defines the maximum        */
//		   /* amount of tries to be used when End Of Conversion indication is     */
//		   /* waited in function 'Convert_AD()'.                                  */ 
//		                                 
//		   Read_AD_Channel(&AD_voltage_parameters);
//		   /* Voltage channel is read.                                            */
//		 
//		   if (AD_voltage_parameters.AD_execution_result != RESULT_OK) 
//		   {
//		      /* An anomaly has occurred during the measurement. */
//
//		      SetSoftwareError(MEASUREMENT_ERROR);         
//		      /* Set measurement error indication bit in */
//		      /* software error status register.         */
//
//		   }
//
//		   else
//		   {
//
//		      switch (channel_selector)
//		      {
//		         /* Measurement result bits 8..15 from channels involving positive      */
//		         /* voltages are written to telemetry.                                  */
//
//		         case channel_0_e:
//
//		            telemetry_data.sensor_unit_1.plus_5_voltage = 
//		               AD_voltage_parameters.unsigned_ADC >> 8; 
//			   
//		            telemetry_data.sensor_unit_2.plus_5_voltage =	
//		               AD_voltage_parameters.unsigned_ADC >> 8;
//
//		            break;
//
//		         case channel_1_e:
//
//		            telemetry_data.sensor_unit_3.plus_5_voltage = 
//		               AD_voltage_parameters.unsigned_ADC >> 8; 
//			   
//		            telemetry_data.sensor_unit_4.plus_5_voltage =	
//		               AD_voltage_parameters.unsigned_ADC >> 8;
//
//		            break;
//
//		         case channel_2_e:
//			   
//		            telemetry_data.SU_plus_50 = AD_voltage_parameters.unsigned_ADC >> 8; 
//		         
//		            break;
//
//		         case channel_3_e:
//
//		            telemetry_data.DPU_plus_5_digital = 
//		               AD_voltage_parameters.unsigned_ADC >> 8;
//
//		            break;
//
//
//		        /* Measurement result bits 8..15 from channels involving negative      */
//		        /* voltages are written to telemetry.                                  */
//		        /* Note that even here, the "unsigned" or "raw" conversion result is   */
//		        /* used; this is a requirement.                                        */
//		 
//		         case channel_4_e:
//
//		            telemetry_data.sensor_unit_1.minus_5_voltage = 
//		               AD_voltage_parameters.unsigned_ADC >> 8; 
//			   
//		            telemetry_data.sensor_unit_2.minus_5_voltage =	
//		               AD_voltage_parameters.unsigned_ADC >> 8;
//
//		            break;
//
//		         case channel_5_e:
//			   
//		            telemetry_data.sensor_unit_3.minus_5_voltage = 
//		               AD_voltage_parameters.unsigned_ADC >> 8; 
//			   
//		            telemetry_data.sensor_unit_4.minus_5_voltage =	
//		               AD_voltage_parameters.unsigned_ADC >> 8;
//
//		            break;
//
//		         case channel_6_e:
//
//		            telemetry_data.SU_minus_50 =
//		               AD_voltage_parameters.unsigned_ADC >> 8;
//		      
//		            break;
//		      }
//		   }   
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
// TODO port
		
//		SetTimeSlice(SYSTEM_INTERVAL);
//		/* Set system clock interval */

		TaskControl.waitInterval(BOOT_WAIT_INTERVAL);
		/* Wait for automatic A/D converter calibration */

//		new_task.rtx_task_number    = TC_TM_INTERFACE_TASK;
//		new_task.task_main_function = TC_task;
//		CreateTask(&new_task);
//		/* Activate the Telecommand Execution task */
//
//		new_task.rtx_task_number    = ACQUISITION_TASK;
//		new_task.task_main_function = acq_task;
//		CreateTask(&new_task);
//		/* Activate the Acquisition task */
//
//		new_task.rtx_task_number    = HIT_TRIGGER_ISR_TASK;
//		new_task.task_main_function = hit_task;
//		CreateTask(&new_task);
//		/* Activate Hit Trigger Interrupt Service task */	
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
		TelecommandExecutionTask.updateSensorUnitState(0);
		TelecommandExecutionTask.updateSensorUnitState(1);
		TelecommandExecutionTask.updateSensorUnitState(2);
		TelecommandExecutionTask.updateSensorUnitState(3);

		updateTime();
		/* Update telemetry registers. */
		
		monitor(health_mon_round);
		/* Execute current Health Monitoring Round.                            */
		
		health_mon_round = updatePeriodCounter(health_mon_round, HEALTH_COUNT);
		/* Decrease or reset health monitor loop counter depending on its      */
		/* current and limiting values.                                        */
		
		TaskControl.waitInterval(HM_INTERVAL);    
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
	   
	   if (health_mon_round == Round.round_0_e.ordinal()) {
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

			   TaskControl.waitTimeout(AcquisitionTask.COUNTER_RESET_MIN_DELAY);

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
	   } else if (health_mon_round == Round.round_1_e.ordinal()) {
		   highVoltageCurrent(health_mon_round);
	       /* Overcurrent indicating bits related to sensor unit 2 in HV       */
		   /* status register are checked.                                     */		   
	   } else if (health_mon_round == Round.round_2_e.ordinal()) {
		   highVoltageCurrent(health_mon_round);
	       /* Overcurrent indicating bits related to sensor unit 3 in HV       */
		   /* status register are checked.                                     */		   
	   } else if (health_mon_round == Round.round_3_e.ordinal()) {
		   highVoltageCurrent(health_mon_round);
	       /* Overcurrent indicating bits related to sensor unit 4 in HV       */
		   /* status register are checked.                                     */		   
	   } else if (health_mon_round == Round.round_4_e.ordinal()) {
		   lowVoltageCurrent();
		   /* 'V_DOWN' indicator bit is checked.                               */
	   } else if (health_mon_round == Round.round_5_e.ordinal()) {
		   if (voltage_meas_count < 7) {
			   /* Seven Secondary voltage channels are measured starting when   */
			   /* 'voltage_meas_count' reaches a value of 6. Last measurement is*/
			   /*  executed on voltage_meas_count value 0.                      */
	 
			   measureVoltage(voltage_meas_count);
		   }
	   } else if (health_mon_round == Round.round_6_e.ordinal()) {
		   
		   if ((acqTask.self_test_SU_number != NO_SU) &&
				   acqTask.sensorUnitState[acqTask.self_test_SU_number - AcquisitionTask.SU1]
				                           == SensorUnit.SensorUnitState.self_test_e) {
			   /* SU self test sequence continues   */

			   // TODO prt
//			   SelfTestChannel(system.acqTask.self_test_SU_number - AcquisitionTask.SU1);
			   /* SU channels are monitored in this round. */
	 
			   acqTask.self_test_SU_number = NO_SU;
			   /* SU self test sequence ends here  */			   
		   }
		   
	   } else if (health_mon_round == Round.round_7_e.ordinal()) {

		   if (acqTask.self_test_SU_number != NO_SU) {
			   /* SU self test sequence has started   */
			   
			   self_test_flag = SELF_TEST_RUNNING;
			   /* Indication of a started test. */

			   // TODO port
//			   SelfTest_SU(system.acqTask.self_test_SU_number - AcquisitionTask.SU1);
			   /* Supply voltages and SU temperatures are monitored in this round. */

			   if (acqTask.self_test_SU_number != NO_SU) {
				   acqTask.sensorUnitState[acqTask.self_test_SU_number - AcquisitionTask.SU1]
				                           = SensorUnit.SensorUnitState.self_test_e;
			   }   
		   }
	   } else if (health_mon_round == Round.round_8_e.ordinal()) {
		   
//	         SET_WD_RESET_HIGH;

		   /* The Watch Dog time out signal state is reset HIGH state, as it is*/
		   /* falling edge active.                                             */		   
	   } else if (health_mon_round == Round.round_9_e.ordinal()) {
		   
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
	
		// TODO port
//	   ADC_parameters_t EXTERNAL AD_temperature_parameters;
//	   /* This struct is used to hold parameters for Reading AD channels.        */
//	 
//	   int temp_limit_value;
//
//	   for (int j=0; j < NUM_TEMP; j++)
//
//	   {
//	      AD_temperature_parameters.ADC_channel = 
//	         5 + (SU_index&1)*8 + (SU_index&2)*12 + j;
//	      /* Select the channel to be measured.                                  */
//	      
//	      AD_temperature_parameters.ADC_max_tries = ADC_TEMPERATURE_MAX_TRIES; 
//	      /* When temperatures are measured this variable defines the maximum    */
//	      /* amount of tries to be used in reading and handling an AD channel in */
//	      /* 'Read_AD_Channel()'.                                                */
//
//	      AD_temperature_parameters.conversion_max_tries = 
//	         CONVERSION_TEMPERATURE_MAX_TRIES;
//	      /* When temperatures are measured this variable defines the maximum    */
//	      /* amount of tries to be used when End Of Conversion indication is     */
//	      /* waited in function 'Convert_AD()'.                                  */
//	                                 
//	      Read_AD_Channel(&AD_temperature_parameters);
//	      /* Get ADC temperature measurement result.                             */
//
//
//						
//	      if (AD_temperature_parameters.unsigned_ADC & 0x8000)
//
//	      {
//	         /* Temperature is stored in the telemetry.                          */
//	       	            
//	         telemetry_data.SU_temperature[SU_index][j] = 
//	         (unsigned char)((AD_temperature_parameters.unsigned_ADC
//	         & 0x7FFF) >> 7);
//	         /* Store bits 7 .. 14 */
//	      }
//
//	      else
//
//	      {                  
//
//	         telemetry_data.SU_temperature[SU_index][j] = 0;
//	         /* Temperature too small -> store zero */
//
//	      }
//	  
//	      temp_limit_value = ( j==0 ? MAX_TEMP_1 : MAX_TEMP_2 );
//	 
//	      if (telemetry_data.SU_temperature[SU_index][j] > temp_limit_value)
//	         
//	      {
//	         /* Temperature has exeeded a predefined limit                       */
//
//	         TemperatureFailure(SU_index);
//	         /* Given SU is switched off, error and SU status registers are      */
//	         /* updated in telemetry.                                            */
//
//	      }	
//
//	      if (AD_temperature_parameters.AD_execution_result != RESULT_OK) 
//
//	      {
//	         /* An anomaly has occurred during the measurement.                  */
//
//	         SetSoftwareError(MEASUREMENT_ERROR);         
//	         /* Set measurement error indication bit in   */
//	         /* software error status register.           */   
//	                 
//	         TemperatureFailure(SU_index);
//	         /* Given SU is switched off and error and SU status registers are   */
//	         /* updated in telemetry.                                            */
//	                                        
//	 	}	      
//
//	   }
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
	 
		if (Dpu.checkCurrent(SU_current_mask[SU_index]) != 
			valid_value[SU_index]) {
			/* Overcurrent is detected.                                            */

			TelemetryData tmData = TelecommandExecutionTask.getTelemetryData();

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

				TelecommandExecutionTask.setSensorUnitOff(i, new SensorUnit());
				/* Switch off given sensor unit.                                 */

				TelecommandExecutionTask.getTelemetryData().setSUError(i, (byte)TelecommandExecutionTask.LV_SUPPLY_ERROR);
				/* Set high LV supply error indicating bit in the SU_Status      */
				/* register.                                                     */
			}
	      
			TelecommandExecutionTask.getTelemetryData().setErrorStatus((byte)TelecommandExecutionTask.OVERALL_SU_ERROR);
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
//		   DISABLE_INTERRUPT_MASTER;

		TelecommandExecutionTask.getTelemetryData()
			.setModeBits(mode_status_error & ~MODE_BITS_MASK);
		   /* The mode bits are secured against unintended modification by */
		   /* clearing those bits in 'mode_status_error' before "or":ing   */
		   /* its value to 'telemetry_data.mode_status'.                   */

//		   ENABLE_INTERRUPT_MASTER;
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
	public static void setMode(int mode)	{
//	   DISABLE_INTERRUPT_MASTER;

		TelemetryData tmData = TelecommandExecutionTask.getTelemetryData();
		tmData.clearModeBits(MODE_BITS_MASK);
		tmData.setModeBits(mode & MODE_BITS_MASK);
	   /* First mode status bits are cleared, and then the given mode is set. */

//	   ENABLE_INTERRUPT_MASTER;
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
//		DISABLE_INTERRUPT_MASTER;
//		/* Disable all interrupts.                                             */

		internal_time.incr();
		/* Increment internal time. */
		                                           
//		ENABLE_INTERRUPT_MASTER;
//		/* Enable all interrupts.                                              */
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

	public static int getCodeChecksum() {
		return code_checksum;
	}
	
}
