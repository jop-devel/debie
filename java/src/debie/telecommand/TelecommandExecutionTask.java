package debie.telecommand;

import debie.particles.EventRecord;
import debie.support.Dpu;
import debie.support.TaskControl;
import debie.support.TelemetryObject;
import debie.target.HwIf;
import debie.target.SensorUnit;
import debie.target.TcTm;

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

	/*--- Ported from tm_data.h:169-177 ---*/
	/* Science Data File : */

	/* XXX: Implementing binary serialization by hand is quite error prone.
	 * It would be nice if SCJ would have some capabilities in this respect
	 */
	public static class ScienceDataFile implements TelemetryObject {
		private char /*unsigned short int*/ length; /* byte: 0-1 */
		private char[][] /*unsigned char*/ event_counter = new char[SensorUnit.NUM_SU][NUM_CLASSES]; /* byte: 2-(2+NUM_SU*NUM_CLASSES-1) */
		private char /*unsigned char*/  not_used;         /* (2+NUM_SU*NUM_CLASSES) */
		private char /*unsigned char*/  counter_checksum; /* (3+NUM_SU*NUM_CLASSES) */
		private EventRecord[] event = new EventRecord[HwIf.MAX_EVENTS]; /* (4+NUM_SU*NUM_CLASSES)-(4+NUM_SU*NUM_CLASSES+MAX_EVENTS*26-1) */
		private static final int BYTE_INDEX_EVENT_RECORDS = 4 + (SensorUnit.NUM_SU * NUM_CLASSES);

		public int getByte(int index) {
			/* FIXME: just a stub */
			if(index == 0) return length & 0xff;
			else if(index == 1) return (length >>> 8);
			else return event_counter[0][0];
		}
		
		public int getEventCounter(int sensor_unit, int classification) {
			return event_counter[sensor_unit][classification];
		}

		public void setEventCounter(int sensor_unit, byte classification,
				char counter) {
			event_counter[sensor_unit][classification] = counter;
		}

		public void resetEventCounters(int i) {
			/* DIRECT_INTERNAL uint_least8_t */ int j;
			/* This variable is used in the for-loop which goes through  */
			/* the science data event counter.                           */

			for(j=0;j<NUM_CLASSES;j++)
			{
				event_counter[i][j] = 0;
			}
		}
		/** for byte-wise telemetry transmission, we need to know at which byte index
		 *  a certain event record starts. */
		public int getEventByteOffset(int free_slot_index) {
			return BYTE_INDEX_EVENT_RECORDS + (free_slot_index * EventRecord.sizeInBytes()); 
		}
	}
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

	private static TelemetryData telemetry_data = new TelemetryData();
	/* aggregates telemetry data */

	// XXX: this should probably be in external memory:
	//EXTERNAL science_data_file_t LOCATION(SCIENCE_DATA_START_ADDRESS)
	public static ScienceDataFile science_data = new ScienceDataFile();

	private static int /* uint_least16_t */ max_events;
	/* This variable is used to speed up certain    */
	/* Functional Test by adding the possibility    */
	/* to restrict the amount of events.            */
	/* It is initialised to value MAX_EVENTS at     */
	/* Boot.                                        */

	private TelemetryObject telemetry_object;
	
	private /* unsigned char* */ int telemetry_index;

	private /* unsigned char* */ int telemetry_end_index;

	private /* unsigned char */ char read_memory_checksum;
	/* Checksum to be sent at the end of Read Memory sequence. */

	private static EventRecord event_queue[] = new EventRecord[MAX_QUEUE_LENGTH];
	/* Holds event records before they are copied to the */
	/* Science Data memory. Normally there is only data  */
	/* from the new event whose data is beign collected, */
	/* but during Science Telemetry there can be stored  */
	/* several older events which are copied to the      */
	/* Science Data memory after telemetry ends.         */

	private static /* uint_least8_t */ int event_queue_length;
	/* Number of event records stored in the queue.    */
	/* These records are stored into event_queue table */
	/* in order starting from the first element.       */
	/* Initialised to zero on power-up.                */

	private static /* uint_least16_t */ int free_slot_index;
	/* Index to the first free record in the Science         */
	/* Data memory, or if it is full equals to 'max_events'. */
	/* Initialised to zero on power-up.                      */

	/* Holds event records before they are copied to the */
	/* Science Data memory. Normally there is only data  */
	/* from the new event whose data is beign collected, */
	/* but during Science Telemetry there can be stored  */
	/* several older events which are copied to the      */
	/* Science Data memory after telemetry ends.         */


	/*--- Ported from tc_hand.c:78-89 */
	/* Type definitions */

	public static class TeleCommand {
		public TeleCommand(int word, int address, int code) {
			this.TC_word = (char) word;
			this.TC_address = (char) address;
			this.TC_code = (char) code;
		}
		private /*uint16_t*/ char TC_word;          /* Received telecommand word */
		private /*unsigned char*/ char TC_address;  /* Telecommand address       */
		private /*unsigned char*/ char TC_code;     /* Telecommand code          */
	}

	public enum MemoryType { Code, Data };

	/*--- Ported from tc_hand.c:90-123 */
	/* Global variables */

	private static TeleCommand previous_TC;
	/* Holds previous telecommand unless a timeout has occurred */

	private static /*unsigned char */ char TC_timeout = 0;
	/* Time out for next telecommand, zero means no timeout */


	private static /*unsigned char[] */ byte[] TC_look_up = new byte[128];
	/* Look-up table for all possible 128 TC address values (domain: ALL_INVALID(0) - ONLY_EVEN(4) ) */

	private static TC_State TC_state = TC_State.TC_handling_e; /* 0 ~ TC_handling_e */
	/* internal state of the Telecommand Execution task */

	private static  MemoryType memory_type;
	/* Selection of memory write target (code/data) */ 

	private static /* unsigned char[] */  byte[] memory_transfer_buffer = new byte[MEM_BUFFER_SIZE];
	/* Buffer for memory read and write telecommands */

	private static /*unsigned char */ int address_MSB;
	/* MSB of memory read source and write destination addresses */

	private static /* unsigned char */ int address_LSB;
	/* LSB of memory read source and write destination addresses. */

	private static /* uint_least8_t */ int memory_buffer_index = 0;
	/* Index to memory_buffer. */

	private static /* unsigned char */ char write_checksum;
	/* Checksum for memory write blocks. */


	public static TelemetryData getTelemetryData() {
		return telemetry_data;
	}
	
	/*--- References to other parts of the system ---*/
	
	/* link to tctm hardware */
	private TcTm tctmDev;
	private Dpu.Time internal_time;

	/*--- Constructor ---*/

	/* Purpose        : Initialize the global state of Telecommand Execution     */
	/* Interface      : inputs      - none                                       */
	/*                  outputs     - TC state                                   */
	/*                  subroutines - DisableInterrupt                           */
	/*                                EnableInterrupt                            */
	/* Preconditions  : none                                                     */
	/* Postconditions : TelecommandExecutionTask is operational.                 */
	/* Algorithm      : - initialize task variables.                             */
	public TelecommandExecutionTask(TcTm tctmDev, Dpu.Time timeRef) {
		this.tctmDev = tctmDev;
		this.internal_time = timeRef;

		initTcLookup();

		TC_state            = TC_State.TC_handling_e;

		// XXX: mailbox is initialized elsewhere
		//		TC_mail.mailbox_number = TCTM_MAILBOX;
		//		TC_mail.message        = &(received_command.TC_word);

		/* Parameters for mail waiting function.   */
		/* Time-out set separately.                */

		previous_TC = new TeleCommand(0, TcAddress.UNUSED_TC_ADDRESS, 0);

		// FIXME: TODO
		// disableInterrupt(TM_ISR_SOURCE);
		// enableInterrupt(TC_ISR_SOURCE);
	}

	/*--- [2] Definitions from   telem.c: 74 ---*/

	public static EventRecord getFreeRecord()

	/* Purpose        : Returns pointer to free event record in event queue.     */
	/* Interface      : inputs      - event_queue_length, legnth of the event    */
	/*                                queue.                                     */
	/*                  outputs     - return value, pointer to the free record.  */
	/*                  subroutines - none                                       */
	/* Preconditions  : none.                                                    */
	/* Postconditions : none.                                                    */
	/* Algorithm      : -If the queue is not full                                */
	/*                     -return pointer to the next free record               */
	/*                  -else                                                    */
	/*                     -return pointer to the last record                    */

	{
		if (event_queue_length < MAX_QUEUE_LENGTH)
		{
			return event_queue[event_queue_length];
		}

		else
		{
			return event_queue[MAX_QUEUE_LENGTH - 1];
		}
	}

	public void handleTelecommand ()

	/* Purpose        : Waits for and handles one Telecommand from the TC ISR    */
	/* Interface      : inputs      - Telecommand Execution task mailbox         */
	/*                                TC state                                   */
	/*                  outputs     - TC state                                   */
	/*                  subroutines - ReadMemory                                 */
	/*                                WriteMemory                                */
	/*                                ExecuteCommand                             */
	/* Preconditions  : none                                                     */
	/* Postconditions : one message removed from the TC mailbox                  */
	/* Algorithm      : - wait for mail to Telecommand Execution task mailbox    */
	/*                  - if mail is "TM_READY" and TC state is either           */
	/*                    "SC_TM_e" or "memory_dump_e".                          */
	/*                    - if TC state is "SC_TM_e"                             */
	/*                       - call ClearEvents function                         */
	/*                    - set TC state to TC handling                          */
	/*                  - else switch TC state                                   */
	/*                    - case ReadMemory_e  : Check received TC's address     */
	/*                    - case WriteMemory_e : call WriteMemory function       */
	/*                    - case MemoryPatch_e : call MemoryPatch function       */
	/*                    - case TC_Handling : call ExecuteCommand               */
	/*                NOTE:   case register_TM_e is left out because             */
	/*                        operation of SEND_STATUS_REGISTER TC does not      */
	/*                        require any functionalites of this task.           */  

	{
		//	   TC_mail.timeout = TC_timeout;
		//
		//	   WaitMail(&TC_mail);
		//
		//	   TC_timeout = 0;
		//	   /* Default value */
		//
		//	   if (TC_mail.execution_result == TIMEOUT_OCCURRED)
		//	   {
		//	      previous_TC.TC_word    = 0;
		//	      previous_TC.TC_address = UNUSED_TC_ADDRESS;
		//	      previous_TC.TC_code    = 0;
		//	      /* Forget previous telecommand. */
		//
		//	      if (TC_state != TC_handling_e)
		//	      {
		//	         /* Memory R/W time-out. */
		//	         Set_TC_Error();
		//	      }
		//
		//	      TC_state = TC_handling_e; 
		//	   }
		//
		//	   else if (TC_mail.execution_result == MSG_RECEIVED)
		//	   {
		//	      received_command.TC_address = TC_ADDRESS (received_command.TC_word);
		//	      received_command.TC_code    = TC_CODE    (received_command.TC_word);
		//
		//	      if (((TC_state == SC_TM_e) || (TC_state == memory_dump_e)) &&
		//	          (received_command.TC_word == TM_READY))
		//
		//	      /* Note that in order to this condition to be sufficient, only */
		//	      /* TM interrupt service should be allowed to send mail to this */
		//	      /* task in the TC states mentioned.                            */
		//
		//	      {
		//	         DisableInterrupt(TM_ISR_SOURCE);
		//
		//	         if (TC_state == SC_TM_e)
		//	         {
		//	           ClearEvents();
		//	         }
		//
		//	         TC_state = TC_handling_e;
		//	      }
		//	      else
		//	      {
		//	         switch (TC_state)
		//	         {
		//
		//	            case read_memory_e:
		//
		//	               if (received_command.TC_address != READ_DATA_MEMORY_LSB)
		//	               {
		//	                  Set_TC_Error();
		//	                  TC_state = TC_handling_e;
		//	               }
		//
		//	               break;
		//
		//	            case write_memory_e:
		//	               WriteMemory (&received_command);
		//	               break;
		//
		//	            case memory_patch_e:
		//	               MemoryPatch (&received_command);
		//	               break;
		//
		//	            case TC_handling_e:
		//	               ExecuteCommand (&received_command);
		//	               break;
		//
		//	         }
		//
		//	      }
		//
		//	      STRUCT_ASSIGN (previous_TC, received_command, telecommand_t);
		//	   }
		//
		//	   else
		//	   {
		//	      /* Nothing is done if WaitMail returns an error message. */
		//	   }
	}

	public void tmInterruptService () // INTERRUPT(TM_ISR_SOURCE) USED_REG_BANK(2)
	/* Purpose        : This function handles the TM interrupts.                 */
	/* Interface      : inputs  - telemetry_pointer                              */
	/*                            telemetry_end_pointer                          */
	/*                            TC_state                                       */
	/*                            telemetry_data                                 */
	/*                  outputs - telemetry_pointer                              */
	/*                            TM HW reigsiters                               */
	/*                            Telemcommand Execution task mailbox            */
	/* Preconditions  : telemetry_pointer and telemetry_end_pointer have valid   */
	/*                  values (TM interrupts should be enabled only when this   */
	/*                  condition is true)                                       */
	/* Postconditions : Next two bytes are written to TM HW registers and if they*/
	/*                  were the last bytes to be written, a "TM_READY" mail is  */
	/*                  sent to the Telecommand Execution task                   */
	/* Algorithm      : - if telemetry_pointer < telemetry_end_pointer           */
	/*                     - write next two bytes from location pointed by       */
	/*                       telemetry_pointer and increase it by two            */
	/*                  - else if TC_state == register_TM_e                      */
	/*                     - write first two TM data registers and set           */
	/*                       telemetry_pointer to point to the third TM data     */
	/*                       register                                            */
	/*                  - else                                                   */
	/*                     - send TM_READY message to Telecommand Execution task */
	/*                       mailbox                                             */

	{
		//	   unsigned char EXTERNAL tm_byte;
		//
		//	   CLEAR_TM_INTERRUPT_FLAG;
		//	   /*The interrupt flag is put down by setting high bit 3 'INT1' in port 3.  */
		//
		//	   if (telemetry_pointer == (unsigned char *) &telemetry_data.time)
		//	   {
		//	      COPY (telemetry_data.time, internal_time);
		//	   }
		//
		//	   if (telemetry_pointer < telemetry_end_pointer)
		//	   {
		//	      /* There are bytes left to be sent to TM. */
		//
		//	      tm_byte = *telemetry_pointer;
		//	      WRITE_TM_MSB (tm_byte);
		//	      read_memory_checksum ^= tm_byte;
		//
		//	      telemetry_pointer++;
		//
		//	      tm_byte = *telemetry_pointer;
		//	      WRITE_TM_LSB (tm_byte);
		//	      read_memory_checksum ^= tm_byte;
		//
		//	      telemetry_pointer++;
		//	   }
		//	   else if (TC_state == register_TM_e)
		//	   /* Start to send TM data registers starting from the first ones */
		//	   {
		//	      telemetry_pointer = (EXTERNAL unsigned char *)&telemetry_data;
		//	      WRITE_TM_MSB (*telemetry_pointer);
		//	      telemetry_pointer++;
		//	      WRITE_TM_LSB (*telemetry_pointer);
		//	      telemetry_pointer++;  
		//	   }
		//	   else if (TC_state == memory_dump_e)
		//	   {
		//	      WRITE_TM_MSB(0);
		//	      WRITE_TM_LSB(read_memory_checksum);
		//	      /* Last two bytes of Read Memory sequence. */
		//
		//	      Send_ISR_Mail(TCTM_MAILBOX, TM_READY);
		//	   }
		//	   else
		//	   /* It is time to stop sending telemetry */
		//	   {
		//	      Send_ISR_Mail (TCTM_MAILBOX, TM_READY);
		//	   }
	}

	void initTcLookup()
	/* Purpose        : Initializes the TC look-up table                     */
	/* Interface      : inputs  - none                                       */
	/*                  outputs - TC_lool_up                                 */
	/* Preconditions  : none                                                 */
	/* Postconditions : TC_look_up is initialized                            */
	/* Algorithm      : - set all elements in table to ALL_INVALID           */
	/*                  - set each element corresponding valid TC address    */
	/*                    to proper value                                    */
	{
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


	public void tcInterruptService () // INTERRUPT(TC_ISR_SOURCE) USED_REG_BANK(2)
	/* Purpose        : Handles the TC interrupt                             */
	/* Interface      : inputs  - TC MSB and LSB hardware registers          */
	/*                            TC_look_up table giving valid TC addresses */
	/*                            and TC codes                               */
	/*                  outputs - TM data registers for received TC and TC   */
	/*                            time tag                                   */
	/*                            TM data registers for error and mode       */
	/*                            status                                     */
	/*                            TM MSB and LSB hardware registers          */
	/*                            telemetry_pointer                          */
	/*                            telemetry_end_pointer                      */
	/*                            Telecommand Execution task mailbox         */
	/* Preconditions  : none                                                 */
	/* Postconditions :                                                      */
	/*                  Mail sent to Telecommand Execution task, if TC was   */
	/*                  valid                                                */
	/* Algorithm      : - Read TC address and code from hardware registers   */
	/*                  - Calculate parity                                   */
	/*                  - If parity not Ok                                   */
	/*                    - Set parity error                                 */
	/*                  - Else                                               */ 
	/*                    - Clear parity error                               */
	/*                    - Check TC address and code                        */
	/*                    - If TC Ok                                         */
	/*                         Clear TC error and send mail                  */
	/*                    - Else                                             */
	/*                         Set TC error                                  */
	/*                  - If TC is valid Send Status Register                */
	/*                    - Send first two registers defined by TC code      */
	/*                  - Else if TC is valid Send Science Data File         */
	/*                    - Send first teo bytes from Science Data File      */
	/*                  - Else if TC responce is enabled                     */
	/*                    - Send Error Status and Mode Status                */
	/*                  - If TC is invalid                                   */
	/*                    - Disable TC responses                             */

	{
		int /* unsigned char */ TC_address;
		int /* unsigned char */ TC_code;
		int /* uint16_t */   TC_word;
		/* Telecommand and it's parts */

		int /* unsigned char */ par8, par4, par2, par1;
		/* Parity calculation results */

		int /* unsigned char */ tmp_error_status;
		/* Temporary result of TC validity check */


		if (tctmDev.getTimerOverflowFlag() == 0)
		{
			/* TC is rejected. */

			telemetry_data.error_status |= TcTm.TC_ERROR;
			/* Set TC Error bit. */

			return;
			/* Abort ISR. */
		}

		if ((TC_state == TC_State.SC_TM_e) || (TC_state == TC_State.memory_dump_e))
		{
			return;
			/* Abort ISR. */
		}

		tctmDev.stopTcTimer();
		tctmDev.initTcTimerMsb();
		tctmDev.initTcTimerLsb();
		tctmDev.clearTimerOverflowFlag();
		tctmDev.startTcTimer();

		TC_address   = tctmDev.readTcMsb();
		TC_code      = tctmDev.readTcLsb();
		TC_word      = TC_address * 256 + TC_code;
		/* Get TC Address, TC Code and TC Word */

		if (TC_state == TC_State.memory_patch_e)
		{
			sendISRMail(0, TC_word);
			return;
			/* This is not a normal telecommand, but word containing two bytes */
			/* of memory block to be written to data or code memory.           */
		}

		if (TC_state == TC_State.register_TM_e)
		{
			TC_state = TC_State.TC_handling_e;
			/* Register TM state is aborted */

			resetInterruptMask(TcTm.TM_ISR_MASK);
			/* Disable TM interrupt mask. Note that DisableInterrupt */
			/* cannot be called from the C51 ISR.                    */
		}


		par8 = TC_address ^ TC_code;
		par4 = (par8 & 0x0F) ^ (par8 >> 4);
		par2 = (par4 & 0x03) ^ (par4 >> 2);
		par1 = (par2 & 0x01) ^ (par2 >> 1);
		/* Calculate parity */

		TC_address >>= 1;

		tmp_error_status = 0;

		if (par1 != 0)
		{
			/* Parity error. */

			tmp_error_status |= TcTm.PARITY_ERROR;
		}

		else
		{

			switch (TC_look_up[TC_address])
			{
			case ALL_INVALID:
				/* Invalid TC Address */
				tmp_error_status |= TcTm.TC_ERROR;
				break;

			case ALL_VALID:
				/* All TC Codes are valid */
				sendISRMail(0, TC_word);
				break;

			case ONLY_EQUAL:
				/* TC Code should be equal to TC Address */
				if (TC_address != TC_code)
				{
					tmp_error_status |= TcTm.TC_ERROR;
				}

				else
				{
					sendISRMail(0, TC_word);
				}
				break;

			case ON_OFF_TC:
				/* TC_Code must have ON , OFF or SELF_TEST value */
				if ((TC_code != TcAddress.ON_VALUE) && (TC_code != TcAddress.OFF_VALUE) && 
						(TC_code != TcAddress.SELF_TEST))
				{
					tmp_error_status |= TcTm.TC_ERROR;
				}

				else
				{
					sendISRMail(0, TC_word);
				}
				break;

			case ONLY_EVEN:
				/* TC_Code must be even and not too big */
				if (((TC_code & 1) != 0) || (TC_code > TcAddress.LAST_EVEN))
				{
					tmp_error_status |= TcTm.TC_ERROR;
				}

				else
				{
					sendISRMail(0, TC_word);
				}
				break;  
			}
		}

		if (((TC_address != TcAddress.SEND_STATUS_REGISTER) 
				|| (tmp_error_status != 0)) 
				&& ((telemetry_data.error_status & TcTm.TC_OR_PARITY_ERROR) == 0))
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
			telemetry_data.TC_time_tag = internal_time.getTag();
			/* Update TC registers in HK TM data area */
		}

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


		if (TC_address == TcAddress.SEND_STATUS_REGISTER)
		{
			/* Send Status Register TC accepted */

			telemetry_data.TC_time_tag = internal_time.getTag();

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

			setInterruptMask(TcTm.TM_ISR_MASK);
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

				telemetry_data.error_status |= TcTm.TC_ERROR;
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

				setInterruptMask(TcTm.TM_ISR_MASK);
				/* Enable TM interrupt mask. Note that EnableInterrupt */
				/* cannot be called from a C51 ISR.                    */
			}
		}

		/* FIXME: this is tricky (again) to implement in Java */
		else if (TC_address == TcAddress.READ_DATA_MEMORY_LSB)
		{
			/* Read Data Memory LSB accepted. */
//			if ( (TC_state != read_memory_e) ||
//					( ((unsigned int)address_MSB << 8) + TC_code
//							> (END_SRAM3 - MEM_BUFFER_SIZE) + 1 ) )
//			{
//				/* Wrong TC state or wrong address range. */
//
//				telemetry_data.error_status |= TcTm.TC_ERROR;
//				tctmDev.writeTmMsb  (telemetry_data.error_status);
//				tctmDev.writeTmLsb  (telemetry_data.mode_status);
//				/* Send response to this TC to TM. */
//
//				TC_state = TC_state.TC_handling_e;
//			}
//
//			else
//			{
//				telemetry_pointer = 
//					DATA_POINTER((int)address_MSB * 256 + TC_code);
//				telemetry_end_pointer = telemetry_pointer + MEM_BUFFER_SIZE;
//
//				tctmDev.clearTmInterruptFlag();
//
//				tctmDev.writeTmMsb (telemetry_data.error_status);
//				tctmDev.writeTmLsb (telemetry_data.mode_status);
//				/* First two bytes of Read Data Memory sequence. */
//
//				read_memory_checksum = tmp_error_status ^ telemetry_data.mode_status;
//
//				TC_state = TC_State.memory_dump_e;
//
//				SetInterruptMask(TM_ISR_MASK);
//			}
		}

		else
		{
			/* Some other TC accepted. */

			tctmDev.writeTmMsb (telemetry_data.error_status);
			tctmDev.writeTmLsb (telemetry_data.mode_status);
			/* Send response to this TC to TM. */
		}

	}
	/** FIXME: unimplemented stubs */	
	private void setInterruptMask(int tmIsrMask) {
		// TODO Auto-generated method stub
		
	}
	private void resetInterruptMask(int tmIsrMask) {
		// TODO Auto-generated method stub
		
	}
	private void sendISRMail(int mailbox, int message) {
		if(TaskControl.isrSendMessage(mailbox, message) == TaskControl.NOT_OK) {
		      telemetry_data.isr_send_message_error = (byte) mailbox;
		}
	}

	/*dpu_time_t*/ static int GetElapsedTime(/*unsigned int*/int event_number)
	/* Purpose        : Returns the hit time of a given event.                   */
	/* Interface      : inputs      - event_number (parameter)                   */
	/*                                science_data[event_number].hit_time, hit   */
	/*                                time of the given event record.            */
	/*                  outputs     - return value, hit time.                    */
	/*                  subroutines - none                                       */
	/* Preconditions  : none.                                                    */
	/* Postconditions : none.                                                    */
	/* Algorithm      :    -copy hit time of an event to a local variable hit    */
	/*                      time                                                 */
	/*                     -return the value of hit time                         */
	{
		/*dpu_time_t INDIRECT_INTERNAL*/ int hit_time;
		/* Hit time. */
		hit_time = science_data.event[event_number].getHitTime();
		//COPY (hit_time, science_data.event[event_number].hit_time);

		return hit_time;
	}

	public static void recordEvent()
	/* Purpose        : This function increments proper event counter and stores */
	/*                  the new event record to the science data memory.         */
	/* Interface      : inputs      - free_slot_index, index of next free event  */
	/*                                   record in the Science Data memory.      */
	/*                                TC_state, state of the TC Execution task.  */
	/*                                event_queue_length, length of the event    */
	/*                                   record queue.                           */
	/*                                event_queue, event record queue.           */
	/*                  outputs     - event_queue_length, as above.              */
	/*                                science_data.event, event records in       */
	/*                                Science Data memory.                       */
	/*                                free_slot_index, as above.                 */
	/*                  subroutines - FindMinQualityRecord                       */
	/*                                IncrementCounters                          */
	/* Preconditions  : none.                                                    */
	/* Postconditions : If Science telemetry is not in progress, event data is   */
	/*                  stored in its proper place in the science data,          */
	/*                  otherwise event data is left in the queue and one record */
	/*                  is reserved from the queue unless it is already full.    */
	/* Algorithm      : If there is room in the Science Data memory, the event   */
	/*                  data is tried to be stored there, otherwise the event    */
	/*                  with the lowest quality is searched and tried to be      */
	/*                  replaced. If the Science telemetry is in progress the    */
	/*                  event data is left in the queue and the length of the    */
	/*                  queue is incremented unless the queue is already full.   */
	/*                  If the Science telemetry is not in progress the event    */
	/*                  data is copied to the Science Data to the location       */
	/*                  defined earlier as described above.                      */
	// FIXME: At the moment, not thread safe (commented (DIS|EN)ABLE_INTERRUPT_MASTER)
	{
		/* uint_least16_t INDIRECT_INTERNAL */ int record_index;

		HwIf.disableInterruptMaster();

		record_index = free_slot_index;

		if (record_index >= max_events && TC_state != TC_State.SC_TM_e)
		{
			/* Science Data memory was full and Science TM was not in progress */

			HwIf.enableInterruptMaster();

			record_index = findMinQualityRecord();

			HwIf.disableInterruptMaster();
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
			// ENABLE_INTERRUPT_MASTER;
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

			// ENABLE_INTERRUPT_MASTER;

			if (event_queue[0].getQualityNumber() >=
				science_data.event[record_index].getQualityNumber())

			{

				science_data.event[record_index].copyFrom(event_queue[0]);

				/* In this state the event data is located always to */
				/* the first element of the queue.                   */
			}
		}
	}   


	private static /*unsigned int*/ int findMinQualityRecord()

	/* Purpose        : Finds event with lowest quality from Science Data memory.*/
	/* Interface      : inputs      - science_data.event, event records          */
	/*                  outputs     - return value, index of event record with   */
	/*                                   the lowest quality.                     */
	/*                  subroutines - GetElapsedTime                             */
	/* Preconditions  : none.                                                    */
	/* Postconditions : none.                                                    */
	/* Algorithm      : -Select first the first event record.                    */
	/*                  -Loop from the second record to the last:                */
	/*                     -if the quality of the record is lower than the       */
	/*                      quality of the selected one, select the record.      */
	/*                     -else if the quality of the record equals the quality */
	/*                      of selected one and it is older than the selected    */
	/*                      one, select the record.                              */
	/*                  -End loop.                                               */
	/*                  -return the index of the selected record.                */

	{
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

		for (i=1; i < max_events; i++)
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


	private static void incrementCounters(
			/* sensor_index_t */ int  sensor_unit,
			/* unsigned char */  byte classification)

	/* Purpose        : Increments given event counters.                         */
	/* Interface      : inputs      - sensor_unit (parameter)                    */
	/*                                classification (parameter)                 */
	/*                  outputs     - telemetry_data.SU_hits, counter of hits of */
	/*                                   given Sensor Unit                       */
	/*                                science_data.event_counter, counter of     */
	/*                                events with given classification and SU.   */
	/*                  subroutines - none                                       */
	/* Preconditions  : none.                                                    */
	/* Postconditions : Given counters are incremented, if they had not their    */
	/*                  maximum values.                                          */
	/* Algorithm      : Increment given counters, if they are less than their    */
	/*                  maximum values. Calculate checksum for event counter.    */
	/*                                                                           */
	/* This function is used by Acquisition and TelecommandExecutionTask.        */
	/* However, it does not have to be of re-entrant type because collision      */
	/* is avoided through design, as follows.                                    */
	/* If Science Telemetry is in progress when Acquisition task is handling     */
	/* an event, the event record cannot be written to the Science Data          */
	/* memory. Instead it is left to the temporary queue which will be           */
	/* copied to the Science Data memory after the Science telemetry is          */
	/* completed. For the same reason  call for IncrementCounters is             */
	/* disabled.                                                                 */
	/* On the other hand, when Acquisition task is handling an event with        */
	/* RecordEvent all interrupts are disabled i.e. TelecommandExecutionTask     */
	/* cannot use IncrementCounters simultaniously.                              */



	{
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

	void clearEvents()
	/* Clears the event counters and the quality numbers of                     */
	/* the event records in the science data memory                              */

	{
		/* DIRECT_INTERNAL uint_least8_t */ int i;
		/* This variable is used in the for-loop which goes through  */
		/* the science data event counter.                           */

		/* Interrupts does not need to be disabled as long as  */
		/* Telecommand Execution task has higher priority than */
		/* Acquisition task.                                   */

		for(i=0;i<SensorUnit.NUM_SU;i++)
		{
			telemetry_data.SU_hits[i] = 0;

			/* XXX: refactored to encapsulate multi-dim array access */
			science_data.resetEventCounters(i);
			/*event counters are cleared in science_data                           */
		}

		for (i=0; i < event_queue_length; i++)
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

	void resetEventQueueLength()
	/* Purpose        : Empty the event queue length.                            */
	/* Interface      : inputs      - none                                       */
	/*                  outputs     - none                                       */
	/*                  subroutines - none                                       */
	/* Preconditions  : none.                                                    */
	/* Postconditions : none.                                                    */
	/* Algorithm      : - reset event queue length.                              */
	{
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


	/** delegator */
	public int getErrorStatus() {
		return telemetry_data.getErrorStatus();
	}

	/** get telecommand state */
	public TC_State getTC_State() {
		return this.TC_state;
	}

	public int getMaxEvents() {
		return max_events;
	}

	private int telemetryPointerNext() {
		return this.telemetry_object.getByte(this.telemetry_index);
	}

	/** returns true if telemetry_pointer is not at the end */
	public boolean telemetryIndexAtEnd() {
		return telemetry_index >= telemetry_end_index;
	}

}
