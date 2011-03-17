package debie.harness;

import static debie.harness.Harness.*;
import debie.target.TcTmDev;
import debie.telecommand.TelecommandExecutionTask;
import debie.telecommand.TelecommandExecutionTask.TC_State;
import static debie.telecommand.TcAddress.*;
import static debie.telecommand.TelecommandExecutionTask.MEMORY_WRITE_ERROR;

public class TelecommandTaskTest extends HarnessTest {

	public TelecommandTaskTest(HarnessSystem sys, TestLogger tl) {
		super(sys, tl);
	}

	@Override
	public void runTests() {
		testErrorStatusClear();
		testSetSUParams();
		testSetClassificationCoeffs();
		testPatchCodeCall();
		testPatchCodeNoAction();
		testPatchCodeSoftReset();
		testPatchCodeWarmReset();
		testPatchCodeInvalidAction();
		testPatchCodeInvalidAddress();
		testPatchCodeChecksumError();
		testPatchCodeSeqErrorFirst();
		testPatchCodeSeqErrorSecond();
		testPatchData();
		testPatchDataAddressError();
		testPatchDataSeqErrorFirst();
		testPatchDataSeqErrorSecond();
		testPatchDataChecksumError();
		testPatchDataTimeoutA();
		testPatchDataTimeoutB();
		
		if(Harness.TRACE) {
			Harness.trace("[TelecommandTaskTest] Finished");
			Harness.trace("[TelecommandTaskTest] Checks: "+this.getChecks());
			Harness.trace("[TelecommandTaskTest] Failures: "+this.getCheckErrors());
		}  		
	}

	private void testErrorStatusClear() {
		testcase("TC = ERROR_STATUS_CLEAR, ok");
		
		/* Flag an error manually: */
		tctmTask.getTelemetryData().setErrorStatus((byte)TcTmDev.PARITY_ERROR);
		
		sendTC(ERROR_STATUS_CLEAR, ERROR_STATUS_CLEAR);
		/* The parity-error flag is not yet reset, because */
		/* the TC was not yet executed:                    */

		checkNonZero(tctmTask.getErrorStatus() & TcTmDev.PARITY_ERROR);

		handleTC(Prob4a);

		/* Now the parity-error flag is reset: */

		checkNoErrors();
	}

	private void testSetSUParams() {
		testcase("TC to set SU parameters");
		
		execTC(SET_SU_1_PLASMA_1P_THRESHOLD,  23, Prob4a);
	    execTC(SET_SU_2_PLASMA_1P_THRESHOLD,  26, Prob4a);
	    execTC(SET_SU_3_PLASMA_1P_THRESHOLD,  32, Prob4a);
	    execTC(SET_SU_4_PLASMA_1P_THRESHOLD, 102, Prob4a);
		
	    checkNoErrors();
	    
	    execTC(SET_SU_1_PLASMA_1M_THRESHOLD, 205, Prob4a);
	    execTC(SET_SU_2_PLASMA_1M_THRESHOLD, 123, Prob4a);
	    execTC(SET_SU_3_PLASMA_1M_THRESHOLD,  99, Prob4a);
	    execTC(SET_SU_4_PLASMA_1M_THRESHOLD,   1, Prob4a);

	    checkNoErrors();

	    execTC(SET_SU_1_PIEZO_THRESHOLD,  14, Prob4a);
	    execTC(SET_SU_2_PIEZO_THRESHOLD,  54, Prob4a);
	    execTC(SET_SU_3_PIEZO_THRESHOLD,  74, Prob4a);
	    execTC(SET_SU_4_PIEZO_THRESHOLD, 104, Prob4a);

	    checkNoErrors();

	    execTC(SET_SU_1_PLASMA_1P_CLASS_LEVEL, 104, Prob4a);
	    execTC(SET_SU_2_PLASMA_1P_CLASS_LEVEL, 204, Prob4a);
	    execTC(SET_SU_3_PLASMA_1P_CLASS_LEVEL, 214, Prob4a);
	    execTC(SET_SU_4_PLASMA_1P_CLASS_LEVEL, 234, Prob4a);

	    checkNoErrors();

	    execTC(SET_SU_1_PLASMA_1M_CLASS_LEVEL, 104, Prob4a);
	    execTC(SET_SU_2_PLASMA_1M_CLASS_LEVEL,  88, Prob4a);
	    execTC(SET_SU_3_PLASMA_1M_CLASS_LEVEL,  66, Prob4a);
	    execTC(SET_SU_4_PLASMA_1M_CLASS_LEVEL,  33, Prob4a);

	    checkNoErrors();

	    execTC(SET_SU_1_PLASMA_2P_CLASS_LEVEL,  61, Prob4a);
	    execTC(SET_SU_2_PLASMA_2P_CLASS_LEVEL,  21, Prob4a);
	    execTC(SET_SU_3_PLASMA_2P_CLASS_LEVEL,  81, Prob4a);
	    execTC(SET_SU_4_PLASMA_2P_CLASS_LEVEL,  11, Prob4a);

	    checkNoErrors();

	    execTC(SET_SU_1_PIEZO_1_CLASS_LEVEL,  14, Prob4a);
	    execTC(SET_SU_2_PIEZO_1_CLASS_LEVEL,  24, Prob4a);
	    execTC(SET_SU_3_PIEZO_1_CLASS_LEVEL,  33, Prob4a);
	    execTC(SET_SU_4_PIEZO_1_CLASS_LEVEL,  77, Prob4a);

	    checkNoErrors();

	    execTC(SET_SU_1_PIEZO_2_CLASS_LEVEL,  14, Prob4a);
	    execTC(SET_SU_2_PIEZO_2_CLASS_LEVEL,  14, Prob4a);
	    execTC(SET_SU_3_PIEZO_2_CLASS_LEVEL,  14, Prob4a);
	    execTC(SET_SU_4_PIEZO_2_CLASS_LEVEL,  14, Prob4a);

	    checkNoErrors();

	    execTC(SET_SU_1_PLASMA_1E_1I_MAX_TIME, 191, Prob4a);
	    execTC(SET_SU_2_PLASMA_1E_1I_MAX_TIME, 171, Prob4a);
	    execTC(SET_SU_3_PLASMA_1E_1I_MAX_TIME, 161, Prob4a);
	    execTC(SET_SU_4_PLASMA_1E_1I_MAX_TIME, 151, Prob4a);

	    checkNoErrors();

	    execTC(SET_SU_1_PLASMA_1E_PZT_MIN_TIME,  11, Prob4a);
	    execTC(SET_SU_2_PLASMA_1E_PZT_MIN_TIME,  22, Prob4a);
	    execTC(SET_SU_3_PLASMA_1E_PZT_MIN_TIME,  33, Prob4a);
	    execTC(SET_SU_4_PLASMA_1E_PZT_MIN_TIME,  44, Prob4a);

	    checkNoErrors();

	    execTC(SET_SU_1_PLASMA_1E_PZT_MAX_TIME, 111, Prob4a);
	    execTC(SET_SU_2_PLASMA_1E_PZT_MAX_TIME, 122, Prob4a);
	    execTC(SET_SU_3_PLASMA_1E_PZT_MAX_TIME, 133, Prob4a);
	    execTC(SET_SU_4_PLASMA_1E_PZT_MAX_TIME, 144, Prob4a);

	    checkNoErrors();

	    execTC(SET_SU_1_PLASMA_1I_PZT_MIN_TIME,  11, Prob4a);
	    execTC(SET_SU_2_PLASMA_1I_PZT_MIN_TIME,  10, Prob4a);
	    execTC(SET_SU_3_PLASMA_1I_PZT_MIN_TIME,   9, Prob4a);
	    execTC(SET_SU_4_PLASMA_1I_PZT_MIN_TIME,   8, Prob4a);

	    checkNoErrors();

	    execTC(SET_SU_1_PLASMA_1I_PZT_MAX_TIME, 211, Prob4a);
	    execTC(SET_SU_2_PLASMA_1I_PZT_MAX_TIME, 210, Prob4a);
	    execTC(SET_SU_3_PLASMA_1I_PZT_MAX_TIME, 209, Prob4a);
	    execTC(SET_SU_4_PLASMA_1I_PZT_MAX_TIME, 208, Prob4a);

	    checkNoErrors();
	}

	private void testSetClassificationCoeffs() {
		testcase("TC to set classification coefficients");
		
		execTC(SET_COEFFICIENT_1, 1, Prob4a);
	    execTC(SET_COEFFICIENT_2, 2, Prob4a);
	    execTC(SET_COEFFICIENT_3, 3, Prob4a);
	    execTC(SET_COEFFICIENT_4, 4, Prob4a);
	    execTC(SET_COEFFICIENT_5, 5, Prob4a);

	    checkNoErrors();	
	}

	private void testPatchCodeCall() {
		testcase("TC to patch code memory, call patch");
		
		int chsum;
		
		chsum = sendPatchCode(0x1100);
		
		chsum ^= 0x5A;   /* Call patch function. */

		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

		sendTCWord(0x5A00 | chsum);
	    handleTC (Prob4a);

	    checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

	    checkNoErrors();
	    checkTcState(TC_State.TC_handling_e);
	}
	
	private void testPatchCodeNoAction() {
		testcase("TC to patch code memory, no action");
		
		int chsum;
		
		chsum = sendPatchCode(0x1300);
		
		chsum ^= 0x00;   /* No action. */

		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

		sendTCWord(0x0000 | chsum);
	    handleTC (Prob4a);

	    checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

	    checkNoErrors();
	    checkTcState(TC_State.TC_handling_e);
	}
	
	private void testPatchCodeSoftReset() {
		testcase("TC to patch code memory, soft reset");
		
		int chsum;
		
		chsum = sendPatchCode(0x1400);
		
		chsum ^= 0x09;   /* Soft Reset. */

		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

		sendTCWord(0x0900 | chsum);
	    handleTC (Prob4a);

	    checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

	    checkNoErrors();
	    checkTcState(TC_State.TC_handling_e);
	}
	
	private void testPatchCodeWarmReset() {
		testcase("TC to patch code memory, warm reset");
		
		int chsum;
		
		chsum = sendPatchCode(0x2100);
		
		chsum ^= 0x37;   /* Warm Reset. */

		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

		sendTCWord(0x3700 | chsum);
	    handleTC (Prob4a);

	    checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

	    checkNoErrors();
	    checkTcState(TC_State.TC_handling_e);
	}
	
	private void testPatchCodeInvalidAction() {
		testcase("TC to patch code memory, invalid action");
		
		int chsum;
		
		chsum = sendPatchCode(0x2400);
		
		chsum ^= 0x62;   /* Invalid. */

		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

		sendTCWord(0x6200 | chsum);
	    handleTC (Prob4a);

	    checkEquals("error status = TC_ERROR", tctmTask.getErrorStatus(), TcTmDev.TC_ERROR);
	    
	    checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);
	    
	    clearErrors();
	}

	private void testPatchCodeInvalidAddress() {
		testcase("TC to patch code memory, invalid address");

		int chsum;

		chsum = sendPatchCode(0x0fff);

		chsum ^= 0x00;   /* No action. */

		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

		sendTCWord(0x0000 | chsum);
		handleTC (Prob4a);

		checkEquals("error status = TC_ERROR", tctmTask.getErrorStatus(), TcTmDev.TC_ERROR);

		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

		clearErrors();
	}
	
	private void testPatchCodeChecksumError() {
		testcase("TC to patch code memory, checksum error");

		int chsum;

		chsum = sendPatchCode(0x1200);

		chsum ^= 0x5A;   /* Correct checksum. */
		chsum ^= 0xff;   /* Wrong   checksum. */

		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

		sendTCWord(0x5A00 | chsum);
		handleTC (Prob4a);

		checkNonZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);
	    checkTcState(TC_State.TC_handling_e);

		clearErrors();
	}
	
	private void testPatchCodeSeqErrorFirst() {
		testcase("TC to patch code, TC sequence error at first word");

		execTC(WRITE_CODE_MEMORY_LSB, 0x32, Prob4a);
		
		checkEquals("error status = TC_ERROR", tctmTask.getErrorStatus(), TcTmDev.TC_ERROR);		
		
		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

		clearErrors();
	}
	
	private void testPatchCodeSeqErrorSecond() {
		testcase("TC to patch code, TC sequence error at second word");

		execTC(WRITE_CODE_MEMORY_MSB, 0x32, Prob4a);
		
		checkNoErrors();
		checkTcState(TC_State.write_memory_e);
		
		execTC(CLEAR_WATCHDOG_FAILURES, CLEAR_WATCHDOG_FAILURES, Prob4a);
		
		checkEquals("error status = TC_ERROR", tctmTask.getErrorStatus(), TcTmDev.TC_ERROR);		
		
		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

		clearErrors();
	}
	
	private void testPatchData() {
		testcase("TC to patch data memory");
		
		int chsum;
		
		chsum = sendPatchData(0x2200);
		
		chsum ^= 0x11;   /* Irrelevant for data patch. */

		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);
	
		sendTCWord(0x1100 | chsum);
		handleTC(Prob4a);
		
		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

		checkNoErrors();
	    checkTcState(TC_State.TC_handling_e);		
	}
	
	private void testPatchDataAddressError() {
		testcase("TC to patch data memory, address error");
		
		int chsum;
		
		chsum = sendPatchData(0xfef0);
		
		chsum ^= 0x11;   /* Irrelevant for data patch. */
		
		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);
		
		sendTCWord(0x1100 | chsum);
		handleTC(Prob4a);
		
		checkEquals("error status = TC_ERROR", tctmTask.getErrorStatus(), TcTmDev.TC_ERROR);		
		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);
		
		clearErrors();
	}
	
	private void testPatchDataSeqErrorFirst() {
		testcase("TC to patch data memory, TC sequence error at first word");
		
		execTC(WRITE_DATA_MEMORY_LSB, 0x32, Prob4a);
		
		checkEquals("error status = TC_ERROR", tctmTask.getErrorStatus(), TcTmDev.TC_ERROR);
		
		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);
		
		clearErrors();
	}
	
	private void testPatchDataSeqErrorSecond() {
		testcase("TC to patch data memory, TC sequence error at second word");
		
		execTC(WRITE_DATA_MEMORY_MSB, 0x32, Prob4a);
		
		checkNoErrors();
		checkTcState(TC_State.write_memory_e);
		
		execTC(CLEAR_WATCHDOG_FAILURES, CLEAR_WATCHDOG_FAILURES, Prob4a);
		
		checkEquals("error status = TC_ERROR", tctmTask.getErrorStatus(), TcTmDev.TC_ERROR);		
		
		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

		clearErrors();
	}
	
	private void testPatchDataChecksumError() {
		testcase("TC to patch data memory, checksum error");

		int chsum;

		chsum = sendPatchCode(0x2300);

		chsum ^= 0x11;   /* Correct checksum. */
		chsum ^= 0xff;   /* Wrong   checksum. */

		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

		sendTCWord(0x1100 | chsum);
		handleTC (Prob4a);

		checkNonZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);
	    checkTcState(TC_State.TC_handling_e);

		clearErrors();
	}
	
	private void testPatchDataTimeoutA() {
		testcase("TC to patch data memory, time-out on TC word reception");
		
		int chsum;
		
		chsum = sendPatchData(0x2200);
		
		chsum ^= 0x11;   /* Irrelevant for data patch. */
		
		checkTcState(TC_State.memory_patch_e);
		checkZero (TelecommandExecutionTask.getTelemetryData().getModeBits() & MEMORY_WRITE_ERROR);

		checkEquals("mail count of tctm mailbox = 0", system.tctmMailbox.getMailCount(), 0);
		
		/* Empty mailbox => WaitMail signals "time-out". */

		if(Harness.INSTRUMENTATION) Harness.startProblem(Prob4a);
		tctmTask.handleTelecommand();
		if(Harness.INSTRUMENTATION) Harness.endProblem(Prob4a);
		
	    checkTcState(TC_State.TC_handling_e);
		checkEquals("error status = TC_ERROR", tctmTask.getErrorStatus(), TcTmDev.TC_ERROR);		

		clearErrors();
	}
	
	private void testPatchDataTimeoutB() {
		testcase("TC timeout during TC handling, normal");
		
		checkEquals("mail count of tctm mailbox = 0", system.tctmMailbox.getMailCount(), 0);

		/* Empty mailbox => WaitMail signals "time-out". */

		if(Harness.INSTRUMENTATION) Harness.startProblem(Prob4a);
		tctmTask.handleTelecommand();
		if(Harness.INSTRUMENTATION) Harness.endProblem(Prob4a);
		
	    checkTcState(TC_State.TC_handling_e);
	    checkNoErrors();
	}
	
}


