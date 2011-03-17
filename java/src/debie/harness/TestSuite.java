package debie.harness;


public abstract class TestSuite {
	private TestLogger td;
	private int checks;
	private int check_errors;

	public int getChecks() {
		return checks;
	}
	public int getCheckErrors() {
		return check_errors;
	}

	public abstract void runTests();
	
	public TestSuite(TestLogger td) {
		this.td = td;
		this.checks = 0;
		this.check_errors = 0;
	}
	
	protected void testcase(String name) {
		td.startTest(name);
	}
		
	protected void checkEquals(String msg, int o1, int o2) {
		checks ++;
		if(o1 != o2) {
			failCheck("assertEquals: " + msg);
		}
	}

	protected<T> void checkEquals(String msg, T o1, T o2) {
		checks ++;
		if(! o1.equals(o2)) {
			failCheck("assertEquals: " + msg);
		}
	}
	
	protected void checkZero(int cond) 
	/* Checks that cond == 0. */
	{
		checks ++;
		if (cond != 0)
		{
			failCheck("Check_Zero");
		}
	}


	protected void checkNonZero (int cond)
	/* Checks that cond != 0. */
	{
		checks ++;
		if (cond == 0)
		{
			failCheck ("Check_Nonzero");
		}
	}

	protected void checkTrue (boolean cond)
	/* Checks that cond == true. */
	{
		checkNonZero(cond ? 1 : 0);
	}

	protected void checkFalse (boolean cond)
	/* Checks that cond == false. */
	{
		checkZero(cond ? 1 : 0);
	}

	protected void failCheck (String message)
	/* Reports a failed check. */
	{
	   check_errors ++;
	   if(Harness.TRACE) Harness.trace(String.format("%s: FAILED (#%d)\n", message, check_errors));
	}

}
