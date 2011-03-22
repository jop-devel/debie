package debie.harness;

import static debie.harness.Harness.*;
import static debie.telecommand.TcAddress.*;
import debie.telecommand.TelemetryData;
import debie.telecommand.TelecommandExecutionTask.TC_State;

public class TelemetryTest extends HarnessTest {

	public TelemetryTest(HarnessSystem sys, TestLogger tl) {
		super(sys, tl);
	}

	@Override
	public void runTests() {

		testWholeRound();
		testPartial();
		testScienceData();
		
		reportTestResults("TelemetryTest");

		/* TM tests elsewhere:
		 * End of memory-dump TM : See Read_Data_Memory and TC_Task_Tests.
		 * Science Data TM (also): See Acquisition_Tests.
		 */
	}

	private void testWholeRound() {
		testcase("One whole round of register TM");

		sendTC (SEND_STATUS_REGISTER, 0);

		checkNoErrors ();
		checkTcState(TC_State.register_TM_e);

		handleTC (Prob4a);

		checkTcState(TC_State.register_TM_e);

		for (int octets = 0; octets < TelemetryData.sizeInBytes(); octets += 2) {

			if (!tctmTask.telemetryIndexEquals(TelemetryData.TIME_INDEX)) {					
				startProblem(Prob2a);
				tctmTask.tmInterruptService();
				endProblem(Prob2a);
			} else {
				startProblem(Prob2b);
				tctmTask.tmInterruptService();
				endProblem(Prob2b);
			}

			checkTcState(TC_State.register_TM_e);
		}
	}

	private void testPartial() {
		testcase("Partial register TM, stop by TC");

		sendTC (SEND_STATUS_REGISTER, 22);

		checkNoErrors ();
		checkTcState(TC_State.register_TM_e);

		handleTC (Prob4a);

		checkTcState(TC_State.register_TM_e);

		for (int octets = 0; octets < 40; octets += 2) {

			if (!tctmTask.telemetryIndexEquals(TelemetryData.TIME_INDEX)) {					
				startProblem(Prob2a);
				tctmTask.tmInterruptService();
				endProblem(Prob2a);
			} else {
				startProblem(Prob2b);
				tctmTask.tmInterruptService();
				endProblem(Prob2b);
			}

			checkTcState(TC_State.register_TM_e);
		}

		sendTC (ERROR_STATUS_CLEAR, ERROR_STATUS_CLEAR);

		checkTcState(TC_State.TC_handling_e);
		
		handleTC (Prob4a);

		checkNoErrors ();
	}

	private void testScienceData() {
		testcase("Science Data TM");

		sendTC (SEND_SCIENCE_DATA_FILE, SEND_SCIENCE_DATA_FILE);

		checkNoErrors ();
		checkTcState(TC_State.SC_TM_e);

		handleTC (Prob4a);

		checkTcState(TC_State.SC_TM_e);

		/* Absorb TM until a TM_READY message is sent to the TC task: */

		int octets = 0;
		while (system.tctmMailbox.getMailCount() == 0) {
			if (!tctmTask.telemetryIndexAtEnd()) {
				startProblem(Prob2a);
				tctmTask.tmInterruptService();
				endProblem(Prob2a);
			} else {
				startProblem(Prob2c);
				tctmTask.tmInterruptService();
				endProblem(Prob2c);
			}

			octets += 2;
			checkTcState(TC_State.SC_TM_e);
		}

		if (Harness.TRACE)Harness.trace(String.format("Science TM octets sent %d", octets)); 

		/* Handle the TM_READY message: */

		handleTC (Prob4a);

		checkTcState(TC_State.TC_handling_e);
	}
	
}
