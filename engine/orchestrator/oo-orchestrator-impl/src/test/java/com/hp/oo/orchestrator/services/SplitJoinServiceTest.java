package com.hp.oo.orchestrator.services;

import com.hp.oo.engine.queue.entities.ExecStatus;
import com.hp.oo.engine.queue.entities.ExecutionMessage;
import com.hp.oo.engine.queue.entities.ExecutionMessageConverter;
import com.hp.oo.engine.queue.services.QueueDispatcherService;
import com.hp.oo.enginefacade.execution.EndBranchDataContainer;
import com.hp.oo.internal.sdk.execution.Execution;
import com.hp.oo.internal.sdk.execution.OOContext;
import com.hp.oo.orchestrator.entities.BranchContexts;
import com.hp.oo.orchestrator.entities.FinishedBranch;
import com.hp.oo.orchestrator.entities.SplitMessage;
import com.hp.oo.orchestrator.entities.SuspendedExecution;
import com.hp.oo.orchestrator.repositories.FinishedBranchRepository;
import com.hp.oo.orchestrator.repositories.SuspendedExecutionsRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.lambdaj.Lambda.having;
import static ch.lambdaj.Lambda.on;
import static ch.lambdaj.Lambda.select;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.any;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class SplitJoinServiceTest {
	@InjectMocks
	private SplitJoinService splitJoinService = new SplitJoinServiceImpl();

	@Mock
	private SuspendedExecutionsRepository suspendedExecutionsRepository;

	@Mock
	private FinishedBranchRepository finishedBranchRepository;

	@Mock
	private QueueDispatcherService queueDispatcherService;

	@Mock
	private ExecutionMessageConverter converter;

	@Captor
	private ArgumentCaptor<List<ExecutionMessage>> queueDispatcherDispatchCaptor;

	@Captor
	private ArgumentCaptor<List<SuspendedExecution>> suspendedExecutionsSaveCaptor;

	@Captor
	private ArgumentCaptor<SuspendedExecution> suspendedExecutionsSingleSaveCaptor;

	@Captor
	private ArgumentCaptor<Execution> converterCaptor;

	@Configuration
	static class EmptyConfig {
	}

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void triggerChildrenSplitTest() {
		String splitId = UUID.randomUUID().toString();
		SplitMessage splitMessage = createSplitMessage(splitId);

		splitJoinService.split(Arrays.asList(splitMessage));
		Mockito.verify(queueDispatcherService).dispatch(queueDispatcherDispatchCaptor.capture());
		List<ExecutionMessage> argument = queueDispatcherDispatchCaptor.getValue();

		List<ExecutionMessage> branchMessages = select(argument, having(on(ExecutionMessage.class).getStatus(), is(ExecStatus.PENDING)));

		assertThat("exactly one branch should be triggered", branchMessages.size(), is(1));
		// validate the branch ExecutionMessage has the proper payload
		Mockito.verify(converter).createPayload(splitMessage.getChildren().get(0));
	}

	@Test
	public void suspendParentSplitTest() {
		String splitId = UUID.randomUUID().toString();
		SplitMessage splitMessage = createSplitMessage(splitId);

		splitJoinService.split(Arrays.asList(splitMessage));
		Mockito.verify(suspendedExecutionsRepository).save(suspendedExecutionsSaveCaptor.capture());
		List<SuspendedExecution> value = suspendedExecutionsSaveCaptor.getValue();

		assertThat("exactly one suspended entity must be created", value.size(), is(1));
		assertThat("suspended execution entity created with incorrect split id", value.get(0).getSplitId(), is(splitId));
		assertThat("suspended entity has incorrect execution object", value.get(0).getExecutionObj(), is(splitMessage.getParent()));
	}

	@Test(expected = IllegalArgumentException.class)
	public void nullParamSplitTest() {
		splitJoinService.split(null);
	}

	// *******************************
	// * SplitJoinService.endBranch() *
	// *******************************
	@Test
	public void persistBranchToDbEndBranchTest() {
		String splitId = UUID.randomUUID().toString();
		Execution execution = Mockito.mock(Execution.class);
		Mockito.when(execution.getSplitId()).thenReturn(splitId);
		Mockito.when(execution.getPosition()).thenReturn(null);

		Mockito.when(suspendedExecutionsRepository.findBySplitIdIn(Arrays.asList(splitId))).thenReturn(Arrays.asList(createSuspendedExecution(splitId, 2)));

		splitJoinService.endBranch(Arrays.asList(execution));

		Mockito.verify(finishedBranchRepository).save((FinishedBranch) any());
	}

	@Test
	public void missingSuspendedExecutionEndBranchTest() {
		String splitId1 = UUID.randomUUID().toString();
		String splitId2 = UUID.randomUUID().toString();
		Execution branch1 = Mockito.mock(Execution.class);
		Execution branch2 = Mockito.mock(Execution.class);
		Mockito.when(branch1.getSplitId()).thenReturn(splitId1);
		Mockito.when(branch2.getSplitId()).thenReturn(splitId2);
		Mockito.when(branch1.getPosition()).thenReturn(null);
		Mockito.when(branch2.getPosition()).thenReturn(null);

		// find only a a partial amount of the required suspended execution entities
		Mockito.when(suspendedExecutionsRepository.findBySplitIdIn(Arrays.asList(splitId1, splitId2))).thenReturn(Arrays.asList(createSuspendedExecution(splitId1, 1)));

		// must not throw an exception
		splitJoinService.endBranch(Arrays.asList(branch1, branch2));
	}

	@Test(expected = IllegalArgumentException.class)
	public void notNullPositionEndBranchTest() {
		Execution branch = Mockito.mock(Execution.class);
		Mockito.when(branch.getPosition()).thenReturn(-1L);
		splitJoinService.endBranch(Arrays.asList(branch));
	}

	@Test(expected = IllegalArgumentException.class)
	public void nullParamEndBranchTest() {
		splitJoinService.endBranch(null);
	}

	// ***************************************
	// * SplitJoinService.joinFinishedSplits() *
	// ***************************************
	@Test
	public void triggerParentJoinFinishedSplitsTest() {
		String splitId = UUID.randomUUID().toString();
		SuspendedExecution suspendedExecution = createSuspendedExecution(splitId, 1);
		OOContext context = new OOContext();
		context.put("someData", "1", false);

		suspendedExecution.getFinishedBranches().add(createFinishedBranch(splitId, splitId + "1", context, new HashMap<String, Serializable>()));
		Mockito.when(suspendedExecutionsRepository.findFinishedSuspendedExecutions(any(Pageable.class))).thenReturn(Arrays.asList(suspendedExecution));

		int joinedSplits = splitJoinService.joinFinishedSplits(1);
		assertThat(joinedSplits, is(1));

		Mockito.verify(converter).createPayload(suspendedExecution.getExecutionObj());
		Mockito.verify(queueDispatcherService).dispatch(queueDispatcherDispatchCaptor.capture());

		List<ExecutionMessage> argument = queueDispatcherDispatchCaptor.getValue();
		assertThat("exactly one execution should be dispatched", argument.size(), is(1));
		assertThat("parent sent back to the queue should be in status pending", argument.get(0).getStatus(), is(ExecStatus.PENDING));
		assertThat("ExecutionMessage has a different msg id then the execution object's execution id", argument.get(0).getMsgId(), is(suspendedExecution.getExecutionObj().getExecutionId()));
	}

	@Test
	public void deleteParentJoinFinishedSplitsTest() {
		String splitId = UUID.randomUUID().toString();
		SuspendedExecution suspendedExecution = createSuspendedExecution(splitId, 1);
		suspendedExecution.getFinishedBranches().add(createFinishedBranch(splitId, splitId + "1", new OOContext(), new HashMap<String, Serializable>()));
		Mockito.when(suspendedExecutionsRepository.findFinishedSuspendedExecutions(any(Pageable.class))).thenReturn(Arrays.asList(suspendedExecution));

		int joinedSplits = splitJoinService.joinFinishedSplits(1);
		assertThat(joinedSplits, is(1));

		Mockito.verify(suspendedExecutionsRepository).delete(Arrays.asList(suspendedExecution));
	}

	@Test
	public void insertBranchesToParentJoinFinishedSplitsTest() {
		String splitId = UUID.randomUUID().toString();
		SuspendedExecution suspendedExecution = createSuspendedExecution(splitId, 1);
		OOContext context = new OOContext();
		Map<String, Serializable> branchSystemContext = new HashMap<>();
		context.put("haha", "lala", false);

		suspendedExecution.getFinishedBranches().add(createFinishedBranch(splitId, splitId + "1", context, branchSystemContext));
		Mockito.when(suspendedExecutionsRepository.findFinishedSuspendedExecutions(any(Pageable.class))).thenReturn(Arrays.asList(suspendedExecution));

		int joinedSplits = splitJoinService.joinFinishedSplits(1);
		assertThat(joinedSplits, is(1));

		Mockito.verify(converter).createPayload(converterCaptor.capture());
		Execution value = converterCaptor.getValue();

		List<EndBranchDataContainer> finishedChildContexts = value.getFinishedChildBranchesData();

		Map<String, OOContext> ooContexts = suspendedExecution.getFinishedBranches().get(0).getBranchContexts().getContexts();
		Map<String, Serializable> systemContext = suspendedExecution.getFinishedBranches().get(0).getBranchContexts().getSystemContext();
		assertThat("parent execution must contain children maps", finishedChildContexts, is(Arrays.asList(
				new EndBranchDataContainer(ooContexts, systemContext, null))));
	}

	// private helpers
	private Execution createExecution(Long id) {
		Execution res = new Execution(null, null, null);
		res.setExecutionId(id.toString());
		return res;
	}

	private SplitMessage createSplitMessage(String splitId) {
		return new SplitMessage(splitId, createExecution(1L), Arrays.asList(createExecution(2L)));
	}

	private SuspendedExecution createSuspendedExecution(String splitId, int numOfBranches) {
		return new SuspendedExecution(1 + "", splitId, numOfBranches, createExecution(1L));
	}

	private FinishedBranch createFinishedBranch(String splitId, String branchId, OOContext context, Map<String, Serializable> systemContext) {
		HashMap<String, OOContext> contexts = new HashMap<>();
		contexts.put("haha", context);
		return new FinishedBranch(null, branchId, splitId, null, new BranchContexts(false, contexts, systemContext));
	}
}