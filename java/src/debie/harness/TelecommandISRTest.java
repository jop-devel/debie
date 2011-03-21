package debie.harness;

import debie.target.TcTmDev;
import debie.telecommand.TelecommandExecutionTask.TC_State;
import static debie.telecommand.TcAddress.*;

public class TelecommandISRTest extends HarnessTest {

	public TelecommandISRTest(HarnessSystem sys, TestLogger tl) {
		super(sys, tl);
	}

	@Override
	public void runTests() {
		
		testRejectOnOverflow();
		testRejectOnStateA();
		testRejectOnStateB();
		testStatePatch();
		testParityError();
		testErrorStatusClear();
		testSendStatusReg();
		testAllInvalid();
		testOnlyEqual();
		testOnOffTc();
		testOnlyEvenOdd();
		testOnlyEvenTooLarge();
		reportTestResults("TelecommandISRTest");
	}

	private void testRejectOnOverflow() {
		testcase("TC rejected because timer overflow is not set");		
		
		system.tctmSim.clearTimerOverflowFlag();
		tcInterrupt();
	}
	
	private void testRejectOnStateA() {
		testcase("TC rejected because TC_state is SC_TM_e");		
		
		tctmTask.setTC_State(TC_State.SC_TM_e);
		sendTC(0, 0);
	}

	private void testRejectOnStateB() {
		testcase("TC rejected because TC_state is memory_dump_e");		
		
		tctmTask.setTC_State(TC_State.memory_dump_e);
		sendTC(0, 0);
	}

	private void testStatePatch() {
		testcase("TC in TC_state = memory_patch_e");		
		
		tctmTask.setTC_State(TC_State.memory_patch_e);
		sendTC(0, 0);
		checkEquals("mail count of tctm mailbox = 1", system.tctmMailbox.getMailCount(), 1);
		system.tctmMailbox.flushMail();
	}

	private void testParityError() {
		testcase("TC with parity error");
		
		tctmTask.setTC_State(TC_State.TC_handling_e);
		system.tctmSim.setTimerOverflowFlag();
		system.tctmSim.tc_msb = 0;
		system.tctmSim.tc_lsb = 1;
		system.tctmSim.tc_word = 1;
		tcInterrupt();
		
		checkNonZero(tctmTask.getErrorStatus() & TcTmDev.PARITY_ERROR);
	}
	
	private void testErrorStatusClear() {
		testcase("TC = ERROR_STATUS_CLEAR, ok");
		
		sendTC(ERROR_STATUS_CLEAR, ERROR_STATUS_CLEAR);
		
		/* The parity-error flag is not yet reset, because */
		/* the TC was not yet executed:                    */
		checkNonZero(tctmTask.getErrorStatus() & TcTmDev.PARITY_ERROR);
		
		checkEquals("mail count of tctm mailbox = 1", system.tctmMailbox.getMailCount(), 1);
		system.tctmMailbox.flushMail();

		/* Clear the error manually: */

	    tctmTask.getTelemetryData().clearErrorStatus();

	    checkNoErrors();
	}
	
	private void testSendStatusReg() {
		testcase("TC = SEND_STATUS_REGISTER, ok");
		
		sendTC(SEND_STATUS_REGISTER, 8);
		
		checkNoErrors();
		checkTcState(TC_State.register_TM_e);
		checkEquals("mail count of tctm mailbox = 1", system.tctmMailbox.getMailCount(), 1);
		system.tctmMailbox.flushMail();
	}
	
	private void testAllInvalid() {
		testcase("TC type ALL_INVALID");
		
		sendTC(4, 4);
		
		checkEquals("error status = TC_ERROR", tctmTask.getErrorStatus(), TcTmDev.TC_ERROR);
		checkEquals("mail count of tctm mailbox = 0", system.tctmMailbox.getMailCount(), 0);	
		tctmTask.getTelemetryData().clearErrorStatus();
	}
	
	private void testOnlyEqual() {
		testcase("TC type ONLY_EQUAL, fail");
		
		sendTC(ERROR_STATUS_CLEAR, ~ERROR_STATUS_CLEAR);
		
		checkEquals("error status = TC_ERROR", tctmTask.getErrorStatus(), TcTmDev.TC_ERROR);
		checkEquals("mail count of tctm mailbox = 0", system.tctmMailbox.getMailCount(), 0);		
		tctmTask.getTelemetryData().clearErrorStatus();
	}
	
	private void testOnOffTc() {
		testcase("TC type ON_OFF_TC, fail");
		
		/* Neither ON_VALUE nor OFF_VALUE nor SELF_TEST. */

		sendTC (SWITCH_SU_3, 0x3F);
		
		checkEquals("error status = TC_ERROR", tctmTask.getErrorStatus(), TcTmDev.TC_ERROR);
		checkEquals("mail count of tctm mailbox = 0", system.tctmMailbox.getMailCount(), 0);		
		tctmTask.getTelemetryData().clearErrorStatus();
	}
	
	private void testOnlyEvenOdd() {
		testcase("TC type ONLY_EVEN, fail (odd)");
		
		sendTC(SEND_STATUS_REGISTER, 5);
		
		checkTcState(TC_State.TC_handling_e);
		checkEquals("error status = TC_ERROR", tctmTask.getErrorStatus(), TcTmDev.TC_ERROR);
		checkEquals("mail count of tctm mailbox = 0", system.tctmMailbox.getMailCount(), 0);		
		tctmTask.getTelemetryData().clearErrorStatus();
	}
	
	private void testOnlyEvenTooLarge() {
		testcase("TC type ONLY_EVEN, fail (too large)");
		
		sendTC(SEND_STATUS_REGISTER, LAST_EVEN + 2);
		
		checkTcState(TC_State.TC_handling_e);
		checkEquals("error status = TC_ERROR", tctmTask.getErrorStatus(), TcTmDev.TC_ERROR);
		checkEquals("mail count of tctm mailbox = 0", system.tctmMailbox.getMailCount(), 0);		
		tctmTask.getTelemetryData().clearErrorStatus();
	}
}
