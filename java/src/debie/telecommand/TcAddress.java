package debie.telecommand;

public class TcAddress {
	
	/*--- Ported from tc_hand.h:30-159*/
	/* Valid telecommand address codes:                 */
	/* NOTE that all codes are not yet defined, because */
	/* all telecommands are not implemented in the      */
	/* Prototype SW.                                    */

	public static final int UNUSED_TC_ADDRESS =                      0x00;

	public static final int START_ACQUISITION =                      0x01;
	public static final int STOP_ACQUISITION =                       0x02;

	public static final int ERROR_STATUS_CLEAR =                     0x03;

	public static final int SEND_STATUS_REGISTER =                   0x05;
	public static final int SEND_SCIENCE_DATA_FILE =                 0x06;

	public static final int SET_TIME_BYTE_0 =                        0x0C;
	public static final int SET_TIME_BYTE_1 =                        0x0D;
	public static final int SET_TIME_BYTE_2 =                        0x0E;
	public static final int SET_TIME_BYTE_3 =                        0x0F;

	public static final int SOFT_RESET =                             0x09;

	public static final int CLEAR_WATCHDOG_FAILURES =                0x0A;
	public static final int CLEAR_CHECKSUM_FAILURES =                0x0B;

	public static final int WRITE_CODE_MEMORY_MSB =                  0x10;
	public static final int WRITE_CODE_MEMORY_LSB =                  0x6F;
	public static final int WRITE_DATA_MEMORY_MSB =                  0x15;
	public static final int WRITE_DATA_MEMORY_LSB =                  0x6A;
	public static final int READ_DATA_MEMORY_MSB =                   0x1F;
	public static final int READ_DATA_MEMORY_LSB =                   0x60;

	public static final int SWITCH_SU_1 =                            0x20;
	public static final int SWITCH_SU_2 =                            0x30;
	public static final int SWITCH_SU_3 =                            0x40;
	public static final int SWITCH_SU_4 =                            0x50;

	public static final int SET_SU_1_PLASMA_1P_THRESHOLD =           0x21;
	public static final int SET_SU_2_PLASMA_1P_THRESHOLD =           0x31;
	public static final int SET_SU_3_PLASMA_1P_THRESHOLD =           0x41;
	public static final int SET_SU_4_PLASMA_1P_THRESHOLD =           0x51;

	public static final int SET_SU_1_PLASMA_1M_THRESHOLD =           0x22;
	public static final int SET_SU_2_PLASMA_1M_THRESHOLD =           0x32;
	public static final int SET_SU_3_PLASMA_1M_THRESHOLD =           0x42;
	public static final int SET_SU_4_PLASMA_1M_THRESHOLD =           0x52;

	public static final int SET_SU_1_PIEZO_THRESHOLD =               0x23;
	public static final int SET_SU_2_PIEZO_THRESHOLD =               0x33;
	public static final int SET_SU_3_PIEZO_THRESHOLD =               0x43;
	public static final int SET_SU_4_PIEZO_THRESHOLD =               0x53;

	public static final int SET_SU_1_PLASMA_1P_CLASS_LEVEL =         0x24;
	public static final int SET_SU_2_PLASMA_1P_CLASS_LEVEL =         0x34;
	public static final int SET_SU_3_PLASMA_1P_CLASS_LEVEL =         0x44;
	public static final int SET_SU_4_PLASMA_1P_CLASS_LEVEL =         0x54;

	public static final int SET_SU_1_PLASMA_1M_CLASS_LEVEL =         0x25;
	public static final int SET_SU_2_PLASMA_1M_CLASS_LEVEL =         0x35;
	public static final int SET_SU_3_PLASMA_1M_CLASS_LEVEL =         0x45;
	public static final int SET_SU_4_PLASMA_1M_CLASS_LEVEL =         0x55;

	public static final int SET_SU_1_PLASMA_2P_CLASS_LEVEL =         0x28;
	public static final int SET_SU_2_PLASMA_2P_CLASS_LEVEL =         0x38;
	public static final int SET_SU_3_PLASMA_2P_CLASS_LEVEL =         0x48;
	public static final int SET_SU_4_PLASMA_2P_CLASS_LEVEL =         0x58;

	public static final int SET_SU_1_PIEZO_1_CLASS_LEVEL =           0x26;
	public static final int SET_SU_2_PIEZO_1_CLASS_LEVEL =           0x36;
	public static final int SET_SU_3_PIEZO_1_CLASS_LEVEL =           0x46;
	public static final int SET_SU_4_PIEZO_1_CLASS_LEVEL =           0x56;

	public static final int SET_SU_1_PIEZO_2_CLASS_LEVEL =           0x27;
	public static final int SET_SU_2_PIEZO_2_CLASS_LEVEL =           0x37;
	public static final int SET_SU_3_PIEZO_2_CLASS_LEVEL =           0x47;
	public static final int SET_SU_4_PIEZO_2_CLASS_LEVEL =           0x57 ;

	public static final int SET_SU_1_PLASMA_1E_1I_MAX_TIME =         0x29;
	public static final int SET_SU_2_PLASMA_1E_1I_MAX_TIME =         0x39;
	public static final int SET_SU_3_PLASMA_1E_1I_MAX_TIME =         0x49;
	public static final int SET_SU_4_PLASMA_1E_1I_MAX_TIME =         0x59;

	public static final int SET_SU_1_PLASMA_1E_PZT_MIN_TIME =        0x2A;
	public static final int SET_SU_2_PLASMA_1E_PZT_MIN_TIME =        0x3A;
	public static final int SET_SU_3_PLASMA_1E_PZT_MIN_TIME =        0x4A;
	public static final int SET_SU_4_PLASMA_1E_PZT_MIN_TIME =        0x5A;

	public static final int SET_SU_1_PLASMA_1E_PZT_MAX_TIME =        0x2B;
	public static final int SET_SU_2_PLASMA_1E_PZT_MAX_TIME =        0x3B;
	public static final int SET_SU_3_PLASMA_1E_PZT_MAX_TIME =        0x4B;
	public static final int SET_SU_4_PLASMA_1E_PZT_MAX_TIME =        0x5B;

	public static final int SET_SU_1_PLASMA_1I_PZT_MIN_TIME =        0x2C;
	public static final int SET_SU_2_PLASMA_1I_PZT_MIN_TIME =        0x3C;
	public static final int SET_SU_3_PLASMA_1I_PZT_MIN_TIME =        0x4C;
	public static final int SET_SU_4_PLASMA_1I_PZT_MIN_TIME =        0x5C;

	public static final int SET_SU_1_PLASMA_1I_PZT_MAX_TIME =        0x2D;
	public static final int SET_SU_2_PLASMA_1I_PZT_MAX_TIME =        0x3D;
	public static final int SET_SU_3_PLASMA_1I_PZT_MAX_TIME =        0x4D;
	public static final int SET_SU_4_PLASMA_1I_PZT_MAX_TIME =        0x5D;

	public static final int SET_COEFFICIENT_1 =                      0x70;
	public static final int SET_COEFFICIENT_2 =                      0x71;
	public static final int SET_COEFFICIENT_3 =                      0x72;
	public static final int SET_COEFFICIENT_4 =                      0x73;
	public static final int SET_COEFFICIENT_5 =                      0x74;

	/* TC codes for SWITCH_SU_x: */

	public static final int ON_VALUE =  0x55;
	public static final int OFF_VALUE = 0x73;
	public static final int SELF_TEST = 0x99;

	/* Last TC code for SEND_STATUS_REGISTER: */

	public static final int LAST_EVEN = 0x74;

}
