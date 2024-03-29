package debie.support;

import debie.health.HealthMonitoringTask;
import debie.particles.AcquisitionTask;
import debie.particles.HitTriggerTask;
import debie.support.Dpu.Time;
import debie.target.AdConverter;
import debie.target.SensorUnitDev;
import debie.target.TcTmDev;
import debie.telecommand.TelecommandExecutionTask;
import debie.telecommand.TelemetryData;

/**
 *  This is the interface used to communicate with other modules in the
 *  DEBIE system. It provides references for all tasks, mailboxes and
 *  hardware used.
 *  
 *  TODO: Discussion: Is this a good idea w.r.t. encapsulation?
 */
public interface DebieSystem {
	public TaskControl				getTaskControl();
	
	public TelemetryData            getTelemetryData();
	
	public AcquisitionTask          getAcquisitionTask();
	public HealthMonitoringTask     getHealthMonitoringTask();
	public TelecommandExecutionTask getTelecommandExecutionTask();
	public HitTriggerTask           getHitTriggerTask();

	public Mailbox                  getAcqMailbox();
	public Mailbox                  getTcTmMailbox();

	public AdConverter              getAdcDevice();
	public SensorUnitDev            getSensorUnitDevice();
	public TcTmDev                  getTcTmDevice();
	public Time                     getInternalTime(); 
}
