package debie.support;

import debie.harness.Harness;
import debie.health.HealthMonitoringTask;
import debie.target.HwIf;

public class Dpu {
	public static final int SAME = 1;
	public static final int NOT_SAME = 0;

	public static final int MEMORY_PATCHED = 1;
	public static final int MEMORY_NOT_PATCHED = 0;
    // #ifndef HIGH
	// public static final int HIGH = 1;
	// #endif
   
	// #ifndef LOW
	//   public static final int LOW = 0;
	//#endif

	public static final int SELECTED = 1;
	public static final int NOT_SELECTED = 0;
	public static final int RESET_OK = 1;
	public static final int RESET_NOT_OK = 0 ;

	public static final int ACCEPT_EVENT = 1;
	public static final int REJECT_EVENT = 0;


	/* memory addresses for program copy                                         */
	public static final int PROGRAM_COPY_START = 0x1000;
	public static final int PROGRAM_COPY_END =   0x8000;
	/* this can be replaced with real end address (+1) of used program code      */
	/* given in the linker map file                                              */

	/* Comment or delete following definition, if program should be executed     */
	/* from RAM                                                                  */
	/* public static final int USE_ALWAYS_PROM = */;

	/* memory addresses for patching                                             */
	public static final int BEGIN_SRAM1 =       0x1000;
	public static final int END_SRAM1 =         0x7FFF;
	public static final int BEGIN_SRAM3 =       0x8000;
	public static final int END_SRAM3 =         0xFEFF;
	public static final int BEGIN_DATA_RAM =    0x0000;


	public static final int SCIENCE_DATA_START_ADDRESS = 0x0000;
	/* First free absolute data address. */

	public static final int INITIAL_CHECKSUM_VALUE =   0;
	/* A value given to 'reference_checksum' variable at 'Boot()'. */
	/* It is zero, since one code byte is dedicated to a constant  */
	/* that ensures that the checksum of the PROM is zero.         */

	/*type definitions*/

	// XXX: DEBIE_mode_t;
	/* Debie mode index. Valid values:  */
	/* 00 DPU self test                 */
	/* 01 Stand by                      */
	/* 10 Acquisition                   */

	public enum ResetClass {
	   power_up_reset_e /* 0 */, /* Don't change value ! */
	   watchdog_reset_e /* 1 */, /* Don't change value ! */
	   soft_reset_e, 
	   warm_reset_e, 
	   error_e,
	   checksum_reset_e
	};

	public static ResetClass  s_w_reset;

	public enum MemoryConfiguration {
	   PROM_e, SRAM_e
	};

	static MemoryConfiguration memory_mode;
	
	
	/**
	 * Purpose        : Information about selected program memory is acquired
	 *                  and returned.
	 * Interface      : input: memory_mode
	 * Preconditions  : none
	 * Postconditions : none
	 * Algorithm      : Information about current memory  configuration is
	 *                  stored in a variable.
	 */
	public static MemoryConfiguration getMemoryConfiguration() {
		return memory_mode;
	}

	/**
	 * Purpose        : External program memory is selected to be either PROM or
	 *                  SRAM1.
	 * Interface      : Port 1 is used.
	 *                  output: memory_mode
	 * Preconditions  : After power-up reset PROM is always selected.
	 * Postconditions : External program memory is set depending on the given
	 *                  parameter.
	 *                  memory_mode contains the selected mode.
	 * Algorithm      : Memory configuration is selected with the output at the
	 *                  I/O port 1.
	 */
	public static void setMemoryConfiguration(MemoryConfiguration memory) {
// TODO port
//		switch (memory)	{
//		case PROM_e:
//			SET_MEM_CONF_PROM;
//			break;
//		case SRAM_e:
//			SET_MEM_CONF_SRAM;
//			break;
//		}
		memory_mode = memory;  
	}

	public static final int MAX_TIME = Integer.MAX_VALUE; /* 0xFFFFFFFF; */
	/* Maximum value for DEBIE time. */

	/** representation of time in DEBIE */
	public static class Time {
		private int internal_repr;

		public Time(int raw) {
			internal_repr = raw;
		}

		public Time() {
			this(0);
		}

		public void incr() {
			internal_repr++;
		}

		public int getTag() {
			return internal_repr;
		}
		// FIXME: synchronized?
		public synchronized void updateWithMask(int mask, int val) {
			internal_repr = (internal_repr & ~mask) | (val & mask);
		}

		public void set(int raw) {
			internal_repr = raw;
		}
	}

	public static class MemoryPatchVariables {
		/* unsigned char * */ public byte [] source;
		/* data_address_t */  public int destination;
		/* uint_least8_t */   public int data_amount;
		/* unsigned char */   public int execution_command;
	};
	
	/** Initial value is 1, set in Init_DPU. Value is 1 when code
	 * checksum value is valid, cleared to 0 when code memory is
	 * patched, set to 1 when next checksum calculation
	 * period is started.
	 */
	/* unsigned char */ public static int code_not_patched;

	/** Expected code checksum. Zero for unpatched code. */
	/* unsigned char */ public static int reference_checksum;

	public static boolean patchExecCommandOk(int execution_command) {
	   switch (execution_command) {
	   case 0:
	   case 0x09:
	   case 0x37:
	   case 0x5A:
		   return true;
	   }
	   return false;
	}

	/**
	 * Purpose        :  Code memory patching.
	 * Interface      :  Following parameters are given: Address from where to
	 *                   copy, address where to copy and the amount of bytes to
	 *                   be copied. Execution result is returned. Variables used
	 *                   in this function are stored in a struct. Pointer to a
	 *                   variable which stores an execution result of the
	 *                   function SetMemoryConfiguration is passed on.
	 * Preconditions  :  Source and destination addresses should be valid.
	 * Postconditions :  Desired part of the memory is copied.
	 * Algorithm      :  Bytes are copied.
	 */
	public static void patchCode(MemoryPatchVariables patch_variables) {
		
		/* unsigned char */ int old_checksum;
		/* Checksum calculated from the old contents of the pachted memory. */

		/* unsigned char */ int new_checksum;
		/* Checksum calculated from the new conrents of the patched memory. */

		/* unsigned char */ int patch_value;
		/* New value of a patched code memory byte. */

		MemoryConfiguration temp_configuration;
		/* Original memory configuration. */
		
		temp_configuration = getMemoryConfiguration();
		/* State of the current memory configuration is stored. */
		
//		DISABLE_INTERRUPT_MASTER;
//		/* Disable all interrupts. */

		setMemoryConfiguration(MemoryConfiguration.PROM_e);
		/* Enable code patching. */
		
		new_checksum = 0;
		old_checksum = 0;
		
		/* Memory block is copied from SRAM3 to SRAM1. */
        
		for (int i=0 ; i < patch_variables.data_amount ; i++) {
			old_checksum ^= getDataByte(patch_variables.destination + i);
			patch_value   = patch_variables.source[i];
			new_checksum ^= patch_value;

			setDataByte(patch_variables.destination + i, (byte)patch_value);
		}
		
		reference_checksum ^= (old_checksum ^ new_checksum);
		
		setMemoryConfiguration(temp_configuration);
		
		switch(patch_variables.execution_command) {
		case 0:
			/* Continue normally. */
			break;

		case 0x09:
			/* Execute soft reset. */

			reboot(ResetClass.soft_reset_e);
			/* Function does not return. */
			break;

		case 0x37:
			/* Execute warm reset. */

	       	reboot(ResetClass.warm_reset_e);
	       	/* Function deos not return. */
	       	break;
		
	      case 0x5A:
// XXX: We cannot really jump to the patch, unless we do somereally dirty hacking
//	          /* Jump to the patched memory. */
//
//	          patch_function = (fptr_t)(patch_variables -> destination);
//
//	          CALL_PATCH(patch_function);
//	          /* Called code may or may not return. */
//
//	          /* TC_state is selected upon return. */

	          break;
		}
		
//		ENABLE_INTERRUPT_MASTER;
//		/* Enable all 'enabled' interrupts. */		
	}
	
	/* Function prototypes: */
//	extern void Init_DPU (reset_class_t reset_class);

	// XXX: todo
	public static void reboot(ResetClass boot_type) {
		if (Harness.TRACE) Harness.trace(String.format("Reboot %d", boot_type.ordinal())); 
		
		if (boot_type == ResetClass.checksum_reset_e) {
			/* Make it not happen (at once) again: */
			reference_checksum = HealthMonitoringTask.getCodeChecksum();
		   
			System.out.println("Target Reboot.");
		}		
	}

	private static byte data_memory[] = new byte[65536];
	
	public static void setDataByte(int addr, byte value) {
		if (Harness.TRACE) Harness.trace(String.format("setDataByte 0x%x to %d = 0x%x",
													   addr, (int)value & 0xff, (int)value & 0xff));
		
		data_memory[addr] = value;
	}
	
	public static byte getDataByte(int addr) {
		byte value = data_memory[addr];
		if (Harness.TRACE) Harness.trace(String.format("getDataByte 0x%x is %d = 0x%x",
													   addr, (int)value & 0xff, (int)value & 0xff));
		return value;
	}
	
	/* Assembly-language function prototypes (asmfuncs.a51): */

//	extern unsigned char TestMemBits (data_address_t address);
//
//	extern unsigned char TestMemData (
//	                data_address_t start,
//	                uint_least8_t  bytes);
//
//	extern unsigned char TestMemSeq (
//	                data_address_t start,
//	                uint_least8_t  bytes);

}
