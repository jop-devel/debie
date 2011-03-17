package debie.harness;

import static debie.harness.Harness.Prob1;
import static debie.harness.Harness.Prob4a;
import static debie.telecommand.TcAddress.ERROR_STATUS_CLEAR;
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
	void monitorHealth (int problem)
	/* Executes HandleHealthMonitoring for a particular analysis problem. */
	{
//	   start_conversion_count = 0;
//	   end_of_adc_count       = 0;
//
//	   if(Harness.INSTRUMENTATION) Harness.startProblem(problem);
//	   handleHealthMonitoring();
//	   if(Harness.INSTRUMENTATION) Harness.endProblem(problem);
//
//	   Report_Start_Conversion_Count (problem);
//	   Report_End_Of_ADC_Count       (problem);
	}
}
