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

	/** representation of time in DEBIE */
	public class Time {
		public int tval;
	}

	public static int getEventFlag() {
		// TODO: stub
		return 0;
	}

}
