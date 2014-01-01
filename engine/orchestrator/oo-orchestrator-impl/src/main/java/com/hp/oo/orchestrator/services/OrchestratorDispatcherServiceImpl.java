package com.hp.oo.orchestrator.services;

import com.hp.oo.engine.queue.entities.ExecutionMessage;
import com.hp.oo.engine.queue.services.QueueDispatcherService;
import com.hp.oo.orchestrator.entities.SplitMessage;
import org.apache.commons.lang.Validate;
import org.apache.log4j.Logger;
import org.hamcrest.Matchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static ch.lambdaj.Lambda.filter;

/**
 * Date: 12/1/13
 *
 * @author Dima Rassin
 */
@Service("orchestratorDispatcherService")
public final class OrchestratorDispatcherServiceImpl implements OrchestratorDispatcherService {
	private final Logger logger = Logger.getLogger(getClass());

	@Autowired
	private QueueDispatcherService queueDispatcher;

	@Autowired
	private SplitJoinService splitJoinService;

	@Override
	@Transactional
	public void dispatch(List<? extends Serializable> messages) {
		Validate.notNull(messages, "Messages list is null");

		if (logger.isDebugEnabled()) logger.debug("Dispatching " + messages.size() + " messages");
		long t = System.currentTimeMillis();
		final AtomicInteger messagesCounter = new AtomicInteger(0);

		dispatch(messages, ExecutionMessage.class, new Handler<ExecutionMessage>() {
			@Override
			public void handle(List<ExecutionMessage> messages) {
				messagesCounter.addAndGet(messages.size());
				queueDispatcher.dispatch(messages);
			}
		});

		dispatch(messages, SplitMessage.class, new Handler<SplitMessage>() {
			@Override
			public void handle(List<SplitMessage> messages) {
				messagesCounter.addAndGet(messages.size());
				splitJoinService.split(messages);
			}
		});

		t = System.currentTimeMillis()-t;
		if (logger.isDebugEnabled()) logger.debug("Dispatching " + messagesCounter.get() + " messages is done in " + t + " ms");
		if (messages.size() > messagesCounter.get()){
			logger.warn((messages.size() - messagesCounter.get()) + " messages were not being dispatched, since unknown type");
		}
 	}

	private <T extends Serializable> void dispatch(List<? extends Serializable> messages, Class<T> messageClass, Handler<T> handler){
		@SuppressWarnings("unchecked")
		List<T> filteredMessages = (List<T>) filter(Matchers.instanceOf(messageClass), messages);
		if (!messages.isEmpty()){
			handler.handle(filteredMessages);
		}
	}

	private interface Handler<T>{
		public void handle(List<T> messages);
	}
}
