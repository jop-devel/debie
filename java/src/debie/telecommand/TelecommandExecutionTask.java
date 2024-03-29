package debie.telecommand;

import static debie.target.SensorUnitDev.NUM_SU;
import debie.particles.AcquisitionTask;
import debie.particles.EventRecord;
import debie.particles.SensorUnit;
import debie.particles.SensorUnit.SensorUnitState;
import debie.support.DebieSystem;
import debie.support.Dpu;
import debie.support.KernelObjects;
import debie.support.Mailbox;
import debie.support.TaskControl;
import debie.support.TelemetryObject;
import debie.target.HwIf;
import debie.target.SensorUnitDev;
import debie.target.TcTmDev;
import debie.target.SensorUnitDev.TriggerSet;

public class TelecommandExecutionTask {

	/*--- [1] Definitions from tm_data.h:33-83 ---*/
	public static final int NUM_CLASSES =  10;
	public static final int NUM_TEMP =     2;
	public static final int NUM_NOT_USED = (4 + 0x70 - 0x6A);

	/* Definitions related to error indicating bits in mode status register: */
	public static final int SUPPLY_ERROR =                0x80;
	public static final int DATA_MEMORY_ERROR =           0x40;
	public static final int PROGRAM_MEMORY_ERROR =        0x20;
	public static final int MEMORY_WRITE_ERROR =          0x10;
	public static final int ADC_ERROR =                   0x04;


	/* Definitions related to error indicating bits in SU status register: */
	public static final int HV_SUPPLY_ERROR =             0x80;
	public static final int LV_SUPPLY_ERROR =             0x40;
	public static final int TEMPERATURE_ERROR =           0x20;
	public static final int SELF_TEST_ERROR =             0x10;
	public static final int HV_LIMIT_ERROR =              0x08 ;
	public static final int LV_LIMIT_ERROR =              0x04;
	public static final int SUPPLY_VOLTAGE_MASK =         0x03   ;

	/* Used when error indiacting bits are cleared. */

	/* Definitions related to error indicating bits in error status register: */
	public static final int CHECKSUM_ERROR =              0x08;
	public static final int WATCHDOG_ERROR =              0x04;
	public static final int OVERALL_SU_ERROR =            0xF0;
	/* Used to indicate error in all of the SUs. */

	public static final int ERROR_STATUS_OFFSET =         0x10;
	/* Used when SU error indicating bit is selected. */


	/* Definitions related to error indicating bits in software error register: */
	public static final int MEASUREMENT_ERROR =           0x01 ;
	public static final int OS_START_SYSTEM_ERROR =       0x02;
	public static final int OS_WAIT_ERROR =               0x04;
	public static final int OS_SET_SLICE_ERROR =          0x08;


	public static final int NUM_QCOEFF = 5;
	/* Number of Quality Coefficients. */

	/* Special value for TC/TM mail to be used only     */
	/* telemetry is ready                               */

	public static final int TM_READY = 0xFFFF;

	public static final int MAX_QUEUE_LENGTH = 10;

	/*--- Ported from tc_hand.c:38-67 */
	private static int TC_ADDRESS(int TC_WORD) { return ((TC_WORD) >> 9); }
	private static int TC_CODE(int TC_WORD)    { return ((TC_WORD) &  255); }

	private static final int SET_TIME_TC_TIMEOUT = 100;
	/* Timeout between Set Time telecommands, 100 x 10ms = 1s */

	private static final int MEM_BUFFER_SIZE =     32;

	/* Possible TC look-up table values:                         */

	private static final int ALL_INVALID =   0 ;
	/* used for invalid TC addresses                             */

	private static final int ALL_VALID =     1 ;
	/* used for TCs which accept all TC codes                    */

	private static final int ONLY_EQUAL =    2 ;
	/* used for TCs which require equal TC address and code      */

	private static final int ON_OFF_TC =     3;
	/* used for TCs which require ON, OFF or SELF_TEST parameter */

	private static final int ONLY_EVEN =     4;
	/* used currently only for SEND_STATUS_REGISTER TC           */


	private static final int WRITE_MEMORY_TIMEOUT = 100;
	/* Timeout 100 x 10 ms = 1s. */

	// XXX: pulled out of tcInterruptService, which breaks re-entrance, but avoids memory allocation
	private final DataPointer data_pointer = new DataPointer(0);
	
	/* State of Telecommand Execution task */
	public static enum TC_State {
		TC_handling_e, 
		read_memory_e,
		memory_dump_e, 
		write_memory_e,
		memory_patch_e, 
		register_TM_e, 
		SC_TM_e
	};

	/*--- [2] Definitions from   telem.c:36-73 ---*/

	// XXX: this should probably be in external memory:
	//EXTERNAL science_data_file_t LOCATION(SCIENCE_DATA_START_ADDRESS)
	public ScienceDataFile science_data;

	/* This variable is used to speed up certain    */
	/* Functional Test by adding the possibility    */
	/* to restrict the amount of events.            */
	/* It is initialised to value MAX_EVENTS at     */
	/* Boot.                                        */
	public int /* uint_least16_t */ max_events;

	private TelemetryObject telemetry_object;

	private /* unsigned char* */ int telemetry_index;

	private /* unsigned char* */ int telemetry_end_index;

	/* Checksum to be sent at the end of Read Memory sequence. */
	private /* unsigned char */ char read_memory_checksum;

	/* Holds event records before they are copied to the */
	/* Science Data memory. Normally there is only data  */
	/* from the new event whose data is beign collected, */
	/* but during Science Telemetry there can be stored  */
	/* several older events which are copied to the      */
	/* Science Data memory after telemetry ends.         */
	private EventRecord[] event_queue; /* Initialized in the constructor */

	/* Number of event records stored in the queue.    */
	/* These records are stored into event_queue table */
	/* in order starting from the first element.       */
	/* Initialised to zero on power-up.                */
	private /* uint_least8_t */ int event_queue_length;

	/* Index to the first free record in the Science         */
	/* Data memory, or if it is full equals to 'max_events'. */
	/* Initialised to zero on power-up.                      */
	/* Holds event records before they are copied to the */
	/* Science Data memory. Normally there is only data  */
	/* from the new event whose data is beign collected, */
	/* but during Science Telemetry there can be stored  */
	/* several older events which are copied to the      */
	/* Science Data memory after telemetry ends.         */
	private /* uint_least16_t */ int free_slot_index;

	/*--- Ported from tc_hand.c:78-89 */
	/* Type definitions */
	
	public static class Telecommand {
		public Telecommand(int word, int address, int code) {
			this.TC_word = (char) word;
			this.TC_address = (char) address;
			this.TC_code = (char) code;
		}
		private /*uint16_t*/ char TC_word;          /* Received telecommand word */
		private /*unsigned char*/ char TC_address;  /* Telecommand address       */
		private /*unsigned char*/ char TC_code;     /* Telecommand code          */

		public void copyFrom(Telecommand cmd) {
			this.TC_word = cmd.TC_word;
			this.TC_address = cmd.TC_address;
			this.TC_code = cmd.TC_code;
		}
	}

	public enum MemoryType { 
		Code, 
		Data
	};

	/*--- Ported from tc_hand.c:90-123 */
	/* Global variables */

	/* Holds previous telecommand unless a timeout has occurred */
	private Telecommand previous_TC;

	/* Time out for next telecommand, zero means no timeout */
	private /*unsigned char */ char TC_timeout = 0;

	/* Look-up table for all possible 128 TC address values (domain: ALL_INVALID(0) - ONLY_EVEN(4) ) */
	private /*unsigned char[] */ byte[] TC_look_up = new byte[128];

	/* internal state of the Telecommand Execution task */
	private TC_State TC_state = TC_State.TC_handling_e; /* 0 ~ TC_handling_e */

	/* Selection of memory write target (code/data) */ 
	private MemoryType memory_type;

	/* Buffer for memory read and write telecommands */
	private /* unsigned char[] */  byte[] memory_transfer_buffer = new byte[MEM_BUFFER_SIZE];

	/* MSB of memory read source and write destination addresses */
	private /*unsigned char */ int address_MSB;

	/* LSB of memory read source and write destination addresses. */
	private /* unsigned char */ int address_LSB;

	/* Index to memory_buffer. */
	private /* uint_least8_t */ int memory_buffer_index = 0;

	/* Checksum for memory write blocks. */
	private /* unsigned char */ int write_checksum;


	/*--- References to other parts of the system ---*/

	/* link to tctm hardware */
	private TcTmDev tctmDev;
	private Mailbox tcMailbox;
	private DebieSystem system;

	private Telecommand received_command;
	private TaskControl taskControl;
	private TelemetryData telemetry_data;
	
	/*--- Constructor ---*/

	/** Purpose        : Initialize the global state of Telecommand Execution
	 * Interface      : inputs      - none
	 *                  outputs     - TC state
	 *                  subroutines - DisableInterrupt
	 *                                EnableInterrupt
	 * Preconditions  : none
	 * Postconditions : TelecommandExecutionTask is operational.
	 * Algorithm      : - initialize task variables.
	 */
	public TelecommandExecutionTask(DebieSystem system) {

		science_data = new ScienceDataFile(system);
		
		this.taskControl = system.getTaskControl();
		
		this.telemetry_data = system.getTelemetryData();
		
		this.tcMailbox = system.getTcTmMailbox();
		this.tctmDev = system.getTcTmDevice();
		this.system = system;
		this.received_command = new Telecommand(0,TcAddress.UNUSED_TC_ADDRESS, 0);

		initTcLookup();

		TC_state            = TC_State.TC_handling_e;

		// [ct. C code] mailbox is initialized elsewhere
		//		TC_mail.mailbox_number = TCTM_MAILBOX;
		//		TC_mail.message        = &(received_command.TC_word);

		/* Parameters for mail waiting function.   */
		/* Time-out set separately.                */

		previous_TC = new Telecommand(0, TcAddress.UNUSED_TC_ADDRESS, 0);

		taskControl.disableInterrupt(KernelObjects.TM_ISR_SOURCE);
		taskControl.enableInterrupt(KernelObjects.TC_ISR_SOURCE);
		
		/* [Java version only] Initialize the EventRecord list */
		event_queue	= new EventRecord[MAX_QUEUE_LENGTH];
		for(int i = 0; i < MAX_QUEUE_LENGTH; ++i) {
			event_queue[i] = new EventRecord(system);
		}
	}

	/*--- [2] Definitions from   telem.c: 74 ---*/

	/**  Purpose        : Returns pointer to free event record in event queue. 
	 * Interface       : inputs      - event_queue_length, legnth of the event
	 *                                queue.
	 *                  outputs     - return value, pointer to the free record.
	 *                  subroutines - none
	 * Preconditions  : none.
	 * Postconditions : none.
	 * Algorithm      : -If the queue is not full
	 *                     -return pointer to the next free record
	 *                  -else
	 *                     -return pointer to the last record
	 */
	public EventRecord getFreeRecord() {
		
		if (event_queue_length < MAX_QUEUE_LENGTH)
		{
			return event_queue[event_queue_length];
		}

		else
		{
			return event_queue[MAX_QUEUE_LENGTH - 1];
		}
	}

	/** Purpose        : Waits for and handles one Telecommand from the TC ISR
	 * Interface      : inputs      - Telecommand Execution task mailbox
	 *                                TC state
	 *                  outputs     - TC state
	 *                  subroutines - ReadMemory
	 *                                WriteMemory
	 *                                ExecuteCommand
	 * Preconditions  : none
	 * Postconditions : one message removed from the TC mailbox
	 * Algorithm      : - wait for mail to Telecommand Execution task mailbox
	 *                  - if mail is "TM_READY" and TC state is either
	 *                    "SC_TM_e" or "memory_dump_e".
	 *                    - if TC state is "SC_TM_e"
	 *                       - call ClearEvents function
	 *                    - set TC state to TC handling
	 *                  - else switch TC state
	 *                    - case ReadMemory_e  : Check received TC's address
	 *                    - case WriteMemory_e : call WriteMemory function
	 *                    - case MemoryPatch_e : call MemoryPatch function
	 *                    - case TC_Handling : call ExecuteCommand
	 *                NOTE:   case register_TM_e is left out because
	 *                        operation of SEND_STATUS_REGISTER TC does not
	 *                        require any functionalites of this task.  
	 */
	public void handleTelecommand () {
		
		tcMailbox.setTimeout(TC_timeout);
		tcMailbox.waitMail();

		TC_timeout = 0;
		/* Default value */

		if (tcMailbox.execution_result == Mailbox.TIMEOUT_OCCURRED)
		{
			previous_TC.TC_word    = 0;
			previous_TC.TC_address = TcAddress.UNUSED_TC_ADDRESS;
			previous_TC.TC_code    = 0;
			/* Forget previous telecommand. */

			if (TC_state != TC_State.TC_handling_e)
			{
				/* Memory R/W time-out. */
				setTCError();
			}

			TC_state = TC_State.TC_handling_e; 
		}

		else if (tcMailbox.execution_result == Mailbox.MSG_RECEIVED)
		{
			received_command.TC_word    = tcMailbox.message;
			received_command.TC_address = (char) TC_ADDRESS (received_command.TC_word);
			received_command.TC_code    = (char) TC_CODE    (received_command.TC_word);

			if (((TC_state == TC_State.SC_TM_e) || (TC_state == TC_State.memory_dump_e)) &&
					(received_command.TC_word == TM_READY))

				/* Note that in order to this condition to be sufficient, only */
				/* TM interrupt service should be allowed to send mail to this */
				/* task in the TC states mentioned.                            */

			{
				taskControl.disableInterrupt(KernelObjects.TM_ISR_SOURCE);

				if (TC_state == TC_State.SC_TM_e)
				{
					clearEvents();
				}

				TC_state = TC_State.TC_handling_e;
			}
			else
			{
				/* XXX: work around ticket #9 for JOP, broken switch/case for enums */
//				switch (TC_state) {
//				case read_memory_e:
//					if (received_command.TC_address != TcAddress.READ_DATA_MEMORY_LSB) {
//						setTCError();
//						TC_state = TC_State.TC_handling_e;
//					}
//					break;
//				case write_memory_e:
//					writeMemory (received_command);
//					break;
//				case memory_patch_e:
//					memoryPatch (received_command);
//					break;
//				case TC_handling_e:
//					executeCommand(received_command);
//					break;
//				}

				if (TC_state == TC_State.read_memory_e) {
					if (received_command.TC_address != TcAddress.READ_DATA_MEMORY_LSB) {
						setTCError();
						TC_state = TC_State.TC_handling_e;
					}					
				} else if (TC_state == TC_State.write_memory_e) {
					writeMemory(received_command);					
				} else if (TC_state == TC_State.memory_patch_e) {
					memoryPatch(received_command);
				} else if (TC_state == TC_State.TC_handling_e) {
					executeCommand(received_command);			
				}
			}
			previous_TC.copyFrom(received_command);
		}

		else
		{
			/* Nothing is done if WaitMail returns an error message. */
		}
	}

	// XXX: pulled out of updateTarget, which breaks re-entrance, but avoids memory allocation
	private final TriggerSet new_threshold = new TriggerSet();
	
	/** Purpose         : Updates a HW register or some gl42obal variable according
	 *                   to the parameter "command"
	 * Interface       : inputs  - Parameter "command" containing received
	 *                             telecommand
	 *                   outputs - telemetry data
	 * Preconditions  : The parameter "command" contains a valid telecommand
	 * Postconditions  : A HW register or global variable is updated (depend on
	 *                   "command")
	 * Algorithm       : - switch "command"
	 *                     - case Set Coefficient:
	 *                          set given quality coefficient value in telemetry
	 *                          according to "command"
	 *                     - case Min/Max Time:
	 *                          set Min/Max Time according to "command"
	 *                     - case Classification Level:
	 *                          set classification level according to "command"
	 *                     - case Error Status Clear:
	 *                          clear error indicating bits from telemetry
	 *                     - case Set Time Byte:
	 *                          set Debie time byte# according to "command"
	 *                     - case Clear Watchdog/Checksum Failure counter
	 *                          clear Watchdog/Checksum Failure counter
	 *                     - case Switch SU # On/Off/SelfTest
	 *                          switch SU to On/Off/SelfTest according to
	 *                          "command"
	 *                     - case Set Threshold
	 *                          set Threshold according to "command"
	 */
	void updateTarget(Telecommand command) {
		
		int /* sensor_index_t */ SU_index;

		SU_index = ((command.TC_address) >> 4) - 2;

		switch (command.TC_address)
		{
		case TcAddress.SET_COEFFICIENT_1:
		case TcAddress.SET_COEFFICIENT_2:
		case TcAddress.SET_COEFFICIENT_3:
		case TcAddress.SET_COEFFICIENT_4:
		case TcAddress.SET_COEFFICIENT_5:

			telemetry_data.coefficient[(command.TC_address)&0x07] =
				(byte) command.TC_code;
			break;

		case TcAddress.SET_SU_1_PLASMA_1E_1I_MAX_TIME:
		case TcAddress.SET_SU_2_PLASMA_1E_1I_MAX_TIME:
		case TcAddress.SET_SU_3_PLASMA_1E_1I_MAX_TIME:
		case TcAddress.SET_SU_4_PLASMA_1E_1I_MAX_TIME:

			telemetry_data.getSuConfig(SU_index).plasma_1_plus_to_minus_max_time =
				command.TC_code;
			break;

		case TcAddress.SET_SU_1_PLASMA_1E_PZT_MIN_TIME:
		case TcAddress.SET_SU_2_PLASMA_1E_PZT_MIN_TIME:
		case TcAddress.SET_SU_3_PLASMA_1E_PZT_MIN_TIME:
		case TcAddress.SET_SU_4_PLASMA_1E_PZT_MIN_TIME:

			telemetry_data.getSuConfig(SU_index).plasma_1_plus_to_piezo_min_time =
				command.TC_code;
			break;

		case TcAddress.SET_SU_1_PLASMA_1E_PZT_MAX_TIME:
		case TcAddress.SET_SU_2_PLASMA_1E_PZT_MAX_TIME:
		case TcAddress.SET_SU_3_PLASMA_1E_PZT_MAX_TIME:
		case TcAddress.SET_SU_4_PLASMA_1E_PZT_MAX_TIME:

			telemetry_data.getSuConfig(SU_index).plasma_1_plus_to_piezo_max_time =
				command.TC_code;
			break;

		case TcAddress.SET_SU_1_PLASMA_1I_PZT_MIN_TIME:
		case TcAddress.SET_SU_2_PLASMA_1I_PZT_MIN_TIME:
		case TcAddress.SET_SU_3_PLASMA_1I_PZT_MIN_TIME:
		case TcAddress.SET_SU_4_PLASMA_1I_PZT_MIN_TIME:

			telemetry_data.getSuConfig(SU_index).plasma_1_minus_to_piezo_min_time =
				command.TC_code;
			break;

		case TcAddress.SET_SU_1_PLASMA_1I_PZT_MAX_TIME:
		case TcAddress.SET_SU_2_PLASMA_1I_PZT_MAX_TIME:
		case TcAddress.SET_SU_3_PLASMA_1I_PZT_MAX_TIME:
		case TcAddress.SET_SU_4_PLASMA_1I_PZT_MAX_TIME:

			telemetry_data.getSuConfig(SU_index).plasma_1_minus_to_piezo_max_time =
				command.TC_code;
			break;

		case TcAddress.SET_SU_1_PLASMA_1P_CLASS_LEVEL:
		case TcAddress.SET_SU_2_PLASMA_1P_CLASS_LEVEL:
		case TcAddress.SET_SU_3_PLASMA_1P_CLASS_LEVEL:
		case TcAddress.SET_SU_4_PLASMA_1P_CLASS_LEVEL:

			telemetry_data.getSuConfig(SU_index).plasma_1_plus_classification = 
				command.TC_code;
			break;

		case TcAddress.SET_SU_1_PLASMA_1M_CLASS_LEVEL:
		case TcAddress.SET_SU_2_PLASMA_1M_CLASS_LEVEL:
		case TcAddress.SET_SU_3_PLASMA_1M_CLASS_LEVEL:
		case TcAddress.SET_SU_4_PLASMA_1M_CLASS_LEVEL:

			telemetry_data.getSuConfig(SU_index).plasma_1_minus_classification = 
				command.TC_code;
			break;

		case TcAddress.SET_SU_1_PLASMA_2P_CLASS_LEVEL:
		case TcAddress.SET_SU_2_PLASMA_2P_CLASS_LEVEL:
		case TcAddress.SET_SU_3_PLASMA_2P_CLASS_LEVEL:
		case TcAddress.SET_SU_4_PLASMA_2P_CLASS_LEVEL:

			telemetry_data.getSuConfig(SU_index).plasma_2_plus_classification = 
				command.TC_code;
			break;

		case TcAddress.SET_SU_1_PIEZO_1_CLASS_LEVEL:
		case TcAddress.SET_SU_2_PIEZO_1_CLASS_LEVEL:
		case TcAddress.SET_SU_3_PIEZO_1_CLASS_LEVEL:
		case TcAddress.SET_SU_4_PIEZO_1_CLASS_LEVEL:

			telemetry_data.getSuConfig(SU_index).piezo_1_classification = 
				command.TC_code;
			break;

		case TcAddress.SET_SU_1_PIEZO_2_CLASS_LEVEL:
		case TcAddress.SET_SU_2_PIEZO_2_CLASS_LEVEL:
		case TcAddress.SET_SU_3_PIEZO_2_CLASS_LEVEL:
		case TcAddress.SET_SU_4_PIEZO_2_CLASS_LEVEL:

			telemetry_data.getSuConfig(SU_index).piezo_2_classification = 
				command.TC_code;
			break;

		case TcAddress.ERROR_STATUS_CLEAR:

			telemetry_data.clearErrorStatus();
			telemetry_data.clearRTXErrors();
			telemetry_data.clearSoftwareError();
			telemetry_data.clearModeStatusError();
			telemetry_data.clearSUError();

			/* Clear Error Status register, RTX and software error indicating bits  */
			/* and Mode and SU Status registers.                                   */

			break;


		case TcAddress.SET_TIME_BYTE_3:

			// new_time = ((dpu_time_t) command.TC_code) << 24;
			// COPY (internal_time, new_time);
			system.getInternalTime().updateWithMask(0xFFFFFFFF, (int)command.TC_code << 24);
			TC_timeout = SET_TIME_TC_TIMEOUT;

			break;

		case TcAddress.SET_TIME_BYTE_2:

			if (previous_TC.TC_address == TcAddress.SET_TIME_BYTE_3)
			{
				system.getInternalTime().updateWithMask(0x00FFFFFF, (int)command.TC_code << 16);
				TC_timeout = SET_TIME_TC_TIMEOUT;
			}

			else
			{
				setTCError();
			}

			break;

		case TcAddress.SET_TIME_BYTE_1:

			if (previous_TC.TC_address == TcAddress.SET_TIME_BYTE_2)
			{
				system.getInternalTime().updateWithMask(0x0000FFFF, (int)command.TC_code << 8);
				TC_timeout = SET_TIME_TC_TIMEOUT;
			}

			else
			{
				setTCError();
			}

			break;

		case TcAddress.SET_TIME_BYTE_0:

			if (previous_TC.TC_address == TcAddress.SET_TIME_BYTE_1)
			{
				system.getInternalTime().updateWithMask(0x000000FF, (int)command.TC_code);
			}

			else
			{
				setTCError();
			}

			break;

		case TcAddress.CLEAR_WATCHDOG_FAILURES:

			telemetry_data.watchdog_failures = 0;
			break;

		case TcAddress.CLEAR_CHECKSUM_FAILURES:

			telemetry_data.checksum_failures = 0;
			break;

		case TcAddress.SWITCH_SU_1:
		case TcAddress.SWITCH_SU_2:
		case TcAddress.SWITCH_SU_3:
		case TcAddress.SWITCH_SU_4:

			if (telemetry_data.getMode() != TelemetryData.ACQUISITION)
			{
				SU_setting.number = SU_index + SensorUnitDev.SU_1;

				// XXX: avoid lookupswitch
				int TC_code = command.TC_code;
				
//				switch (command.TC_code)
//				{
//				case TcAddress.ON_VALUE:
//					startSensorUnitSwitchingOn(SU_index, SU_setting);
//					break;
//
//				case TcAddress.OFF_VALUE:
//					setSensorUnitOff(SU_index, SU_setting);
//					break;
//
//				case TcAddress.SELF_TEST:
//					SU_setting.state                 = SensorUnitState.self_test_mon_e;
//					SU_setting.expected_source_state = SensorUnitState.on_e;
//					switchSensorUnitState (SU_setting);
//					break;
//				}
				if (TC_code == TcAddress.ON_VALUE) {
					startSensorUnitSwitchingOn(SU_index, SU_setting);
					
				} else if (TC_code == TcAddress.OFF_VALUE) {
					setSensorUnitOff(SU_index, SU_setting);
					
				} else if (TC_code == TcAddress.SELF_TEST) {
					SU_setting.state                 = SensorUnitState.self_test_mon_e;
					SU_setting.expected_source_state = SensorUnitState.on_e;
					switchSensorUnitState (SU_setting);
				}
				
				
				if (SU_setting.execution_result == SensorUnitDev.SU_STATE_TRANSITION_FAILED)
				{
					/* The requested SU state transition failed. */

					setTCError();
				}

			}

			else

			{
				setTCError();
			}

			break;

		case TcAddress.SET_SU_1_PLASMA_1P_THRESHOLD:

			new_threshold.sensor_unit = SensorUnitDev.SU_1;
			new_threshold.channel     = SensorUnitDev.PLASMA_1_PLUS;
			new_threshold.level       = command.TC_code;
			system.getSensorUnitDevice().setTriggerLevel(new_threshold);

			telemetry_data.sensor_unit_1.plasma_1_plus_threshold = 
				command.TC_code;
			break;

		case TcAddress.SET_SU_2_PLASMA_1P_THRESHOLD:

			new_threshold.sensor_unit = SensorUnitDev.SU_2;
			new_threshold.channel     = SensorUnitDev.PLASMA_1_PLUS;
			new_threshold.level       = command.TC_code;
			system.getSensorUnitDevice().setTriggerLevel(new_threshold);

			telemetry_data.sensor_unit_2.plasma_1_plus_threshold = 
				command.TC_code;
			break;

		case TcAddress.SET_SU_3_PLASMA_1P_THRESHOLD:

			new_threshold.sensor_unit = SensorUnitDev.SU_3;
			new_threshold.channel     = SensorUnitDev.PLASMA_1_PLUS;
			new_threshold.level       = command.TC_code;
			system.getSensorUnitDevice().setTriggerLevel(new_threshold);

			telemetry_data.sensor_unit_3.plasma_1_plus_threshold = 
				command.TC_code;
			break;

		case TcAddress.SET_SU_4_PLASMA_1P_THRESHOLD:

			new_threshold.sensor_unit = SensorUnitDev.SU_4;
			new_threshold.channel     = SensorUnitDev.PLASMA_1_PLUS;
			new_threshold.level       = command.TC_code;
			system.getSensorUnitDevice().setTriggerLevel(new_threshold);

			telemetry_data.sensor_unit_4.plasma_1_plus_threshold = 
				command.TC_code;
			break;

		case TcAddress.SET_SU_1_PLASMA_1M_THRESHOLD:

			new_threshold.sensor_unit = SensorUnitDev.SU_1;
			new_threshold.channel     = SensorUnitDev.PLASMA_1_MINUS;
			new_threshold.level       = command.TC_code;
			system.getSensorUnitDevice().setTriggerLevel(new_threshold);

			telemetry_data.sensor_unit_1.plasma_1_minus_threshold = 
				command.TC_code;
			break;

		case TcAddress.SET_SU_2_PLASMA_1M_THRESHOLD:

			new_threshold.sensor_unit = SensorUnitDev.SU_2;
			new_threshold.channel     = SensorUnitDev.PLASMA_1_MINUS;
			new_threshold.level       = command.TC_code;
			system.getSensorUnitDevice().setTriggerLevel(new_threshold);

			telemetry_data.sensor_unit_2.plasma_1_minus_threshold = 
				command.TC_code;
			break;

		case TcAddress.SET_SU_3_PLASMA_1M_THRESHOLD:

			new_threshold.sensor_unit = SensorUnitDev.SU_3;
			new_threshold.channel     = SensorUnitDev.PLASMA_1_MINUS;
			new_threshold.level       = command.TC_code;
			system.getSensorUnitDevice().setTriggerLevel(new_threshold);

			telemetry_data.sensor_unit_3.plasma_1_minus_threshold = 
				command.TC_code;
			break;

		case TcAddress.SET_SU_4_PLASMA_1M_THRESHOLD:

			new_threshold.sensor_unit = SensorUnitDev.SU_4;
			new_threshold.channel     = SensorUnitDev.PLASMA_1_MINUS;
			new_threshold.level       = command.TC_code;
			system.getSensorUnitDevice().setTriggerLevel(new_threshold);

			telemetry_data.sensor_unit_4.plasma_1_minus_threshold = 
				command.TC_code;
			break;

		case TcAddress.SET_SU_1_PIEZO_THRESHOLD:

			new_threshold.sensor_unit = SensorUnitDev.SU_1;
			new_threshold.channel     = SensorUnitDev.PZT_1_2;
			new_threshold.level       = command.TC_code;
			system.getSensorUnitDevice().setTriggerLevel(new_threshold);

			telemetry_data.sensor_unit_1.piezo_threshold = command.TC_code;
			break;

		case TcAddress.SET_SU_2_PIEZO_THRESHOLD:

			new_threshold.sensor_unit = SensorUnitDev.SU_2;
			new_threshold.channel     = SensorUnitDev.PZT_1_2;
			new_threshold.level       = command.TC_code;
			system.getSensorUnitDevice().setTriggerLevel(new_threshold);

			telemetry_data.sensor_unit_2.piezo_threshold = command.TC_code;
			break;

		case TcAddress.SET_SU_3_PIEZO_THRESHOLD:

			new_threshold.sensor_unit = SensorUnitDev.SU_3;
			new_threshold.channel     = SensorUnitDev.PZT_1_2;
			new_threshold.level       = command.TC_code;
			system.getSensorUnitDevice().setTriggerLevel(new_threshold);

			telemetry_data.sensor_unit_3.piezo_threshold = command.TC_code;
			break;

		case TcAddress.SET_SU_4_PIEZO_THRESHOLD:

			new_threshold.sensor_unit = SensorUnitDev.SU_4;
			new_threshold.channel     = SensorUnitDev.PZT_1_2;
			new_threshold.level       = command.TC_code;
			system.getSensorUnitDevice().setTriggerLevel(new_threshold);

			telemetry_data.sensor_unit_4.piezo_threshold = command.TC_code;
			break;

		default:
			/* Telecommands that will not be implemented in the Prototype SW */
			break;
		}
	}

	// XXX: pulled out of memoryPatch, which breaks re-entrance, but avoids memory allocation
	private final Dpu.MemoryPatchVariables patch_info = new Dpu.MemoryPatchVariables();
	 
	/**
	 * Purpose        : Handles received telecommand in memory patch state
	 * Interface      : inputs  - command, received telecommand
	 *                            address_MSB, MSB of patch area
	 *                            address_LSB, LSB of patch area
	 *                            memory_transfer_buffer, buffer for bytes
	 *                               to be written to patch area
	 *                            memory_buffer_index, index to above buffer
	 *                  outputs - memory_transfer_buffer, as above
	 *                            memory_buffer_index, as above
	 *                            code_not_patched, indicates if code
	 *                               checksum is vali or not
	 *                            telemetry_data.mode_status, Mode Status
	 *                               telemetry register
	 *                            TC_state, TC Execution task state
	 * Preconditions  : TC_state is memory patch.
	 *                  Destination memory type (data/code) is memorized.
	 * Postconditions : After last phase code or data memory patched if
	 *                     operation succeeded.
	 * Algorithm      : - If memory_transfer_buffer not filled
	 *                     - update write_checksum (XOR with MSB and LSB of
	 *                          the telecommand word)
	 *                     - store TC word to memory_transfer_buffer
	 *                  - else
	 *                     - If checksum of TC block Ok
	 *                        - If data memory patch
	 *                           - copy memory_tranfer_buffer to data memory
	 *                        - else (code memory patch)
	 *                           - call PatchCode function
	 *                     - else
	 *                        - set MEMORY_WRITE_ERROR bit in ModeStatus
	 */
	private void memoryPatch(Telecommand command) {

		int address;
		/* Start address of the memory area to be patched. */

		int TC_msb;
		/* Most significant byte of the TC word. */

		TC_msb = (command.TC_word) >> 8;

		write_checksum ^= TC_msb;

		if (memory_buffer_index < MEM_BUFFER_SIZE) {
			/* Filling the buffer bytes to be written to code or data */
			/* memory.                                                */

			write_checksum ^= command.TC_code;

			memory_transfer_buffer[memory_buffer_index] = (byte)TC_msb;
			memory_buffer_index++;
			memory_transfer_buffer[memory_buffer_index] = (byte)command.TC_code;
			memory_buffer_index++;

			TC_timeout = WRITE_MEMORY_TIMEOUT;

		} else {
			/* Now all bytes for memory area to be patched have been */
			/* received.                                             */

			if (write_checksum == command.TC_code) {
				/* Checksum Ok. */

				address = (address_MSB)*256 + address_LSB;

		         if (memory_type == MemoryType.Data) {
		        	 /* Write to the data memory. */

		        	 if (address <= (Dpu.END_SRAM3 - MEM_BUFFER_SIZE + 1)) {
		                  for(int i=0; i<MEM_BUFFER_SIZE; i++) {
		                	  Dpu.setDataByte(address + i, memory_transfer_buffer[i]);
		                  }

		        	 } else {
		        		 setTCError();
		        	 }
		         } else if (memory_type == MemoryType.Code) { 
		        	 /* Write to the code memory. */

		        	 if ((address >= Dpu.BEGIN_SRAM1) && 
		        			 (address <= Dpu.END_SRAM1 - MEM_BUFFER_SIZE + 1) &&
		        			 (Dpu.patchExecCommandOk(TC_msb))) {
		        		 /* Destination section resides in SRAM1.   */
		 
		        		 Dpu.code_not_patched = 0;
		        		 /* Next code checksum not valid, because code memory */
		        		 /* will be patched.                                  */

		        		 patch_info.source            = memory_transfer_buffer;
		        		 patch_info.destination       = address;
		        		 patch_info.data_amount       = MEM_BUFFER_SIZE;
		        		 patch_info.execution_command = TC_msb;
		        		 /* Set parameters for the MemoryPatch function   */
		        		 /* (see definition of memory_patch_variables_t). */

		        		 Dpu.patchCode(patch_info);
		        		 /* May or may not return here. */

		        	 } else {
		        		 /* Destination section out side SRAM1.   */
		        		 setTCError();
		        	 }
		         }

			} else {
				/* Checksum failure. */

				system.getHealthMonitoringTask().setModeStatusError(MEMORY_WRITE_ERROR);
				/* Set memory write error bit in Mode Status register. */
			}

			TC_state = TC_State.TC_handling_e;		
		}
	}
	
	/**
	 * Purpose        : Handles received telecommand in the WriteMemory state
	 * Interface      : inputs  - Parameter "command" containing received
	 *                            telecommand or memory byte
	 *                            memory_type defining code or data memory
	 *                            destination
	 *                  outputs - address_LSB, LSB of the patch area
	 *                            TC_state
	 * Preconditions  : TC_state is write_memory
	 *                  Destination memory type (data/code) is memorized
	 * Postconditions : LSB of the patch area address is memorized.
	 * Algorithm      : - update write_checksum (XOR with TC word MSB and LSB)
	 *                  - if telecommand is LSB setting of patch memory address
	 *                    of selected memory type
	 *                      - set TC state to memory_patch_e
	 *                      - memorize address LSB
	 *                      - clear memory_buffer_index
	 *                      - set TC timeout to WRITE_MEMORY_TIMEOUT
	 *                  - else
	 *                      - set TC state to TC_handling_e
	 */
	private void writeMemory(Telecommand command) {

		/* Expecting LSB of the start address for the memory area to be patched. */

		write_checksum ^= ((command.TC_word) >> 8) ^ (command.TC_code);

		if (((command.TC_address == TcAddress.WRITE_CODE_MEMORY_LSB)
				&& (memory_type == MemoryType.Code))
				|| ((command.TC_address == TcAddress.WRITE_DATA_MEMORY_LSB)
						&& (memory_type == MemoryType.Data)))
		{
			TC_state = TC_State.memory_patch_e;

			address_LSB = command.TC_code;
			memory_buffer_index = 0;
			TC_timeout = WRITE_MEMORY_TIMEOUT;
		}

		else
		{
			TC_state = TC_State.TC_handling_e;

			setTCError();
		}
	}		

	// XXX: pulled out of executeCommand/updateTarget, which breaks re-entrance, but avoids memory allocation	
	private final SensorUnit SU_setting = new SensorUnit(); /* bad name choice (original from DEBIE) */

	/** Purpose        : Executes telecommand
	 * Interface      : inputs      - Parameter "command" containing received
	 *                                telecommand
	 *                  outputs     - TC_state
	 *                                address_MSB
	 *                                memory_type
	 *                  subroutines - UpdateTarget
	 *                                StartAcquisition
	 *                                StopAcquisition
	 *                                SoftReset
	 *                                Set_TC_Error
	 *                                Switch_SU_State
	 *                                Reboot
	 *                                SetMode
	 *                                UpdateTarget
	 * Preconditions  : The parameter "command" contains a valid telecommand
	 *                  TC_state is TC_handling
	 * Postconditions : Telecommand is executed
	 *                  TC_state updated when appropriate (depend on "command")
	 *                  HW registers modified when appropriate (depend on
	 *                  "command")
	 *                  Global variables modified when appropriate (depend on
	 *                  "command")
	 * Algorithm      : - switch "command"
	 *                    - case Send Sciece Data File : set TC_state to SC_TM
	 *                    - case Send Status Register  : set TC_state to
	 *                         RegisterTM
	 *                    - case Read Data Memory MSB  : memorize the address
	 *                         MSB given in the TC_code and set TC_state to
	 *                         read_memory
	 *                    - case Write Code Memory MSB : memorize the address
	 *                         MSB given in the TC_code, memorize code
	 *                         destination selection and set TC_state to
	 *                         write_memory
	 *                    - case Write Data Memory MSB : memorize the address
	 *                         MSB given in the TC_code, memorize data
	 *                         destination selection and set TC_state to
	 *                         write_memory
	 *                    - case Read/Write Memory LSB : ignore telecommand
	 *                    - case Soft Reset            : call SoftReset
	 *                    - case Start Acquisition     : call StartAcquisition
	 *                    - case Stop Acquisition      : call StopAcquisition
	 *                    - default : call UpdateTarget
	 */
	private void executeCommand(Telecommand command) {

		{
			int /* unsigned char */ error_flag;
			int /* sensor_number_t */  i;

			// XXX: avoid lookupswitch
			int TC_address = command.TC_address;
			
			if (TC_address == TcAddress.SEND_SCIENCE_DATA_FILE) {
				
			} else if (TC_address == TcAddress.SEND_STATUS_REGISTER) {
				
			} else if (TC_address == TcAddress.READ_DATA_MEMORY_MSB) {
				address_MSB = command.TC_code;
				TC_state    = TC_State.read_memory_e;
				
			} else if (TC_address == TcAddress.WRITE_CODE_MEMORY_MSB) {
				if (telemetry_data.getMode() == TelemetryData.STAND_BY)
				{
					address_MSB    = command.TC_code;
					memory_type    = MemoryType.Code;
					TC_timeout     = WRITE_MEMORY_TIMEOUT;
					write_checksum = ((command.TC_word) >> 8) ^ (command.TC_code);
					TC_state       = TC_State.write_memory_e;
				}
				else
				{
					setTCError();
				}
			} else if (TC_address == TcAddress.WRITE_DATA_MEMORY_MSB) {
				if (telemetry_data.getMode() == TelemetryData.STAND_BY)
				{
					address_MSB = command.TC_code;
					memory_type = MemoryType.Data;
					TC_timeout     = WRITE_MEMORY_TIMEOUT;
					write_checksum = ((command.TC_word) >> 8) ^ (command.TC_code);
					TC_state    = TC_State.write_memory_e;
				}
				else
				{
					setTCError();
				}
			} else if (TC_address == TcAddress.READ_DATA_MEMORY_LSB) {
				
			} else if (TC_address == TcAddress.WRITE_CODE_MEMORY_LSB
					|| TC_address == TcAddress.WRITE_DATA_MEMORY_LSB) {
				if (TC_state != TC_State.write_memory_e)
				{
					setTCError();
				}
			} else if (TC_address == TcAddress.SOFT_RESET) {
				Dpu.reboot(Dpu.ResetClass.soft_reset_e);
				/* Software is rebooted, no return to this point. */
				
			} else if (TC_address == TcAddress.START_ACQUISITION) {
				error_flag = 0;

				for (i=SensorUnitDev.SU_1; i<=SensorUnitDev.SU_4; i++)
				{		        	 
					if ((readSensorUnit(i) == SensorUnitState.start_switching_e) ||
							(readSensorUnit(i) == SensorUnitState.switching_e))
					{
						/* SU is being switched on. */

						error_flag = 1;
						/* StartAcquisition TC has to be rejected. */
					}
				}

				if ((telemetry_data.getMode() == TelemetryData.STAND_BY) && (error_flag == 0))
				{
					SU_setting.state              = SensorUnitState.acquisition_e;
					SU_setting.expected_source_state = SensorUnitState.on_e;

					SU_setting.number = SensorUnitDev.SU_1;
					switchSensorUnitState (SU_setting);
					/* Try to switch SU 1 to Acquisition state. */

					SU_setting.number = SensorUnitDev.SU_2;
					switchSensorUnitState (SU_setting);
					/* Try to switch SU 2 to Acquisition state. */

					SU_setting.number = SensorUnitDev.SU_3;
					switchSensorUnitState (SU_setting);
					/* Try to switch SU 3 to Acquisition state. */

					SU_setting.number = SensorUnitDev.SU_4;
					switchSensorUnitState (SU_setting);
					/* Try to switch SU 4 to Acquisition state. */

					taskControl.clearHitTriggerISRFlag();

					HwIf.resetDelayCounters();
					/* Resets the SU logic that generates Hit Triggers.    */
					/* Brings T2EX to a high level, making a new falling   */
					/* edge possible.                                      */
					/* This statement must come after the above "clear",   */
					/* because a reversed order could create a deadlock    */
					/* situation.                                          */

					system.getHealthMonitoringTask().setMode(TelemetryData.ACQUISITION);
				}
				else
				{
					setTCError();
				}
			} else if (TC_address == TcAddress.STOP_ACQUISITION) {
				if (telemetry_data.getMode() == TelemetryData.ACQUISITION)
				{
					SU_setting.state              = SensorUnitState.on_e;
					SU_setting.expected_source_state = SensorUnitState.acquisition_e;

					SU_setting.number = SensorUnitDev.SU_1;
					switchSensorUnitState (SU_setting);
					/* Try to switch SU 1 to On state. */

					SU_setting.number = SensorUnitDev.SU_2;
					switchSensorUnitState (SU_setting);
					/* Try to switch SU 2 to On state. */

					SU_setting.number = SensorUnitDev.SU_3;
					switchSensorUnitState (SU_setting);
					/* Try to switch SU 3 to On state. */

					SU_setting.number = SensorUnitDev.SU_4;
					switchSensorUnitState (SU_setting);
					/* Try to switch SU 4 to On state. */

					system.getHealthMonitoringTask().setMode(TelemetryData.STAND_BY);
				}

				else
				{
					setTCError();
				}
			} else {
				updateTarget(command);
			}
// XXX: Work around broken switch for enums
//			switch (command.TC_address)
//			{
//			case TcAddress.SEND_SCIENCE_DATA_FILE:
//				break;
//
//			case TcAddress.SEND_STATUS_REGISTER:
//				break;
//
//			case TcAddress.READ_DATA_MEMORY_MSB:
//				address_MSB = command.TC_code;
//				TC_state    = TC_State.read_memory_e;
//				break;
//
//			case TcAddress.WRITE_CODE_MEMORY_MSB:
//				if (telemetry_data.getMode() == TelemetryData.STAND_BY)
//				{
//					address_MSB    = command.TC_code;
//					memory_type    = MemoryType.Code;
//					TC_timeout     = WRITE_MEMORY_TIMEOUT;
//					write_checksum = ((command.TC_word) >> 8) ^ (command.TC_code);
//					TC_state       = TC_State.write_memory_e;
//				}
//
//				else
//				{
//					setTCError();
//				}
//
//				break;
//
//			case TcAddress.WRITE_DATA_MEMORY_MSB:
//				if (telemetry_data.getMode() == TelemetryData.STAND_BY)
//				{
//					address_MSB = command.TC_code;
//					memory_type = MemoryType.Data;
//					TC_timeout     = WRITE_MEMORY_TIMEOUT;
//					write_checksum = ((command.TC_word) >> 8) ^ (command.TC_code);
//					TC_state    = TC_State.write_memory_e;
//				}
//
//				else
//				{
//					setTCError();
//				}
//
//				break;
//
//			case TcAddress.READ_DATA_MEMORY_LSB:
//				break;
//
//			case TcAddress.WRITE_CODE_MEMORY_LSB:
//			case TcAddress.WRITE_DATA_MEMORY_LSB:
//				if (TC_state != TC_State.write_memory_e)
//				{
//					setTCError();
//				}
//				break;
//
//			case TcAddress.SOFT_RESET:
//				Dpu.reboot(Dpu.ResetClass.soft_reset_e);
//				/* Software is rebooted, no return to this point. */
//
//				break;
//
//			case TcAddress.START_ACQUISITION:
//				error_flag = 0;
//
//				for (i=SensorUnitDev.SU_1; i<=SensorUnitDev.SU_4; i++)
//				{		        	 
//					if ((readSensorUnit(i) == SensorUnitState.start_switching_e) ||
//							(readSensorUnit(i) == SensorUnitState.switching_e))
//					{
//						/* SU is being switched on. */
//
//						error_flag = 1;
//						/* StartAcquisition TC has to be rejected. */
//					}
//				}
//
//				if ((telemetry_data.getMode() == TelemetryData.STAND_BY) && (error_flag == 0))
//				{
//					SU_setting.state              = SensorUnitState.acquisition_e;
//					SU_setting.expected_source_state = SensorUnitState.on_e;
//
//					SU_setting.number = SensorUnitDev.SU_1;
//					switchSensorUnitState (SU_setting);
//					/* Try to switch SU 1 to Acquisition state. */
//
//					SU_setting.number = SensorUnitDev.SU_2;
//					switchSensorUnitState (SU_setting);
//					/* Try to switch SU 2 to Acquisition state. */
//
//					SU_setting.number = SensorUnitDev.SU_3;
//					switchSensorUnitState (SU_setting);
//					/* Try to switch SU 3 to Acquisition state. */
//
//					SU_setting.number = SensorUnitDev.SU_4;
//					switchSensorUnitState (SU_setting);
//					/* Try to switch SU 4 to Acquisition state. */
//
//					taskControl.clearHitTriggerISRFlag();
//
//					HwIf.resetDelayCounters();
//					/* Resets the SU logic that generates Hit Triggers.    */
//					/* Brings T2EX to a high level, making a new falling   */
//					/* edge possible.                                      */
//					/* This statement must come after the above "clear",   */
//					/* because a reversed order could create a deadlock    */
//					/* situation.                                          */
//
//					system.getHealthMonitoringTask().setMode(TelemetryData.ACQUISITION);
//				}
//
//				else
//				{
//					setTCError();
//				}
//				break;
//
//			case TcAddress.STOP_ACQUISITION:
//				if (telemetry_data.getMode() == TelemetryData.ACQUISITION)
//				{
//					SU_setting.state              = SensorUnitState.on_e;
//					SU_setting.expected_source_state = SensorUnitState.acquisition_e;
//
//					SU_setting.number = SensorUnitDev.SU_1;
//					switchSensorUnitState (SU_setting);
//					/* Try to switch SU 1 to On state. */
//
//					SU_setting.number = SensorUnitDev.SU_2;
//					switchSensorUnitState (SU_setting);
//					/* Try to switch SU 2 to On state. */
//
//					SU_setting.number = SensorUnitDev.SU_3;
//					switchSensorUnitState (SU_setting);
//					/* Try to switch SU 3 to On state. */
//
//					SU_setting.number = SensorUnitDev.SU_4;
//					switchSensorUnitState (SU_setting);
//					/* Try to switch SU 4 to On state. */
//
//					system.getHealthMonitoringTask().setMode(TelemetryData.STAND_BY);
//				}
//
//				else
//				{
//					setTCError();
//				}
//				break;
//
//			default:
//				updateTarget(command);
//			}
		}            		
	}
	
	/** Purpose        :  To find out whether given Sensor Unit is switched on or
	 *                   off.
	 * Interface      :
	 * Preconditions  :  SU_Number should be 1,2,3 or 4.
	 * Postconditions :  Value of state variable is returned.
	 * Algorithm      :  Value of state variable (on_e or off_e) is returned.
	 */
	private SensorUnitState readSensorUnit(int suNumber) {
		
		   return system.getAcquisitionTask().sensorUnitState[suNumber - 1];      
	}

	/* from health.c: 373-398 
	 * Within TcExecTask, use telemetry_data.getMode() directly */

	/** Purpose        : This function will be called always when
	 *                  mode in the mode status register is checked.
	 * Interface      :
	 *                  inputs      - mode status register
	 *
	 *                  outputs     - mode status register
	 *                              - Mode bits, which specify the mode
	 *                                stored in the ModeStatus register.
	 *                                Value is on one of the following:
	 *                                   DPU self test
	 *                                   stand by
	 *                                   acquisition
	 *
	 *                  subroutines - none
	 * Preconditions  : none
	 * Postconditions : none
	 * Algorithm      :
	 *                  - Read Mode Status register
	 */
	int getMode() {
		
		return(telemetry_data.getMode());
		/* Return the value of the two least significant bits in */
		/* mode status register and return this value.           */    
	}

	/** Purpose        : This function will be called always when TC_ERROR bit in
	 *                  the ErrorStatus register is set.
	 * Interface      : inputs      - Error_status register
	 *                  outputs     - Error_status register
	 *                  subroutines - none
	 * Preconditions  : none
	 * Postconditions : none
	 * Algorithm      : - Disable interrupts
	 *                  - Write to Error Status register
	 *                  - Enable interrupts
	 */
	private void setTCError() {
		
		taskControl.disableInterruptMaster();
		
		telemetry_data.error_status |= TcTmDev.TC_ERROR;

		taskControl.enableInterruptMaster();
	}

	/** Purpose        : This function handles the TM interrupts.
	 * Interface      : inputs  - telemetry_pointer
	 *                            telemetry_end_pointer
	 *                            TC_state
	 *                            telemetry_data
	 *                  outputs - telemetry_pointer
	 *                            TM HW reigsiters
	 *                            Telemcommand Execution task mailbox
	 * Preconditions  : telemetry_pointer and telemetry_end_pointer have valid
	 *                  values (TM interrupts should be enabled only when this
	 *                  condition is true)
	 * Postconditions : Next two bytes are written to TM HW registers and if they
	 *                  were the last bytes to be written, a "TM_READY" mail is
	 *                  sent to the Telecommand Execution task
	 * Algorithm      : - if telemetry_pointer < telemetry_end_pointer
	 *                     - write next two bytes from location pointed by
	 *                       telemetry_pointer and increase it by two
	 *                  - else if TC_state == register_TM_e
	 *                     - write first two TM data registers and set
	 *                       telemetry_pointer to point to the third TM data
	 *                       register
	 *                  - else
	 *                     - send TM_READY message to Telecommand Execution task
	 *                       mailbox
	 */
	public void tmInterruptService () { // INTERRUPT(TM_ISR_SOURCE) USED_REG_BANK(2)
	
		int /* unsigned char */ tm_byte;

		tctmDev.clearTmInterruptFlag();

		if (telemetry_object == telemetry_data && telemetry_index == TelemetryData.TIME_INDEX)		
		{
			telemetry_data.time.set(system.getInternalTime()); // @WC marker "copytime"
		}

		if (! telemetryIndexAtEnd())
		{
			/* There are bytes left to be sent to TM. */
			tm_byte = telemetryPointerNext();
			tctmDev.writeTmMsb(tm_byte);
			read_memory_checksum ^= tm_byte;

			telemetry_index++;

			tm_byte = telemetryPointerNext();
			tctmDev.writeTmLsb(tm_byte);
			read_memory_checksum ^= tm_byte;

			telemetry_index++;			
		}
		else if (TC_state == TC_State.register_TM_e)
			/* Start to send TM data registers starting from the first ones */
		{
			telemetry_object = telemetry_data;
			tctmDev.writeTmMsb (telemetryPointerNext());
			telemetry_index++;
			tctmDev.writeTmLsb (telemetryPointerNext());
			telemetry_index++;
		}
		else if (TC_state == TC_State.memory_dump_e)
		{
			tctmDev.writeTmMsb(0);
			tctmDev.writeTmLsb(read_memory_checksum);
			/* Last two bytes of Read Memory sequence. */

			tcMailbox.sendISRMail((char)TM_READY);
		}
		else
			/* It is time to stop sending telemetry */
		{
			tcMailbox.sendISRMail((char)TM_READY);  // @WC marker "sendmail"
		}
	}

	/** Purpose        : Initializes the TC look-up table
	 * Interface      : inputs  - none
	 *                  outputs - TC_lool_up
	 * Preconditions  : none
	 * Postconditions : TC_look_up is initialized
	 * Algorithm      : - set all elements in table to ALL_INVALID
	 *                  - set each element corresponding valid TC address
	 *                    to proper value
	 */
	void initTcLookup() {
		
		int /* uint_least8_t */ i;
		/* Loop variable */


		for(i=0; i<128; i++) TC_look_up[i] = ALL_INVALID;

		TC_look_up[TcAddress.START_ACQUISITION]            = ONLY_EQUAL;
		TC_look_up[TcAddress.STOP_ACQUISITION]             = ONLY_EQUAL;

		TC_look_up[TcAddress.ERROR_STATUS_CLEAR]           = ONLY_EQUAL;

		TC_look_up[TcAddress.SEND_STATUS_REGISTER]         = ONLY_EVEN;
		TC_look_up[TcAddress.SEND_SCIENCE_DATA_FILE]       = ONLY_EQUAL;

		TC_look_up[TcAddress.SET_TIME_BYTE_0]              = ALL_VALID;
		TC_look_up[TcAddress.SET_TIME_BYTE_1]              = ALL_VALID;
		TC_look_up[TcAddress.SET_TIME_BYTE_2]              = ALL_VALID;
		TC_look_up[TcAddress.SET_TIME_BYTE_3]              = ALL_VALID;

		TC_look_up[TcAddress.SOFT_RESET]                   = ONLY_EQUAL;

		TC_look_up[TcAddress.CLEAR_WATCHDOG_FAILURES]      = ONLY_EQUAL;
		TC_look_up[TcAddress.CLEAR_CHECKSUM_FAILURES]      = ONLY_EQUAL;

		TC_look_up[TcAddress.WRITE_CODE_MEMORY_MSB]        = ALL_VALID;
		TC_look_up[TcAddress.WRITE_CODE_MEMORY_LSB]        = ALL_VALID;
		TC_look_up[TcAddress.WRITE_DATA_MEMORY_MSB]        = ALL_VALID;
		TC_look_up[TcAddress.WRITE_DATA_MEMORY_LSB]        = ALL_VALID;
		TC_look_up[TcAddress.READ_DATA_MEMORY_MSB]         = ALL_VALID;
		TC_look_up[TcAddress.READ_DATA_MEMORY_LSB]         = ALL_VALID;

		TC_look_up[TcAddress.SWITCH_SU_1]                  = ON_OFF_TC;
		TC_look_up[TcAddress.SWITCH_SU_2]                  = ON_OFF_TC;
		TC_look_up[TcAddress.SWITCH_SU_3]                  = ON_OFF_TC;
		TC_look_up[TcAddress.SWITCH_SU_4]                  = ON_OFF_TC;

		TC_look_up[TcAddress.SET_SU_1_PLASMA_1P_THRESHOLD] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_2_PLASMA_1P_THRESHOLD] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_3_PLASMA_1P_THRESHOLD] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_4_PLASMA_1P_THRESHOLD] = ALL_VALID;

		TC_look_up[TcAddress.SET_SU_1_PLASMA_1M_THRESHOLD] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_2_PLASMA_1M_THRESHOLD] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_3_PLASMA_1M_THRESHOLD] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_4_PLASMA_1M_THRESHOLD] = ALL_VALID;

		TC_look_up[TcAddress.SET_SU_1_PIEZO_THRESHOLD]     = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_2_PIEZO_THRESHOLD]     = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_3_PIEZO_THRESHOLD]     = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_4_PIEZO_THRESHOLD]     = ALL_VALID;

		TC_look_up[TcAddress.SET_SU_1_PLASMA_1P_CLASS_LEVEL] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_2_PLASMA_1P_CLASS_LEVEL] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_3_PLASMA_1P_CLASS_LEVEL] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_4_PLASMA_1P_CLASS_LEVEL] = ALL_VALID;

		TC_look_up[TcAddress.SET_SU_1_PLASMA_1M_CLASS_LEVEL] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_2_PLASMA_1M_CLASS_LEVEL] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_3_PLASMA_1M_CLASS_LEVEL] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_4_PLASMA_1M_CLASS_LEVEL] = ALL_VALID;

		TC_look_up[TcAddress.SET_SU_1_PLASMA_2P_CLASS_LEVEL] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_2_PLASMA_2P_CLASS_LEVEL] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_3_PLASMA_2P_CLASS_LEVEL] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_4_PLASMA_2P_CLASS_LEVEL] = ALL_VALID;

		TC_look_up[TcAddress.SET_SU_1_PIEZO_1_CLASS_LEVEL]   = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_2_PIEZO_1_CLASS_LEVEL]   = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_3_PIEZO_1_CLASS_LEVEL]   = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_4_PIEZO_1_CLASS_LEVEL]   = ALL_VALID;

		TC_look_up[TcAddress.SET_SU_1_PIEZO_2_CLASS_LEVEL]   = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_2_PIEZO_2_CLASS_LEVEL]   = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_3_PIEZO_2_CLASS_LEVEL]   = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_4_PIEZO_2_CLASS_LEVEL]   = ALL_VALID;

		TC_look_up[TcAddress.SET_SU_1_PLASMA_1E_1I_MAX_TIME]  = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_2_PLASMA_1E_1I_MAX_TIME]  = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_3_PLASMA_1E_1I_MAX_TIME]  = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_4_PLASMA_1E_1I_MAX_TIME]  = ALL_VALID;

		TC_look_up[TcAddress.SET_SU_1_PLASMA_1E_PZT_MIN_TIME] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_2_PLASMA_1E_PZT_MIN_TIME] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_3_PLASMA_1E_PZT_MIN_TIME] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_4_PLASMA_1E_PZT_MIN_TIME] = ALL_VALID;

		TC_look_up[TcAddress.SET_SU_1_PLASMA_1E_PZT_MAX_TIME] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_2_PLASMA_1E_PZT_MAX_TIME] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_3_PLASMA_1E_PZT_MAX_TIME] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_4_PLASMA_1E_PZT_MAX_TIME] = ALL_VALID;

		TC_look_up[TcAddress.SET_SU_1_PLASMA_1I_PZT_MIN_TIME] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_2_PLASMA_1I_PZT_MIN_TIME] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_3_PLASMA_1I_PZT_MIN_TIME] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_4_PLASMA_1I_PZT_MIN_TIME] = ALL_VALID;

		TC_look_up[TcAddress.SET_SU_1_PLASMA_1I_PZT_MAX_TIME] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_2_PLASMA_1I_PZT_MAX_TIME] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_3_PLASMA_1I_PZT_MAX_TIME] = ALL_VALID;
		TC_look_up[TcAddress.SET_SU_4_PLASMA_1I_PZT_MAX_TIME] = ALL_VALID;

		TC_look_up[TcAddress.SET_COEFFICIENT_1]               = ALL_VALID;
		TC_look_up[TcAddress.SET_COEFFICIENT_2]               = ALL_VALID;
		TC_look_up[TcAddress.SET_COEFFICIENT_3]               = ALL_VALID;
		TC_look_up[TcAddress.SET_COEFFICIENT_4]               = ALL_VALID;
		TC_look_up[TcAddress.SET_COEFFICIENT_5]               = ALL_VALID;

	}


	/** Purpose        : Handles the TC interrupt
	 * Interface      : inputs  - TC MSB and LSB hardware registers
	 *                            TC_look_up table giving valid TC addresses
	 *                            and TC codes
	 *                  outputs - TM data registers for received TC and TC
	 *                            time tag
	 *                            TM data registers for error and mode
	 *                            status
	 *                            TM MSB and LSB hardware registers
	 *                            telemetry_pointer
	 *                            telemetry_end_pointer
	 *                            Telecommand Execution task mailbox
	 * Preconditions  : none
	 * Postconditions :
	 *                  Mail sent to Telecommand Execution task, if TC was
	 *                  valid
	 * Algorithm      : - Read TC address and code from hardware registers
	 *                  - Calculate parity
	 *                  - If parity not Ok
	 *                    - Set parity error
	 *                  - Else 
	 *                    - Clear parity error
	 *                    - Check TC address and code
	 *                    - If TC Ok
	 *                         Clear TC error and send mail
	 *                    - Else
	 *                         Set TC error
	 *                  - If TC is valid Send Status Register
	 *                    - Send first two registers defined by TC code
	 *                  - Else if TC is valid Send Science Data File
	 *                    - Send first teo bytes from Science Data File
	 *                  - Else if TC responce is enabled
	 *                    - Send Error Status and Mode Status
	 *                  - If TC is invalid
	 *                    - Disable TC responses
	 */
	public void tcInterruptService () // INTERRUPT(TC_ISR_SOURCE) USED_REG_BANK(2)
	{
		int /* unsigned char */ TC_address;
		int /* unsigned char */ TC_code;
		int /* uint16_t */   TC_word;
		/* Telecommand and it's parts */

		int /* unsigned char */ tmp_error_status;
		/* Temporary result of TC validity check */


		if (tctmDev.getTimerOverflowFlag() == 0)
		{
			/* TC is rejected. */

			telemetry_data.error_status |= TcTmDev.TC_ERROR;
			/* Set TC Error bit. */

			return;
			/* Abort ISR. */
		}

		if ((TC_state == TC_State.SC_TM_e) || (TC_state == TC_State.memory_dump_e))
		{
			return;
			/* Abort ISR. */
		}

		tctmDev.restartTcTimer();

		TC_address   = tctmDev.readTcMsb();
		TC_code      = tctmDev.readTcLsb();
		TC_word      = TC_address * 256 + TC_code;
		/* Get TC Address, TC Code and TC Word */

		if (TC_state == TC_State.memory_patch_e)
		{
			tcMailbox.sendISRMail((char)TC_word);
			return;
			/* This is not a normal telecommand, but word containing two bytes */
			/* of memory block to be written to data or code memory.           */
		}

		if (TC_state == TC_State.register_TM_e)
		{
			TC_state = TC_State.TC_handling_e;
			/* Register TM state is aborted */

			taskControl.resetInterruptMask(TcTmDev.TM_ISR_MASK);
			/* Disable TM interrupt mask. Note that DisableInterrupt */
			/* cannot be called from the C51 ISR.                    */
		}

		tmp_error_status = decode_TC(TC_address, TC_code, TC_word);

		TC_address >>= 1;

		if (tmp_error_status != 0)
		{
			/* TC is invalid. */

			telemetry_data.error_status |= tmp_error_status;
			tctmDev.writeTmMsb(telemetry_data.error_status);
			tctmDev.writeTmLsb(telemetry_data.mode_status);
			/* Send response to this TC to TM */

			return;
			/* Abort ISR because TC is invalid. */
		}


		handleTC(TC_address, TC_code, tmp_error_status);

	}
	private int decode_TC(int TC_address, int TC_code, int TC_word) {

		int tmp_error_status;
		int /* unsigned char */ par8, par4, par2, par1;

		tmp_error_status = 0;

		/* Parity calculation results */

		par8 = TC_address ^ TC_code;
		par4 = (par8 & 0x0F) ^ (par8 >> 4);
		par2 = (par4 & 0x03) ^ (par4 >> 2);
		par1 = (par2 & 0x01) ^ (par2 >> 1);
		/* Calculate parity */

		TC_address >>= 1;
		
		if (par1 != 0)
		{
			/* Parity error. */

			tmp_error_status |= TcTmDev.PARITY_ERROR;
		}

		else
		{

			switch (TC_look_up[TC_address])
			{
			case ALL_INVALID:
				/* Invalid TC Address */
				tmp_error_status |= TcTmDev.TC_ERROR;
				break;

			case ALL_VALID:
				/* All TC Codes are valid */
				tcMailbox.sendISRMail((char)TC_word);
				break;

			case ONLY_EQUAL:
				/* TC Code should be equal to TC Address */
				if (TC_address != TC_code)
				{
					tmp_error_status |= TcTmDev.TC_ERROR;
				}

				else
				{
					tcMailbox.sendISRMail((char)TC_word);
				}
				break;

			case ON_OFF_TC:
				/* TC_Code must have ON , OFF or SELF_TEST value */
				if ((TC_code != TcAddress.ON_VALUE) && (TC_code != TcAddress.OFF_VALUE) && 
						(TC_code != TcAddress.SELF_TEST))
				{
					tmp_error_status |= TcTmDev.TC_ERROR;
				}

				else
				{
					tcMailbox.sendISRMail((char)TC_word);
				}
				break;

			case ONLY_EVEN:
				/* TC_Code must be even and not too big */
				if (((TC_code & 1) != 0) || (TC_code > TcAddress.LAST_EVEN))
				{
					tmp_error_status |= TcTmDev.TC_ERROR;
				}

				else
				{
					tcMailbox.sendISRMail((char)TC_word);
				}
				break;  
			}
		}

		if (((TC_address != TcAddress.SEND_STATUS_REGISTER) 
				|| (tmp_error_status != 0)) 
				&& ((telemetry_data.error_status & TcTmDev.TC_OR_PARITY_ERROR) == 0))
		{
			/* Condition 1 :                                        */
			/* (TC is not SEND STATUS REGISTER or TC is invalid).   */
			/* and condition 2:                                     */
			/* no invalid telecommands are recorded                 */
			/*                                                      */
			/* First condition checks that the Command Status and   */
			/* Command Time Tag registers should be updated.        */
			/* Second condition checks that the update can be done. */


			telemetry_data.TC_word = (char) TC_word;
			telemetry_data.TC_time_tag.set(system.getInternalTime());
			/* Update TC registers in HK TM data area */
		}
		return tmp_error_status;
	}
	
	private void handleTC(int TC_address, int TC_code, int tmpErrorStatus) {
	
		if (TC_address == TcAddress.SEND_STATUS_REGISTER)
		{
			/* Send Status Register TC accepted */

			telemetry_data.TC_time_tag.set(system.getInternalTime());

			telemetry_object = telemetry_data;
			telemetry_index = TC_code;
			telemetry_end_index = TcAddress.LAST_EVEN + 1;
			/* First TM register to be sent is given in TC_Code */

			tctmDev.clearTmInterruptFlag();

			tctmDev.writeTmMsb  (telemetryPointerNext()); /* was: *telemetry_pointer */
			telemetry_index++;
			tctmDev.writeTmLsb  (telemetryPointerNext());
			telemetry_index++;

			TC_state = TC_State.register_TM_e;

			taskControl.setInterruptMask(TcTmDev.TM_ISR_MASK);
			/* Enable TM interrupt mask. Note that EnableInterrupt */
			/* cannot be called from the C51 ISR                   */

			if (telemetry_index > telemetry_end_index) {
				/* was: (EXTERNAL unsigned char *)&telemetry_data; */
				telemetry_object = telemetry_data;
				telemetry_index = 0;
			}
		}

		else if (TC_address == TcAddress.SEND_SCIENCE_DATA_FILE)
		{
			/* Send Science Data File TC accepted. */

			if (telemetry_data.getMode() == TelemetryData.DPU_SELF_TEST)
			{
				/* Wrong DEBIE mode. */

				telemetry_data.error_status |= TcTmDev.TC_ERROR;
				tctmDev.writeTmMsb (telemetry_data.error_status);
				tctmDev.writeTmLsb (telemetry_data.mode_status);
				/* Send response to this TC to TM. */
			}

			else  
			{
				telemetry_object = science_data;
				telemetry_index = 0; /* was: (EXTERNAL unsigned char *)&science_data; */
				telemetry_end_index = science_data.getEventByteOffset(free_slot_index) - 1;
				/* was:  ((EXTERNAL unsigned char *) &(science_data.event[free_slot_index])) - 1; */

				/* Science telemetry stops to the end of the last used event */
				/* record of the Science Data memory.                        */

				science_data.length = /* (unsigned short int) */ (char)
				((telemetry_end_index - telemetry_index + 1)/2);
				/* Store the current length of used science data. */  

				tctmDev.clearTmInterruptFlag();

				tctmDev.writeTmMsb (telemetryPointerNext());
				telemetry_index++;
				tctmDev.writeTmLsb (telemetryPointerNext());
				telemetry_index++;

				TC_state = TC_State.SC_TM_e;

				taskControl.setInterruptMask(TcTmDev.TM_ISR_MASK);
				/* Enable TM interrupt mask. Note that EnableInterrupt */
				/* cannot be called from a C51 ISR.                    */
			}
		}

		else if (TC_address == TcAddress.READ_DATA_MEMORY_LSB)
		{
			/* Read Data Memory LSB accepted. */
			if ((TC_state != TC_State.read_memory_e) ||
					((address_MSB << 8) + TC_code
							> (Dpu.END_SRAM3 - MEM_BUFFER_SIZE) + 1))
			{
				/* Wrong TC state or wrong address range. */
			
				telemetry_data.error_status |= TcTmDev.TC_ERROR;
				tctmDev.writeTmMsb  (telemetry_data.error_status);
				tctmDev.writeTmLsb  (telemetry_data.mode_status);
				/* Send response to this TC to TM. */
			
				TC_state = TC_State.TC_handling_e;
			}
			
			else
			{
				data_pointer.setBase((int)address_MSB * 256 + TC_code);
				telemetry_object = data_pointer;
				telemetry_index = 0;
				telemetry_end_index = MEM_BUFFER_SIZE;
				/* was originally:
					telemetry_pointer = DATA_POINTER((int)address_MSB * 256 + TC_code);
					telemetry_end_pointer = telemetry_pointer + MEM_BUFFER_SIZE;
				*/
				
				tctmDev.clearTmInterruptFlag();
				
				tctmDev.writeTmMsb (telemetry_data.error_status);
				tctmDev.writeTmLsb (telemetry_data.mode_status);
				/* First two bytes of Read Data Memory sequence. */
				
				read_memory_checksum = (char)(tmpErrorStatus ^ telemetry_data.mode_status);
				
				TC_state = TC_State.memory_dump_e;
				
				taskControl.setInterruptMask(TcTmDev.TM_ISR_MASK);
			}
		}

		else
		{
			/* Some other TC accepted. */

			tctmDev.writeTmMsb (telemetry_data.error_status);
			tctmDev.writeTmLsb (telemetry_data.mode_status);
			/* Send response to this TC to TM. */
		}
	}

	/** Purpose        : Returns the hit time of a given event.
	 * Interface      : inputs      - event_number (parameter)
	 *                                science_data[event_number].hit_time, hit
	 *                                time of the given event record.
	 *                  outputs     - return value, hit time.
	 *                  subroutines - none
	 * Preconditions  : none.
	 * Postconditions : none.
	 * Algorithm      :    -copy hit time of an event to a local variable hit
	 *                      time
	 *                     -return the value of hit time
	 */
	int /*dpu_time_t*/ GetElapsedTime(/*unsigned int*/int event_number)
	{
		/*dpu_time_t INDIRECT_INTERNAL*/ int hit_time;
		/* Hit time. */
		hit_time = science_data.event[event_number].getHitTime().toInt();
		// FIXME: is this the right way to port this to Java
		//COPY (hit_time, science_data.event[event_number].hit_time);

		return hit_time;
	}

	/** Purpose        : This function increments proper event counter and stores
	 *                  the new event record to the science data memory.
	 * Interface      : inputs      - free_slot_index, index of next free event
	 *                                   record in the Science Data memory.
	 *                                TC_state, state of the TC Execution task.
	 *                                event_queue_length, length of the event
	 *                                   record queue.
	 *                                event_queue, event record queue.
	 *                  outputs     - event_queue_length, as above.
	 *                                science_data.event, event records in
	 *                                Science Data memory.
	 *                                free_slot_index, as above.
	 *                  subroutines - FindMinQualityRecord
	 *                                IncrementCounters
	 * Preconditions  : none.
	 * Postconditions : If Science telemetry is not in progress, event data is
	 *                  stored in its proper place in the science data,
	 *                  otherwise event data is left in the queue and one record
	 *                  is reserved from the queue unless it is already full.
	 * Algorithm      : If there is room in the Science Data memory, the event
	 *                  data is tried to be stored there, otherwise the event
	 *                  with the lowest quality is searched and tried to be
	 *                  replaced. If the Science telemetry is in progress the
	 *                  event data is left in the queue and the length of the
	 *                  queue is incremented unless the queue is already full.
	 *                  If the Science telemetry is not in progress the event
	 *                  data is copied to the Science Data to the location
	 *                  defined earlier as described above.
	 */
	public void recordEvent() {
		
		/* uint_least16_t INDIRECT_INTERNAL */ int record_index;
		taskControl.disableInterruptMaster();

		record_index = free_slot_index;

		if (record_index >= max_events && TC_state != TC_State.SC_TM_e)
		{
			/* Science Data memory was full and Science TM was not in progress */

			taskControl.enableInterruptMaster();

			record_index = findMinQualityRecord();

			taskControl.disableInterruptMaster();
		}

		if (TC_state == TC_State.SC_TM_e)
		{
			/* Science Telemetry is in progress, so the event record */
			/* cannot be written to the Science Data memory. Instead */
			/* it is left to the temporary queue which will be       */
			/* copied to the Science Data memory after the Science   */
			/* telemetry is completed.                               */

			if (event_queue_length < MAX_QUEUE_LENGTH)
			{
				/* There is still room in the queue. */

				event_queue_length++;
				/* Prevent the event data from being overwritten. */
			}
			taskControl.enableInterruptMaster();
		}

		else
		{
			if (free_slot_index < max_events)
			{
				/* Science Data memory was not full */

				record_index = free_slot_index;
				science_data.event[record_index].setQualityNumber(0);
				free_slot_index++;
			}


			/* Increment event counters. */
			incrementCounters(
					event_queue[0].getSUNumber() - 1,
					event_queue[0].getClassification());

			taskControl.enableInterruptMaster();

			if (event_queue[0].getQualityNumber() >=
				science_data.event[record_index].getQualityNumber())

			{

				science_data.event[record_index].copyFrom(event_queue[0]);

				/* In this state the event data is located always to */
				/* the first element of the queue.                   */
			}
		}
	}   



	/** Purpose        : Finds event with lowest quality from Science Data memory.
	 * Interface      : inputs      - science_data.event, event records
	 *                  outputs     - return value, index of event record with
	 *                                   the lowest quality.
	 *                  subroutines - GetElapsedTime
	 * Preconditions  : none.
	 * Postconditions : none.
	 * Algorithm      : -Select first the first event record.
	 *                  -Loop from the second record to the last:
	 *                     -if the quality of the record is lower than the
	 *                      quality of the selected one, select the record.
	 *                     -else if the quality of the record equals the quality
	 *                      of selected one and it is older than the selected
	 *                      one, select the record.
	 *                  -End loop.
	 *                  -return the index of the selected record.
	 */
	private /*unsigned int*/ int findMinQualityRecord()  {
		
		/* unsigned int INDIRECT_INTERNAL */ int min_quality_number;
		/* The quality number of an event which has the lowest quality */
		/* number in the science data.                                 */

		/* unsigned int INDIRECT_INTERNAL */ int min_quality_location;
		/* The location of an event which has the lowest quality number */
		/* in the science data.                                         */

		/* dpu_time_t DIRECT_INTERNAL */ int min_time;
		/* Elapsed time of the oldest event. */

		/* dpu_time_t DIRECT_INTERNAL */ int time;
		/* Elapsed time as previously mentioned. */

		/* uint_least16_t DIRECT_INTERNAL */ int i;
		/* Loop variable. */


		min_time             = GetElapsedTime(0);
		min_quality_number   = science_data.event[0].getQualityNumber();
		min_quality_location = 0;
		/* First event is selected and compared against */
		/* the following events in the science_data.    */

		for (i=1; i < max_events; i++) // @WCA loop <= debie.target.HwIf.MAX_EVENTS - 1
		{
			time = GetElapsedTime(i);

			if(science_data.event[i].getQualityNumber() < min_quality_number)
			{
				min_time = time;
				min_quality_number = science_data.event[i].getQualityNumber();
				min_quality_location = i;
				/* If an event in the science_data has a lower quality number than  */
				/* any of the previous events, its quality_number and location is   */
				/* stored into variables.                                           */
			}

			else if(   (science_data.event[i].getQualityNumber() == min_quality_number)
					&& (time < min_time))
			{
				min_time = time;
				min_quality_location = i;
				/* If an event in the science_data has an equal quality number with */
				/* any of the previous events and it's older, event's               */
				/* quality_number and location are stored into variables.           */
			}
		}

		return min_quality_location;
	}


	/** Purpose        : Increments given event counters.
	 * Interface      : inputs      - sensor_unit (parameter)
	 *                                classification (parameter)
	 *                  outputs     - telemetry_data.SU_hits, counter of hits of
	 *                                   given Sensor Unit
	 *                                science_data.event_counter, counter of
	 *                                events with given classification and SU.
	 *                  subroutines - none
	 * Preconditions  : none.
	 * Postconditions : Given counters are incremented, if they had not their
	 *                  maximum values.
	 * Algorithm      : Increment given counters, if they are less than their
	 *                  maximum values. Calculate checksum for event counter.
	 *
	 * This function is used by Acquisition and TelecommandExecutionTask.
	 * However, it does not have to be of re-entrant type because collision
	 * is avoided through design, as follows.
	 * If Science Telemetry is in progress when Acquisition task is handling
	 * an event, the event record cannot be written to the Science Data
	 * memory. Instead it is left to the temporary queue which will be
	 * copied to the Science Data memory after the Science telemetry is
	 * completed. For the same reason  call for IncrementCounters is
	 * disabled.
	 * On the other hand, when Acquisition task is handling an event with
	 * RecordEvent all interrupts are disabled i.e. TelecommandExecutionTask
	 * cannot use IncrementCounters simultaniously.
	 */
	private void incrementCounters(
			/* sensor_index_t */ int  sensor_unit,
			/* unsigned char */  int  classification) {
		
		/* unsigned char EXTERNAL */ int counter;
		/* unsigned char EXTERNAL */ char new_checksum;


		if (telemetry_data.SU_hits[sensor_unit] < 0xFFFF)
		{
			telemetry_data.SU_hits[sensor_unit]++;
			/* SU hit counter is incremented. */
		}

		if (science_data.getEventCounter(sensor_unit,classification) < 0xFF)
		{

			counter = science_data.getEventCounter(sensor_unit,classification);

			new_checksum = 
				(char) (science_data.counter_checksum ^ counter);
			/* Delete effect of old counter value from the checksum. */

			counter++;

			new_checksum ^= counter;
			/* Add effect of new counter value to the checksum. */

			science_data.setEventCounter(sensor_unit,classification,(char) counter);
			/* The event counter is incremented. */

			science_data.counter_checksum = new_checksum;
			/* Event counter checksum is updated. */
		}

	}

	/** Clears the event counters and the quality numbers of                     
	 *  the event records in the science data memory
	 */
	public void clearEvents() {
		
		/* DIRECT_INTERNAL uint_least8_t */ int i;
		/* This variable is used in the for-loop which goes through  */
		/* the science data event counter.                           */

		/* Interrupts does not need to be disabled as long as  */
		/* Telecommand Execution task has higher priority than */
		/* Acquisition task.                                   */

		for(i=0;i<NUM_SU;i++)
		{
			telemetry_data.SU_hits[i] = 0;

			/* XXX: refactored to encapsulate multi-dim array access */
			science_data.resetEventCounters(i);
			/*event counters are cleared in science_data                           */
		}

		for (i=0; i < event_queue_length; i++) // @WCA loop <= 10
		{
			/* Events from the event queue are copied to the Science */
			/* Data memory.                                          */
			science_data.event[i].copyFrom(event_queue[i]);
			//	      STRUCT_ASSIGN (
			//	         science_data.event[i],
			//	         event_queue[i],
			//	         event_record_t);

			incrementCounters(
					event_queue[i].getSUNumber() - 1,
					event_queue[i].getClassification());

			/* One more event is stored in the Science Data memory. */
			/* NOTE that the event queue should always be smaller   */
			/* than the space reserved for event records in the     */
			/* Science Data memory.                                 */
		}

		free_slot_index    = event_queue_length;

		event_queue_length = 0;
		/* Empty the event queue. */

		science_data.counter_checksum = 0;
		science_data.not_used         = 0;
	}   

	/** Purpose        : Empty the event queue length.
	 * Interface      : inputs      - none
	 *                  outputs     - none
	 *                  subroutines - none
	 * Preconditions  : none.
	 * Postconditions : none.
	 * Algorithm      : - reset event queue length.
	 */
	public void resetEventQueueLength() {
		
		event_queue_length = 0;
	}

	/** get length of event queue */
	public int getEventQueueLength() {
		
		return event_queue_length;
	}

	/** check whether there is a free slot for events */
	public boolean hasFreeSlot() {
		
		return free_slot_index < max_events;
	}

	/** get free slot index (debugging/testing) */
	public int getFreeSlotIndex() {
		
		return free_slot_index;
	}

	/*--- ported from measure.c:543-EOF */
	/** Purpose        : Used when only the SU_state variable must be modified.
	 * Interface      : inputs      - SU_state
	 *                              - An Address of 'sensor_unit_t' type of a
	 *                                struct.
	 *                  outputs     - SU_state
	 *                              - SU_setting.execution_result
	 *                  subroutines - none
	 * Preconditions  : none
	 * Postconditions : SU_state variable is conditionally modified.
	 * Algorithm      :
	 *                  - If the expected SU_state variable value related to the
	 *                    given SU_index number is not valid, variable value is
	 *                    not changed. Error indication is recorded instead.
	 *                  - Else state variable value is changed and an indication
	 *                    of this is recorded.
	 */
	public void switchSensorUnitState(SensorUnit sensorUnit) {
		
		if (system.getAcquisitionTask().sensorUnitState[(sensorUnit.number) - SensorUnitDev.SU_1] != 
			sensorUnit.expected_source_state)
		{
			/* The original SU state is wrong. */

			sensorUnit.execution_result = SensorUnitDev.SU_STATE_TRANSITION_FAILED;
		}

		else if (sensorUnit.state == SensorUnitState.self_test_mon_e &&
				system.getAcquisitionTask().self_test_SU_number    != SensorUnitDev.NO_SU)
		{
			/* There is a self test sequence running already */

			sensorUnit.execution_result = SensorUnitDev.SU_STATE_TRANSITION_FAILED;
		}


		else
		{
			/* The original SU state is correct. */

			if (sensorUnit.state == SensorUnitState.self_test_mon_e)
			{
				system.getAcquisitionTask().self_test_SU_number = sensorUnit.number;
				/* Number of the SU under self test is recorded. */
			}

			else if (sensorUnit.number == system.getAcquisitionTask().self_test_SU_number)
			{
				system.getAcquisitionTask().self_test_SU_number = SensorUnitDev.NO_SU;
				/* Reset self test state i.e. no self test is running. */
			}

			system.getAcquisitionTask().sensorUnitState[(sensorUnit.number) - SensorUnitDev.SU_1] = sensorUnit.state;
			sensorUnit.execution_result  = SensorUnitDev.SU_STATE_TRANSITION_OK;
		}
	}

	/** Purpose        : Transition to SU state on.
	 * Interface      : inputs      - Sensor_index number
	 *                              - An Address of 'exec_result' variable
	 *                              - SU_state
	 *                  outputs     - SU_state
	 *                              - 'exec_result'
	 *                  subroutines - Switch_SU_On
	 * Preconditions  : none
	 * Postconditions : Under valid conditions transition to 'on' state is
	 *                  completed.
	 * Algorithm      :
	 *                  - If the original SU_state variable value related to the
	 *                    given SU_index number is not valid, variable value is
	 *                    not changed. Error indication is recorded instead.
	 *                  - Else
	 *                    - Disable interrups
	 *                    - 'Switch_SU_On' function is called and an
	 *                      indication of this transition is recorded.
	 *                    - Enable interrupts
	 */
	private void startSensorUnitSwitchingOn(int sensorUnitIndex, SensorUnit sensorUnit) {

	// Note: exec_result was a pointer to sensorUnit.execution_result
	//	void Start_SU_SwitchingOn(
	//		      sensor_index_t SU,
	//		      unsigned char EXTERNAL *exec_result) COMPACT_DATA REENTRANT_FUNC
		sensorUnit.execution_result = SensorUnitDev.SU_STATE_TRANSITION_OK;
		/* Default value, may be changed below. */ 

		if (system.getAcquisitionTask().sensorUnitState[sensorUnitIndex] != SensorUnitState.off_e)
		{
			/* The original SU state is wrong. */

			sensorUnit.execution_result = SensorUnitDev.SU_STATE_TRANSITION_FAILED;
		}

		else
		{
			/* The original SU state is correct. */
			taskControl.disableInterruptMaster();

			/* SU state is still off_e, because there is only one task  */
			/* which can switch SU state from off_e to any other state. */

			system.getSensorUnitDevice().switchSensorUnitOn(sensorUnitIndex + SensorUnitDev.SU_1, sensorUnit);
			// Moved from switchSensorUnitOn (unconditionally executed) to here
			
			telemetry_data.SU_status[sensorUnitIndex] |= SensorUnitDev.SU_ONOFF_MASK;
			/* SU_status register is updated to indicate that SU is switched on. */
			/* Other bits in this register are preserved.                        */

			if (sensorUnit.execution_result == sensorUnitIndex + SensorUnitDev.SU_1)
			{
				/* Transition succeeds. */

				system.getAcquisitionTask().sensorUnitState[sensorUnitIndex] = SensorUnitState.start_switching_e;
			}

			else
			{
				/* Transition fails. */

				sensorUnit.execution_result = SensorUnitDev.SU_STATE_TRANSITION_FAILED;
			}

			taskControl.enableInterruptMaster();
		}   
	}

	/**
	 * Holds parameters for "Switch_SU_State" operation
	 * Must be in external memory, because the parameter to the function
	 * is pointer to external memory
	 */
	// XXX: pulled out of sensorUnitOff, which breaks re-entrance, but avoids memory allocation
	private final SensorUnit sensorUnitSetting = new SensorUnit();

	/** Purpose        : Transition to SU state off.
	 * Interface      : inputs      - Sensor_index number
	 *                              - An Address of 'exec_result' variable
	 *                              - SU_state
	 *                  outputs     - SU_state
	 *                              - 'exec_result'
	 *                  subroutines - Switch_SU_Off
	 * Preconditions  : none
	 * Postconditions : Under valid conditions transition to 'off' state is
	 *                  completed.
	 * Algorithm      :
	 *                  - Disable interrups
	 *                  - 'Switch_SU_Off' function is called.
	 *                  - If transition succeeds,
	 *                    - 'Off' state is recorded to 'SU_state' variable.
	 *                    - Indication of transition is recorded to
	 *                      'exec_result'.
	 *                  - Else if transition fails,
	 *                    - Indication of this is recorded to 'exec_result'.
	 *                  - Enable interrupts
	 */
	public void setSensorUnitOff(int index, SensorUnit sensorUnit) {
	//
	//		void SetSensorUnitOff(
	//		         sensor_index_t SU,
	//		         unsigned char EXTERNAL *exec_result) COMPACT_DATA REENTRANT_FUNC
		taskControl.disableInterruptMaster();
		
		system.getSensorUnitDevice().switchSensorUnitOff(index + SensorUnitDev.SU_1, sensorUnit);
		telemetry_data.SU_status[index] &= (~SensorUnitDev.SU_ONOFF_MASK);

		/* SU_status register is updated to indicate that SU is switched off. */
		/* Other bits in this register are preserved.                         */

		if (sensorUnit.execution_result == index + SensorUnitDev.SU_1)
		{
			/* Transition succeeds. */

			sensorUnitSetting.number = index + SensorUnitDev.SU_1;
			sensorUnitSetting.expected_source_state = system.getAcquisitionTask().sensorUnitState[index]; 
			sensorUnitSetting.state = SensorUnitState.off_e;
			switchSensorUnitState(sensorUnitSetting);
			sensorUnit.execution_result = SensorUnitDev.SU_STATE_TRANSITION_OK;
		}

		else
		{
			/* Transition fails. */

			sensorUnit.execution_result = SensorUnitDev.SU_STATE_TRANSITION_FAILED;
		}

		taskControl.enableInterruptMaster();
	}

	//		SU_state_t  ReadSensorUnit(unsigned char SU_number) COMPACT_DATA REENTRANT_FUNC
	/** Purpose        :  To find out whether given Sensor Unit is switched on or
	 *                   off.
	 * Interface      :
	 * Preconditions  :  SU_Number should be 1,2,3 or 4.
	 * Postconditions :  Value of state variable is returned.
	 * Algorithm      :  Value of state variable (on_e or off_e) is returned.
	 */
	//		{
	//		   return SU_state[SU_number - 1];      
	//		}       


	/** Purpose        : Sensor unit state is updated.
	 * Interface      : inputs      - SU_state
	 *                              - SU_index number
	 *                  outputs     - SU_state
	 *                  subroutines - none
	 * Preconditions  : none
	 * Postconditions : SU_state variable is modified.
	 * Algorithm      : - Disable interrups
	 *                  - Change SU_state variable value related to the given
	 *                    SU_index number. Selection of the new state depends on
	 *                    the present one.
	 *                  - Enable interrups
	 */
	public void updateSensorUnitState(int idx) /* COMPACT_DATA REENTRANT_FUNC */ {
		
		SensorUnitState suState [] = system.getAcquisitionTask().sensorUnitState;
		
		taskControl.disableInterruptMaster();
		
		if (suState[idx] == SensorUnitState.start_switching_e)
		{
			suState[idx] = SensorUnitState.switching_e;
		}

		else if (suState[idx] == SensorUnitState.switching_e)
		{
			HwIf.resetPeakDetector(idx + SensorUnitDev.SU_1);
			/*Peak detector for this Sensor Unit is resetted. */       

			taskControl.waitTimeout(AcquisitionTask.PEAK_RESET_MIN_DELAY);

			HwIf.resetPeakDetector(idx + SensorUnitDev.SU_1);
			/*Peak detector for this Sensor Unit is resetted again. */   

			suState[idx] = SensorUnitState.on_e;
		}
		
		taskControl.enableInterruptMaster();
	}



	/** delegator */
	public int getErrorStatus() {
		
		return telemetry_data.getErrorStatus();
	}

	public Dpu.Time getInternalTime() {
		
		return system.getInternalTime();
	}

	public void setInternalTime(int raw) {
		
		system.getInternalTime().set(raw);
	}
	
	/** get telecommand state */
	public TC_State getTC_State() {
		
		return TC_state;
	}

	/** set telecommand state */
	public void setTC_State(TC_State state) {
		
		TC_state = state;
	}

	public int getMaxEvents() {
		
		return max_events;
	}

	private int telemetryPointerNext() {
		
		return telemetry_object.getByte(telemetry_index);
	}

	/** returns true if telemetry_pointer is at the end */
	public boolean telemetryIndexAtEnd() {
		
		return telemetry_index >= telemetry_end_index;
	}

	/** returns true if telemetry_pointer is exactly at the end */
	public boolean telemetryIndexAtEndExactly() {
		
		return telemetry_index == telemetry_end_index;
	}

	/** returns true if telemetry_pointer is exactly at given index */
	public boolean telemetryIndexEquals(int index) {
		
		return telemetry_index == index;
	}

}
