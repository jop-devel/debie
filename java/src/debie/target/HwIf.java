package debie.target;

import debie.target.SensorUnitDev.Delays;
import debie.telecommand.TelecommandExecutionTask;

import static debie.telecommand.TelecommandExecutionTask.*;

public class HwIf {
	public static final int MAX_EVENTS = 1261;
	
	/* The test records the address of the first failed cell in the
	 * code (program) RAM and the external data RAM.
	 * If no problem is found, the address is NO_RAM_FAILURE.
	 * Note: these variables must _not_ be initialised here (at
	 * declaration), since this initialisation would be done after
	 * the RAM test and so would destroy its results.
	 * These variables are set by Init_DPU, which is called from
	 * the startup module. */
	private static char failed_code_address;
	private static char failed_data_address;	
	private static final int NO_RAM_FAILURE = 0xffff;
	
	/* Same as in the original DEBIE-1 SW */

	/** Purpose        :  Plasma1+ rise time counter is read from the specified
	 *                    address.
	 *  Interface      :  Result is returned as an unsigned char.
	 *  Preconditions  :
	 *  Postconditions :  Data is gained.
	 *  Algorithm      :  Counter is read with XBYTE.
	/* XXX: was unsigned char  */ 
	public static byte readRiseTimeCounter() {
		// TODO: stub. This is a hardware interface
		//return GET_DATA_BYTE(RISE_TIME_COUNTER);
		return 0;
	}

	public static void enableInterruptMaster() {
		// TODO Auto-generated method stub		
	}

	public static void disableInterruptMaster() {
		// TODO Auto-generated method stub
		
	}

	public static void resetPeakDetector(int trigger_unit) {
		// TODO Auto-generated method stub
		
	}
	
	/* Delay and rise time counters */
	public static void readDelayCounters(Delays delay_counters) {
		// TODO Auto-generated method stub
		
	}
	

	public static void resetDelayCounters() {
		// TODO Auto-generated method stub
		
	}
	
	/** Purpose        : Copy results of RAM test to telemetry_data.
	 *  Interface      : - inputs:  failed_code_address, failed_data_address
	 *                   - outputs: telemetry_data fields:
	 *                                 failed_code_address
	 *                                 failed_data_address
	 *                                 mode_status bits for:
	 *                                    PROGRAM_MEMORY_ERROR
	 *                                    DATA_MEMORY_ERROR
	 *  Preconditions  : Init_DPU called since reset.
	 *  Postconditions : telemetry_data reflects the results of the memory tests
	 *                   done in Init_DPU, as recorded in failed_code_address
	 *                   and failed_data_address.
	 *                   Note that the TM addresses are zero for "no failure".
	 *  Algorithm      : see below. */
	public static void signalMemoryErrors() {
		
		if (failed_code_address == NO_RAM_FAILURE) {
			TelecommandExecutionTask.getTelemetryData().clearModeBits(PROGRAM_MEMORY_ERROR);
			TelecommandExecutionTask.getTelemetryData().setFailedCodeAddress((char)0x0000);
		} else {
			TelecommandExecutionTask.getTelemetryData().setModeBits(PROGRAM_MEMORY_ERROR);
			TelecommandExecutionTask.getTelemetryData().setFailedCodeAddress(failed_code_address);
		}

		if (failed_data_address == NO_RAM_FAILURE) {
			TelecommandExecutionTask.getTelemetryData().clearModeBits(DATA_MEMORY_ERROR);
			TelecommandExecutionTask.getTelemetryData().setFailedDataAddress((char)0x0000);
		} else {
			TelecommandExecutionTask.getTelemetryData().setModeBits(DATA_MEMORY_ERROR);
			TelecommandExecutionTask.getTelemetryData().setFailedDataAddress(failed_data_address);
		}

	}
	
}
