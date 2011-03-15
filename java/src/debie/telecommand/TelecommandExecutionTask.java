package debie.telecommand;

import debie.target.HwIf;
import debie.target.SensorUnit;

public class TelecommandExecutionTask {
	
	/*--- [1] Definitions from tm_data.h:33 ---*/
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

	/*--- [1]            until tm_data.h:83 ---*/
	/*--- [2] Ported from tm_data.h:169-177 ---*/
	/* Science Data File : */

	public static class ScienceDataFile {
	   /*unsigned short int*/ char length;
	   /*unsigned char*/ char[][]  event_counter = new char[SensorUnit.NUM_SU][NUM_CLASSES];
	   /*unsigned char*/ char  not_used;
	   /*unsigned char*/ char  counter_checksum;
	   EventRecord[] event = new EventRecord[HwIf.MAX_EVENTS];

	   public int getEventCounter(int sensor_unit, int classification) {
		   if(classification >= NUM_CLASSES) throw new RuntimeException("Bad classification value");

		   return event_counter[sensor_unit][classification];
	   }

	   /* XXX: encapsulating multi-dim array access */
	   public void setEventCounter(int sensor_unit, byte classification,
			   char counter) {
		   if(classification >= NUM_CLASSES) throw new RuntimeException("Bad classification value");
		   
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
	}
	
	/*--- [2] Definitions from   telem.c:36 ---*/
	
	private static TelemetryData telemetry_data = new TelemetryData();
	/* aggregates telemetry data */

	// FIXME: this should be on in external memory (I suppose)
	//EXTERNAL science_data_file_t LOCATION(SCIENCE_DATA_START_ADDRESS)
	public static ScienceDataFile science_data = new ScienceDataFile();

	private static int /* uint_least16_t */ max_events;
	/* This variable is used to speed up certain    */
	/* Functional Test by adding the possibility    */
	/* to restrict the amount of events.            */
	/* It is initialised to value MAX_EVENTS at     */
	/* Boot.                                        */

	// FIXME: missing
	// unsigned char EXTERNAL *telemetry_pointer;

	// FIXME: missing
	// unsigned char EXTERNAL *telemetry_end_pointer;

	private static /* unsigned char */ char read_memory_checksum;
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

	/*--- [2]            until   telem.c: 73 ---*/


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


	/*--- Ported from tc_hand.c:78-89 */
	/* Type definitions */

	public static class TeleCommand {
		private /*uint16_t*/ char TC_word;          /* Received telecommand word */
		private /*unsigned char*/ char TC_address;  /* Telecommand address       */
		private /*unsigned char*/ char TC_code;     /* Telecommand code          */
	}

	public enum MemoryType { Code, Data };

	/*--- Ported from tc_hand.c:90-123 */
	/* Global variables */

	private static TeleCommand previous_TC;
	/* Holds previous telecommand unless a timeout has occurred */

	/*unsigned char EXTERNAL*/ private static char TC_timeout = 0;
	/* Time out for next telecommand, zero means no timeout */


	/*unsigned char EXTERNAL*/ private static byte[] TC_look_up = new byte[128];
	/* Look-up table for all possible 128 TC address values (domain: ALL_INVALID(0) - ONLY_EVEN(4) ) */

	private static TC_State TC_state = TC_State.TC_handling_e; /* 0 ~ TC_handling_e */
	/* internal state of the Telecommand Execution task */

	private static  MemoryType memory_type;
	/* Selection of memory write target (code/data) */ 

	/* unsigned char[] */ private static byte[] memory_transfer_buffer = new byte[MEM_BUFFER_SIZE];
	/* Buffer for memory read and write telecommands */

	/*unsigned char EXTERNAL*/ private static int address_MSB;
	/* MSB of memory read source and write destination addresses */

	/* unsigned char EXTERNAL*/ private static int address_LSB;
	/* LSB of memory read source and write destination addresses. */

	/* uint_least8_t EXTERNAL*/ private static int memory_buffer_index = 0;
	/* Index to memory_buffer. */

	/* unsigned char EXTERNAL */ private static char write_checksum;
	/* Checksum for memory write blocks. */
	
	 
	public static TelemetryData getTelemetryData() {
		return telemetry_data;
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

//	void TM_InterruptService (void) INTERRUPT(TM_ISR_SOURCE) USED_REG_BANK(2)
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

//	{
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
//	}
	
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
	   hit_time = science_data.event[event_number].hit_time;
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
	         science_data.event[record_index].quality_number = 0;
	         free_slot_index++;
	      }


	      /* Increment event counters. */
	      incrementCounters(
	         event_queue[0].SU_number - 1,
	         event_queue[0].classification);

	      // ENABLE_INTERRUPT_MASTER;

	      if (event_queue[0].quality_number >=
	          science_data.event[record_index].quality_number)

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
	   min_quality_number   = science_data.event[0].quality_number;
	   min_quality_location = 0;
	   /* First event is selected and compared against */
	   /* the following events in the science_data.    */

	   for (i=1; i < max_events; i++)
	   {
	      time = GetElapsedTime(i);

	      if(science_data.event[i].quality_number < min_quality_number)
	      {
	         min_time = time;
	         min_quality_number = science_data.event[i].quality_number;
	         min_quality_location = i;
	         /* If an event in the science_data has a lower quality number than  */
	         /* any of the previous events, its quality_number and location is   */
	         /* stored into variables.                                           */
	      }

	      else if(   (science_data.event[i].quality_number == min_quality_number)
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
	         event_queue[i].SU_number - 1,
	         event_queue[i].classification);

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

	
	public void tcInterruptService() {
		// TODO Auto-generated method stub
		
	}

	public void tmInterruptService() {
		// TODO Auto-generated method stub
		
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
	

}
