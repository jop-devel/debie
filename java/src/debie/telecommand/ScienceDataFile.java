/*
  This file is part of JOP, the Java Optimized Processor
    see <http://www.jopdesign.com/>

  Copyright (C) 2011, Benedikt Huber (benedikt@vmars.tuwien.ac.at)

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package debie.telecommand;

import static debie.target.SensorUnitDev.NUM_SU;
import debie.particles.EventRecord;
import debie.support.DebieSystem;
import debie.support.TelemetryObject;
import debie.target.HwIf;

public class ScienceDataFile implements TelemetryObject {

	/* byte: 0-1 */
	char /*unsigned short int*/ length;
	
	/* byte: 2-(2+NUM_SU*NUM_CLASSES-1) */
	private char[][] /*unsigned char*/ event_counter = new char[NUM_SU][TelecommandExecutionTask.NUM_CLASSES];
	
	/* byte: (2+NUM_SU*NUM_CLASSES) */
	byte /*unsigned char*/  not_used;

	/* byte: (3+NUM_SU*NUM_CLASSES) */
	int /*unsigned char*/  counter_checksum;
	
	/* byte: (4+NUM_SU*NUM_CLASSES)-(4+NUM_SU*NUM_CLASSES+MAX_EVENTS*26-1) */	
	EventRecord[] event = new EventRecord[HwIf.MAX_EVENTS];

	private static final int BYTE_INDEX_EVENT_RECORDS = 4 + (NUM_SU * TelecommandExecutionTask.NUM_CLASSES);
	
	public ScienceDataFile(DebieSystem system) {
		
		/* Java only: Array initialization */
		for(int i = 0; i < event.length; ++i) {
			event[i] = new EventRecord(system);
		}
	}
	
	public int getByte(int index) {
		
		if (index == 0) return length & 0xff;
		else if (index == 1) return (length >> 8) & 0xff;
		else if (index < 2+NUM_SU*TelecommandExecutionTask.NUM_CLASSES) {
			int realIdx = index - 2;
			int suIdx = realIdx/TelecommandExecutionTask.NUM_CLASSES;
			int classIdx = realIdx - (suIdx * TelecommandExecutionTask.NUM_CLASSES);
			return event_counter[suIdx][classIdx] & 0xff;
		} else if (index == 2+NUM_SU*TelecommandExecutionTask.NUM_CLASSES) return not_used & 0xff;
		else if (index == 3+NUM_SU*TelecommandExecutionTask.NUM_CLASSES) return counter_checksum & 0xff;
		else if (index < 4+NUM_SU*TelecommandExecutionTask.NUM_CLASSES+HwIf.MAX_EVENTS*EventRecord.SIZE_IN_BYTES) {
			int realIdx = index - (4+NUM_SU*TelecommandExecutionTask.NUM_CLASSES);
			int eventIdx = realIdx/EventRecord.SIZE_IN_BYTES;
			int recordIdx = realIdx - (eventIdx * EventRecord.SIZE_IN_BYTES);
			return event[eventIdx].getByte(recordIdx);
		}
		else return 0;
	}

	public int getEventCounter(int sensor_unit, int classification) {
		
		return event_counter[sensor_unit][classification];
	}

	public void setEventCounter(int sensor_unit, int classification, char counter) {
		
		event_counter[sensor_unit][classification] = counter;
	}

	public void resetEventCounters(int i) {
		
		for(/* DIRECT_INTERNAL uint_least8_t */ int j=0;j<TelecommandExecutionTask.NUM_CLASSES;j++)
		{
			event_counter[i][j] = 0;
		}
	}
	
	/** for byte-wise telemetry transmission, we need to know at which byte index
	 *  a certain event record starts. */
	public int getEventByteOffset(int free_slot_index) {
		
		return BYTE_INDEX_EVENT_RECORDS + (free_slot_index * EventRecord.sizeInBytes()); 
	}
}
