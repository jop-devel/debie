package debie;

public interface Version {
	
	/* Software version. Unique for each delivered build of the software. */
	public static final byte SW_VERSION = 0;

	/**
	 * This must be set so that the checksum calculated from the code
	 * memory becomes zero (this value will then be actually equal to
	 * the code checksum when this value would be zero).
	 * So the procedure is as follows:
	 *   1. Update source code file(s), including SW_VERSION above.
	 *   2. Set CODE_CHECKSUM to zero.
	 *   3. Compile and link DEBIE into a .hex file.
	 *   4. Compute the XOR of the .hex data (using e.g. 'hexor').
	 *   5. Set CODE_CHECKSUM to the value computed in step 3.
	 *   6. Recompile and relink DEBIE into a new .hex file.
	 *   7. Verify that XOR of the new .hex file is zero. 
	 */
	public static final int CODE_CHECKSUM = -1;
}
