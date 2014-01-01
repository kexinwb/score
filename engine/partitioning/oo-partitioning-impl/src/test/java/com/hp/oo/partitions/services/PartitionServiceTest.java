package com.hp.oo.partitions.services;

import com.hp.oo.partitions.entities.PartitionGroup;
import com.hp.oo.partitions.repositories.PartitionGroupRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.Mockito.*;
import static org.fest.assertions.Assertions.assertThat;


/**
 * Date: 4/23/12
 *
 * @author Dima Rassin
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class PartitionServiceTest {
	private static final String TABLE_NAME = "TEST_TABLE";
	private static final int GROUP_SIZE = 4;
	private static final long TIMEOUT = 10000L;
	private static final long MAX_RECORDS = 1000L;

	private static PartitionGroup partitionGroup = new PartitionGroup(TABLE_NAME, GROUP_SIZE, TIMEOUT, MAX_RECORDS);

	@Autowired
	private PartitionService partitionService;

	// Mocks
	@Autowired private PartitionGroupRepository repository;
	@Autowired private TransactionTemplate transactionTemplate;
	@Autowired private JdbcTemplate jdbcTemplate;

	@Before
	public void before(){
		// reset all mocks
		reset(
				repository,
				transactionTemplate,
				jdbcTemplate
		);
	}

	@Test
	public void partitionRollFalse(){
		when(repository.lock(TABLE_NAME)).thenReturn(1);
		when(repository.findByName(TABLE_NAME)).thenReturn(partitionGroup);
		boolean rollResult = partitionService.rollPartitions(TABLE_NAME);
		assertThat(rollResult).isFalse();
	}

	//@Test
	public void partitionRollOnTimeout(){
		partitionGroup.setLastRollTime(0);
		when(repository.findByName(TABLE_NAME)).thenReturn(partitionGroup);
		boolean rollResult = partitionService.rollPartitions(TABLE_NAME);
		assertThat(rollResult).isTrue();
	}

	@Configuration
	static class Configurator{
		@Bean public PartitionService createPartitionManager(){
			return new PartitionServiceImpl();
		}

		@Bean(name = TABLE_NAME) public PartitionTemplate template(){
			return new PartitionTemplateImpl();
		}

		@Bean public PartitionGroupRepository repository(){
			return mock(PartitionGroupRepository.class);
		}

		@Bean public JdbcTemplate jdbcTemplate(){
			return mock(JdbcTemplate.class);
		}

		@Bean public TransactionTemplate createTransactionTemplate(){
			TransactionTemplate template = mock(TransactionTemplate.class);
			//noinspection unchecked
			when(template.<PartitionGroup>execute(any(TransactionCallback.class)))
					.thenReturn(partitionGroup);
			return template;
		}

        @Bean public PartitionUtils partitionUtils() {
            return new PartitionUtils();
        }
	}
}
