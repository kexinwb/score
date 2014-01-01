package com.hp.oo.engine.queue.services;

import com.hp.oo.engine.queue.entities.ExecStatus;
import com.hp.oo.engine.queue.entities.ExecutionMessage;
import com.hp.oo.engine.queue.entities.Payload;

import java.util.Date;
import java.util.List;

/**
 * User: Amit Levin
 * Date: 10/09/12
 * Time: 11:00
 */
public interface QueueDispatcherService {

	void dispatch(List<ExecutionMessage> messages);

	List<ExecutionMessage> poll(String uuid, int maxSize, Date createDate);

	void dispatch(String messageId, String group, ExecStatus status, Payload payload);
}
