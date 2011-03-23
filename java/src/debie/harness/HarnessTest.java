package debie.harness;

import static debie.harness.Harness.Prob1;
import static debie.harness.Harness.Prob2a;
import static debie.harness.Harness.Prob2c;
import static debie.harness.Harness.Prob4a;
import static debie.harness.Harness.Prob4b;
import static debie.harness.Harness.Prob4c;
import static debie.harness.Harness.Prob4d;
import static debie.target.TcTmDev.TC_ERROR;
import static debie.telecommand.TcAddress.*;
import debie.health.HealthMonitoringTask;
import debie.particles.AcquisitionTask;
import debie.telecommand.TelecommandExecutionTask;
import debie.telecommand.TelecommandExecutionTask.TC_State;

public abstract class HarnessTest extends TestSuite {

	protected HarnessSystem system;
	protected AcquisitionTask acqTask;
	protected TelecommandExecutionTask tctmTask;
	protected HealthMonitoringTask hmTask;
	
	public HarnessTest(HarnessSystem sys, TestLogger td) {
		super(td);
		this.system = sys;
		this.acqTask = sys.acqTask;
		this.tctmTask = sys.tctmTask;
		this.hmTask = sys.hmTask;
	}

	
	/** Invokes TC_InterruptService with a TC composed of the
	 * given address and code, provided with valid (even) parity,
	 * then invokes HandleTelecommand to execute the TC.
	 * The problem parameter shows in which analysis problem the
	 * HandleTelecommand should be included.
	 */	
	public void execTC(int address, int code, int problem)
	{
		sendTC (address, code);
		handleTC (problem);
	}

	/** Invokes TC_InterruptService with a TC composed of the
	 * given address and code, provided with valid (even) parity.
	 */
	public void sendTC (/* unsigned char */ int address, /* unsigned char */ int code)
	{
	   system.tctmSim.setTcRegs(address,code);

	   /* Invoke the TC ISR: */

	   system.tctmSim.setTimerOverflowFlag();

	   tcInterrupt ();
	}
	
	public void sendTCWord (/* uint_least16_t */ int word)
	{
	   system.tctmSim.setTcRegsWord(word);

	   /* Invoke the TC ISR: */

	   system.tctmSim.setTimerOverflowFlag();

	   tcInterrupt ();
	}
	
	protected void tcInterrupt ()
	/* Runs the TC Interrupt Service. */	
	{
	   /* Analysis Problem (1) */
	   if(Harness.INSTRUMENTATION) Harness.startProblem(Prob1);
	   system.tctmTask.tcInterruptService();
	   if(Harness.INSTRUMENTATION) Harness.endProblem(Prob1);
	}

	public void handleTC (int problem)
	/* Checks that the TC mailbox has a message, then invokes
	 * HandleTelecommand to handle (usually, execute) the message.
	 * The problem parameter shows in which analysis problem the
	 * HandleTelecommand should be included.
	 */
	{
	   checkEquals ("[handleTC] TC mailbox has 1 msg", system.tctmMailbox.getMailCount(), 1);

	   if(Harness.INSTRUMENTATION) Harness.startProblem(problem);
	   system.tctmTask.handleTelecommand ();
	   if(Harness.INSTRUMENTATION) Harness.endProblem(problem);

	   checkEquals ("[handleTC] TC mailbox has 0 msgs", system.tctmMailbox.getMailCount(), 0);
	}

	/** Executes HandleHealthMonitoring for a particular analysis problem. */
	protected void monitorHealth (int problem)
	{
	   system.adcSim.start_conversion_count = 0;
	   system.adcSim.end_of_adc_count       = 0;
	   
	   if(Harness.INSTRUMENTATION) Harness.startProblem(problem);
	   system.hmTask.handleHealthMonitor();
	   if(Harness.INSTRUMENTATION) Harness.endProblem(problem);

	   reportStartConversionCount (problem);
	   reportEndOfADCCount        (problem);
	}

	/**
	 * Invoke HandleHitTrigger. 
	 * The problem parameter defines the analysis problem for this test.
	 */
	protected void triggerHit(int problem) {
	   checkEquals("no acq mail", system.acqMailbox.getMailCount(), 0);
	   if(Harness.TRACE) Harness.trace("[HarnessTest] Hit!");
	
	   if(Harness.INSTRUMENTATION) Harness.startProblem(problem);
	
	   system.acqTask.handleHitTrigger();
	
	   if(Harness.INSTRUMENTATION) Harness.endProblem(problem);
	
	   if(Harness.TRACE) {
		   if(system.acqMailbox.getMailCount() == 0) {
			   Harness.trace("[HarnessTest] - hit rejected");
		   } else {   
			   Harness.trace("[HarnessTest] - hit accepted");
		   }
	   }
	}
	
	/**
	 * Invoke HandleHitTrigger with the given SU in trigger_source_0/1. 
	 * The problem parameter defines the analysis problem for this test.
	 */
	protected void triggerSUHit (int SU, int problem) {
		system.suSim.setTriggerSU (SU);
		triggerHit (problem);
	}

	/**
	 * Sends the multi-word TC to patch code memory at the given address,
	 * with some arbitary contents. Returns the checksum of the patch,
	 * for use in the final word of the TC, which is not sent here.
	 */
	public int sendPatchCode(int address) {
		int sum;
		
		/* Send the patch address: */

		execTC(WRITE_CODE_MEMORY_MSB, (address >> 8) & 0xff, Prob4b); 

		sum = (system.tctmSim.tc_word >> 8) ^ (system.tctmSim.tc_word & 0xff);
		
		checkNoErrors();
		checkTcState(TC_State.write_memory_e);
		
		execTC(WRITE_CODE_MEMORY_LSB, address & 0xff, Prob4c);

		sum ^= (system.tctmSim.tc_word >> 8) ^ (system.tctmSim.tc_word & 0xff);

		checkNoErrors();
		checkTcState(TC_State.memory_patch_e);
	
		/* Send the patch contents, 16 words = 32 octets: */

		for (int i = 0; i < 16; i++) {
			sendTCWord(i << 6);
			sum ^= (system.tctmSim.tc_word >> 8) ^ (system.tctmSim.tc_word & 0xff);
			handleTC(Prob4d);
		}

		/* The last word remains to be sent. */
		return sum;
	}
	
	/**
	 * Sends the multi-word TC to patch data memory at the given address,
	 * with some arbitary contents. Returns the checksum of the patch,
	 * for use in the final word of the TC, which is not sent here.
	 */
	public int sendPatchData(int address) {
		int sum;
		
		/* Send the patch address: */

		execTC(WRITE_DATA_MEMORY_MSB, (address >> 8) & 0xff, Prob4b); 

		sum = (system.tctmSim.tc_word >> 8) ^ (system.tctmSim.tc_word & 0xff);

		checkNoErrors();
		checkTcState(TC_State.write_memory_e);

		execTC(WRITE_DATA_MEMORY_LSB, address & 0xff, Prob4c);

		sum ^= (system.tctmSim.tc_word >> 8) ^ (system.tctmSim.tc_word & 0xff);

		checkNoErrors();
		checkTcState(TC_State.memory_patch_e);

		/* Send the patch contents, 16 words = 32 octets: */

		for (int i = 0; i < 16; i++) {
			sendTCWord (i << 6);
			sum ^= (system.tctmSim.tc_word >> 8) ^ (system.tctmSim.tc_word & 0xff);
			handleTC (Prob4d);
		}

		/* The last word remains to be sent. */
		return sum;
	}
	
	/**
	 * Sends the TC to read 32 octets of data memory, starting at
	 * the given address, receives the corresponding TM block, and
	 * handles the TM_READY message to the TC Execution task.
	 */
	public void readDataMemory (int address) {

		execTC (READ_DATA_MEMORY_MSB, (address >> 8) & 0xff, Prob4a);

		checkNoErrors();
		checkTcState(TC_State.read_memory_e);

		execTC (READ_DATA_MEMORY_LSB, address & 0xff, Prob4a);

		checkNoErrors();
		checkTcState(TC_State.memory_dump_e);

		/* The 16 first words of data: */

	   for (int i = 0; i < 16; i++) {
	      checkFalse(tctmTask.telemetryIndexAtEnd());
	      
	      /* The first TM IT, below, acknowledges the immediate TC
	       * response and transmits the first data word.
	       */

	      if(Harness.INSTRUMENTATION) Harness.startProblem(Prob2a);	      
	      tctmTask.tmInterruptService();
	      if(Harness.INSTRUMENTATION) Harness.endProblem(Prob2a);	      

	      checkTcState(TC_State.memory_dump_e);
	   }

	   /* The last word, with the checksum: */
	   
	   checkTrue(tctmTask.telemetryIndexAtEndExactly());

	   /* The TM IT below acknowledges the last data word and
	    * transmits the very last word of the Read Data Memory
	    * sequence, containing the data checksum.
	    */

	   if(Harness.INSTRUMENTATION) Harness.startProblem(Prob2c);	      
	   tctmTask.tmInterruptService();
	   if(Harness.INSTRUMENTATION) Harness.endProblem(Prob2c);	      

	   /* The TM_READY message: */

	   handleTC (Prob1);

	   checkNoErrors ();
	   checkTcState(TC_State.TC_handling_e);

	   /* There is a design error in TM_InterruptService:
	    * when the Read Data Memory sequence ends, with the
	    * transmission of the checksum word, the TM IT is
	    * left enabled. If the TC Execution task does not
	    * react quickly to the TM_READY message, and disable
	    * the TM IT before the transmission of the checksum
	    * word is completed, a new TM IT can invoke
	    * TM_InterruptService once again, sending the checksum
	    * word a second time, and perhaps a third time etc.
	    */
	}
	
	protected void clearErrors ()
	/* Executes the ERROR_STATUS_CLEAR TC. */
	{
	   execTC (ERROR_STATUS_CLEAR, ERROR_STATUS_CLEAR, Prob4a);

	   checkNoErrors ();
	   checkTcState(TC_State.TC_handling_e);
	}
	
	protected void checkTcState(TC_State expectedState) {
		checkEquals ("check tc state", system.tctmTask.getTC_State(), expectedState);		
	}

	protected void checkNoErrors()
	/* Checks that no errors are flagged in telemetry_data.error_status. */
	{
		checkZero (system.tctmTask.getErrorStatus());
	}
	
	protected void checkNoTcError() {
		checkZero (tctmTask.getErrorStatus() & TC_ERROR);		
	}


	protected void checkTcError() {
		checkEquals ("tm status = error", tctmTask.getErrorStatus(), TC_ERROR);		
	}


	protected void checkMode(int expectedMode) {
		checkEquals ("check telemetry mode", system.getTelemetryData().getMode(), expectedMode);		
	}

	/*--- Common Tests ---*/
	
	/** Reports and then clears the count of Start_Conversion calls.
	 * The problem parameter associates this count with a given
	 * analysis problem for this benchmark.
	 */
	protected void reportStartConversionCount (int problem)
	{
		if(Harness.TRACE) {
			Harness.trace(String.format("[HarnessTest] Called Start_Conversion %d times in problem %d.",
					system.adcSim.start_conversion_count, problem));
		}

		system.adcSim.start_conversion_count = 0;
	}
	
	/** Reports and then clears the count of End_Of_ADC calls.
	 * The problem parameter associates this count with a given
	 * analysis problem for this benchmark.
	 */
	protected void reportEndOfADCCount (int problem)
	{
		if(Harness.TRACE) {
			Harness.trace(String.format("[HarnessTest] Called End_Of_ADC %d times in problem %d.",
					system.adcSim.end_of_adc_count, problem));
		}
		system.adcSim.end_of_adc_count = 0;
	}
}
