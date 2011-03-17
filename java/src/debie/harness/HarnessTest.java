package debie.harness;

import static debie.harness.Harness.Prob1;
import static debie.harness.Harness.Prob4a;
import static debie.harness.Harness.Prob4b;
import static debie.harness.Harness.Prob4c;
import static debie.harness.Harness.Prob4d;
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
	private int start_conversion_count;
	private int end_of_adc_count;

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
		
	/*--- Common Tests ---*/
	
	protected void monitorHealth (int problem)
	/* Executes HandleHealthMonitoring for a particular analysis problem. */
	{
	   start_conversion_count = 0;
	   end_of_adc_count       = 0;

	   if(Harness.INSTRUMENTATION) Harness.startProblem(problem);
	   system.hmTask.handleHealthMonitor();
	   if(Harness.INSTRUMENTATION) Harness.endProblem(problem);

	   reportStartConversionCount (problem);
	   reportEndOfADCCount        (problem);
	}

	protected void reportStartConversionCount (int problem)
	/* Reports and then clears the count of Start_Conversion calls.
	 * The problem parameter associates this count with a given
	 * analysis problem for this benchmark.
	 */
	{
		if(Harness.TRACE) {
			Harness.trace(String.format("[HarnessTest] Called Start_Conversion %d times in problem %d.",
				      start_conversion_count, problem));
		}

	   start_conversion_count = 0;
	}
	
	void reportEndOfADCCount (int problem)
	/* Reports and then clears the count of End_Of_ADC calls.
	 * The problem parameter associates this count with a given
	 * analysis problem for this benchmark.
	 */
	{
		if(Harness.TRACE) {
			Harness.trace(String.format("[HarnessTest] Called End_Of_ADC %d times in problem %d.",
				      end_of_adc_count, problem));
		}
		end_of_adc_count = 0;
	}


}
