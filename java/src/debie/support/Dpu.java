package debie.support;

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
	} ;


	public static final int MAX_TIME = Integer.MAX_VALUE; /* 0xFFFFFFFF; */
	/* Maximum value for DEBIE time. */

	/** representation of time in DEBIE */
	public static class Time {
		public int tval;

		public void incr() {
			tval++;
		}

		public int getTag() {
			return tval;
		}
		// FIXME: synchronized?
		public synchronized void updateWithMask(int mask, int val) {
			tval = (tval & ~mask) | (val & mask);
		}
	}

	public static int getEventFlag() {
		// TODO: stub
		return 0;
	}


	/* Function prototypes: */

//	extern void Init_DPU (reset_class_t reset_class);
//
//	extern reset_class_t GetResetClass(void);
//
//	extern void SignalMemoryErrors (void);
//
//	extern void SetMemoryConfiguration (memory_configuration_t memory);
//
//	extern memory_configuration_t GetMemoryConfiguration(void)
//	   COMPACT REENTRANT_FUNC; 
//
//	extern void PatchCode(memory_patch_variables_t EXTERNAL *patch_variables);  

	// XXX: todo
	public static void reboot(ResetClass boot_type) {
//		void Reboot(reset_class_t boot_type)
//		{
//		#if defined(TRACE_HARNESS)
//		   printf ("Reboot %d\n", boot_type);
//		#endif
//
//		   if (boot_type == checksum_reset_e)
//		   {
//		      /* Make it not happen (at once) again: */
//		      reference_checksum = code_checksum;
//		   }
//
//		   TARGET_REBOOT;
//		}
		
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
