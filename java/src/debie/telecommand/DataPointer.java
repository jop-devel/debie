package debie.telecommand;

import debie.support.Dpu;
import debie.support.TelemetryObject;

/** Emulate a pointer to plain data memory */
public class DataPointer implements TelemetryObject {
	private int base;
	
	public DataPointer(int base) {
		
		this.base = base;
	}
	
	public void setBase(int base) {
		
		this.base = base;
	}
	
	public int getByte(int addr) {
		
		return Dpu.getDataByte(base+addr);
	}
}
