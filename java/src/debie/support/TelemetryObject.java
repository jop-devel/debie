package debie.support;

/** serializable objects, which are send via telemetry */
public interface TelemetryObject {
	// should provide constants
	// SIZE_IN_BYTES
	// INDEX_BITS (number of bits needed to keep track of serialization state)
	public int getByte(int index);
}
