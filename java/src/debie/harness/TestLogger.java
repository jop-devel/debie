package debie.harness;

public class TestLogger {

	void startTest(String name) {
		System.out.println();
		System.out.print("start test: ");
		System.out.println(name);
		System.out.println();
	}

	void failedCheck(String msg) {
		System.out.print("failed check: ");
		System.out.println(msg);		
	}

}
