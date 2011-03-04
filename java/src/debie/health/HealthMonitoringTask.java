package debie.health;

import debie.support.Dpu;
import joprt.RtThread;

/**
 * Health monitoring, invoked periodically.
 * 
 * XXX: This is a runnable (not a RTThread). ok?
 */
public class HealthMonitoringTask implements Runnable {

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
	char  code_checksum;

	char confirm_hit_result;  
	Dpu.Time internal_time;

	public HealthMonitoringTask() {
		initHealthMonitor();
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
	private void initHealthMonitor() {
		//		   /* Initializes the system.    */ 
		//		   InitSystem();
		//
		//		   /* Execute the DPU self test. */
		//		   DPU_SelfTest();
		//
		//		   /* Switch to Standby mode */
		//		   SetMode(STAND_BY);
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

}
