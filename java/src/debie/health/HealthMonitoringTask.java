package debie.health;

import debie.Version;
import debie.harness.HarnessSystem;
import debie.particles.EventClassifier;
import debie.particles.SensorUnit;
import debie.support.Dpu;
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
	
	/* === Configuration === */
	/** Health Monitoring loop count. */
	private static final int HEALTH_COUNT = 9;

	/** Voltage Measurement loop count. */
	private static final int VOLTAGE_COUNT = 17;

	/** Temperature measurement loop count. Its value must equal or greater than
	 * NUM_SU, because its value defines the SU whos temperatures are to be
	 * measured. */
	private static final int TEMP_COUNT = 5;

	/** Checksum loop count. */
	private static final int CHECK_COUNT = 59;

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
	static int code_checksum;

	char confirm_hit_result;  
	private Time internal_time = new Time();

	private HarnessSystem system;

	private int ADCChannelRegister;
	
	public HealthMonitoringTask(HarnessSystem system) {
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
		system.adcSim.updateADC_ChannelReg(ADCChannelRegister);
		
		ResetClass reset_class = HwIf.getResetClass();
		
		if (reset_class != ResetClass.warm_reset_e) {
			/* We are running the PROM code unpatched, either   */
			/* from PROM or from SRAM.                          */

			HwIf.reference_checksum = Dpu.INITIAL_CHECKSUM_VALUE;
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

// TODO: port
//		      fill_pointer = (EXTERNAL unsigned char * DIRECT_INTERNAL)&telemetry_data;
//
//		      for (i=0; i < sizeof(telemetry_data); i++)
//		      {
//		         *fill_pointer = 0;
//		         fill_pointer++;
//		      }

			TelecommandExecutionTask.resetEventQueueLength();
			/* Empty event queue. */

			TelecommandExecutionTask.clearEvents();
			/* Clears the event counters, quality numbers  */
			/* and free_slot_index of the event records in */
			/* the science data memory.                    */

			EventClassifier.initClassification();
			/* Initializes thresholds, classification levels and */
			/* min/max times related to classification.          */

// TODO: port
// 			Clear_RTX_Errors();
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
		 
// TODO: port
//		      ClearErrorStatus();  
//		      Clear_SU_Error();
//		      Clear_RTX_Errors();
//		      ClearSoftwareError();
		   
		   tmData.clearModeBits(~MODE_BITS_MASK);
		   tmData.resetWatchdogFailures();
		   tmData.resetChecksumFailures();
		   tmData.resetTCWord();
		   
		   /* Clear error status bits, error status counters */
		   /* and Command Status register.                   */

		   TelecommandExecutionTask.resetEventQueueLength();
		   /* Empty event queue. */

		   TelecommandExecutionTask.clearEvents();
		   /* Clears the event counters, quality numbers  */
		   /* and free_slot_index of the event records in */
		   /* the science data memory.                    */

		   EventClassifier.initClassification();
		   /* Initializes thresholds, classification levels and */
		   /* min/max times related to classification.          */

// TODO: port
//		   self_test_SU_number = NO_SU;
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

// TODO: port
//		   SetTestPulseLevel(DEFAULT_TEST_PULSE_LEVEL);
//		   /* Initializes test pulse level. */
//
//		   Set_SU_TriggerLevels (SU_1, &telemetry_data.sensor_unit_1);
//		   Set_SU_TriggerLevels (SU_2, &telemetry_data.sensor_unit_2);
//		   Set_SU_TriggerLevels (SU_3, &telemetry_data.sensor_unit_3);
//		   Set_SU_TriggerLevels (SU_4, &telemetry_data.sensor_unit_4);	
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
		// TODO stub
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
		
//		SetTimeSlice(SYSTEM_INTERVAL);
//		/* Set system clock interval */

//      WaitInterval(BOOT_WAIT_INTERVAL);
//      /* Wait for automatic A/D converter calibration */

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
	private void handleHealthMonitor() {
		//		   Update_SU_State (0);
		//		   Update_SU_State (1);
		//		   Update_SU_State (2);
		//		   Update_SU_State (3);
		//
		//		   UpdateTime();
		//		   /* Update telemetry registers. */
		//
		//		   Monitor(health_mon_round);
		//		   /* Execute current Health Monitoring Round.                            */
		//
		//		   UpdatePeriodCounter(&health_mon_round, HEALTH_COUNT);
		//		   /* Decrease or reset health monitor loop counter depending on its      */
		//		   /* current and limiting values.                                        */
		//
		//		   WaitInterval(HM_INTERVAL);    
		//		   /* Wait for next activation */
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
	void setMode(int mode)	{
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
	
	
	public Dpu.Time getInternalTime() {
		return internal_time;
	}

	public static int getCodeChecksum() {
		return code_checksum;
	}
	
}
