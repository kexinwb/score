package com.hp.oo.engine.queue.services.recovery;

/**
 * Created by IntelliJ IDEA.
 * User: Amit Levin
 * Date: 20/11/12
 */
public interface ExecutionRecoveryService {
	
	public void doRecovery();

	public void doWorkerRecovery(String workerName);
	
}
