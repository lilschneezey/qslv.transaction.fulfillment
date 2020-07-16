package qslv.transaction.fulfillment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import qslv.common.TimedResponse;
import qslv.common.kafka.ResponseMessage;
import qslv.common.kafka.TraceableMessage;
import qslv.data.Account;
import qslv.data.OverdraftInstruction;
import qslv.transaction.request.CommitReservationRequest;
import qslv.transaction.request.ReservationRequest;
import qslv.transaction.request.TransactionRequest;
import qslv.transaction.request.TransferAndTransactRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.CommitReservationResponse;
import qslv.transaction.response.ReservationResponse;
import qslv.transaction.response.TransactionResponse;
import qslv.transaction.response.TransferAndTransactReponse;
import qslv.util.Random;

@SpringBootTest
@Import(value = { TestConfig.class })
@DirtiesContext
@EmbeddedKafka(partitions = 1,topics = { "transaction.request.queue", "transaction.reply.queue" })
@ActiveProfiles("test")
public class Unit_EmbeddedKakfa_OverDraft {
	private static String request_topic = "transaction.request.queue";
	private static String reply_topic = "transaction.reply.queue";
	@Autowired EmbeddedKafkaBroker embeddedKafka;
	@Autowired ConfigProperties configProperties;
	
	@Autowired JdbcDao jdbcDao;
	@Mock JdbcTemplate jdbcTemplate;
	
	@Autowired TransactionDao transactionDao;
	@Mock RestTemplateProxy restTemplateProxy;
	
	@BeforeEach
	public void init() {
		jdbcDao.setJdbcTemplate(jdbcTemplate);
		transactionDao.setRestTemplateProxy(restTemplateProxy);
		configProperties.setKafkaTransactionRequestQueue(request_topic);
		configProperties.setKafkaTransactionReplyQueue(reply_topic);
	}
	
	@Autowired
	Producer<String, TraceableMessage<TransactionRequest>> producer;
	@Autowired
	Consumer<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> consumer;
	
	@Test
	void test_processTransaction_success() {
		//new

		// -Setup input output----------------
		ResponseEntity<TimedResponse<TransactionResponse>> response = setup_responseEntity();
		List<OverdraftInstruction> instructions = setup_instructions();
		ResponseEntity<TimedResponse<ReservationResponse>> reservation = setup_reservation();
		ResponseEntity<TimedResponse<TransferAndTransactReponse>> transfer = setup_transfer();
		ResponseEntity<TimedResponse<CommitReservationResponse>> commit = setup_commit();
		
		// -Prepare----------------------------
		doReturn(response).when(restTemplateProxy).exchange(eq(configProperties.getPostTransactionUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<TransactionRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransactionResponse>>>any());
		doReturn(instructions).when(jdbcTemplate).query(eq(JdbcDao.getOverdraftInstructions_sql), ArgumentMatchers.<RowMapper<OverdraftInstruction>>any(), anyString());
		doReturn(reservation).when(restTemplateProxy).exchange(eq(configProperties.getPostReservationUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<ReservationRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<ReservationResponse>>>any());
		doReturn(transfer).when(restTemplateProxy).exchange(eq(configProperties.getTransferAndTransactUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<TransferAndTransactRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransferAndTransactReponse>>>any());
		doReturn(commit).when(restTemplateProxy).exchange(eq(configProperties.getCommitReservationUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<CommitReservationRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<CommitReservationResponse>>>any());

		TraceableMessage<TransactionRequest> message = setup_traceable();
				
		// -Execute----------------
		producer.send(new ProducerRecord<>(request_topic, message.getPayload().getAccountNumber(), message));
		producer.flush();
		ConsumerRecord<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> record 
			= KafkaTestUtils.getSingleRecord(consumer, reply_topic, 2000L);

		// -Verify----------------
		assertNotNull(record);
		assertNotNull(record.value().getPayload());
		assertNotNull(record.value().getPayload().getRequest());
		assertNotNull(record.value().getPayload().getResponse());
		
		TraceableMessage<?> trace = record.value();
		assertEquals(trace.getBusinessTaxonomyId(), message.getBusinessTaxonomyId());
		assertEquals(trace.getCorrelationId(), message.getCorrelationId());
		assertNotNull(trace.getMessageCompletionTime());
		assertEquals(trace.getMessageCreationTime(), message.getMessageCreationTime());
		assertEquals(trace.getProducerAit(), message.getProducerAit());
		
		ResponseMessage<?,?> responseMessage = record.value().getPayload();
		assertEquals(ResponseMessage.SUCCESS, responseMessage.getStatus());

		TransactionRequest tranRequest = record.value().getPayload().getRequest();
		assertEquals(tranRequest.getAccountNumber(), message.getPayload().getAccountNumber());
		assertEquals(tranRequest.getDebitCardNumber(), message.getPayload().getDebitCardNumber());
		assertEquals(tranRequest.getRequestUuid(), message.getPayload().getRequestUuid());
		assertEquals(tranRequest.getTransactionAmount(), message.getPayload().getTransactionAmount());
		assertEquals(tranRequest.getTransactionMetaDataJson(), message.getPayload().getTransactionMetaDataJson());
		assertEquals(tranRequest.isAuthorizeAgainstBalance(), message.getPayload().isAuthorizeAgainstBalance());
		assertEquals(tranRequest.isProtectAgainstOverdraft(), message.getPayload().isProtectAgainstOverdraft());
		
		TransactionResponse tranResponse = record.value().getPayload().getResponse();
		assertEquals(tranResponse.getStatus(), TransactionResponse.SUCCESS);
		assertEquals(tranResponse.getTransactions().size(), 5);

		assertTransaction(tranResponse.getTransactions().get(0), response.getBody().getPayload().getTransactions().get(0));
		assertTransaction(tranResponse.getTransactions().get(1), reservation.getBody().getPayload().getResource());
		assertTransaction(tranResponse.getTransactions().get(2), transfer.getBody().getPayload().getTransactions().get(0));
		assertTransaction(tranResponse.getTransactions().get(3), transfer.getBody().getPayload().getTransactions().get(1));
		assertTransaction(tranResponse.getTransactions().get(4), commit.getBody().getPayload().getResource());

	}
	
	void assertTransaction(TransactionResource actual, TransactionResource expected) {
		assertEquals(actual.getAccountNumber(), expected.getAccountNumber());
		assertEquals(actual.getDebitCardNumber(), expected.getDebitCardNumber());
		//assertEquals(actual.getInsertTimestamp(), expected.getInsertTimestamp());
		assertEquals(actual.getRequestUuid(), expected.getRequestUuid());
		assertEquals(actual.getReservationUuid(), expected.getReservationUuid());
		assertEquals(actual.getRunningBalanceAmount(), expected.getRunningBalanceAmount());
		assertEquals(actual.getTransactionAmount(), expected.getTransactionAmount());
		assertEquals(actual.getTransactionMetaDataJson(), expected.getTransactionMetaDataJson());
		assertEquals(actual.getTransactionTypeCode(), expected.getTransactionTypeCode());
		assertEquals(actual.getTransactionUuid(), expected.getTransactionUuid());
	}

	@Test
	void test_processTransaction_jdbcThrows() {
		//new

		// -Setup input output----------------
		ResponseEntity<TimedResponse<TransactionResponse>> response = setup_responseEntity();
		
		// -Prepare----------------------------
		doReturn(response).when(restTemplateProxy).exchange(eq(configProperties.getPostTransactionUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<TransactionRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransactionResponse>>>any());
		doThrow(new RuntimeException()).when(jdbcTemplate).query(eq(JdbcDao.getOverdraftInstructions_sql), ArgumentMatchers.<RowMapper<OverdraftInstruction>>any(), anyString());

		TraceableMessage<TransactionRequest> message = setup_traceable();
				
		// -Execute----------------
		producer.send(new ProducerRecord<>(request_topic, message.getPayload().getAccountNumber(), message));
		producer.flush();
		ConsumerRecord<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> record 
			= KafkaTestUtils.getSingleRecord(consumer, reply_topic, 2000L);

		// -Verify----------------		
		assertNotNull(record);
		assertNotNull(record.value().getPayload());
		assertNotNull(record.value().getPayload().getRequest());
		
		TraceableMessage<?> trace = record.value();
		assertEquals(trace.getBusinessTaxonomyId(), message.getBusinessTaxonomyId());
		assertEquals(trace.getCorrelationId(), message.getCorrelationId());
		assertEquals(trace.getMessageCreationTime(), message.getMessageCreationTime());
		assertEquals(trace.getProducerAit(), message.getProducerAit());
		
		ResponseMessage<?,?> responseMessage = record.value().getPayload();
		assertEquals(ResponseMessage.INTERNAL_ERROR, responseMessage.getStatus());

	}
	
	@Test
	void test_processTransaction_reservationThrows() {
		//new

		// -Setup input output----------------
		ResponseEntity<TimedResponse<TransactionResponse>> response = setup_responseEntity();
		List<OverdraftInstruction> instructions = setup_instructions();

		// -Prepare----------------------------
		doReturn(response).when(restTemplateProxy).exchange(eq(configProperties.getPostTransactionUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<TransactionRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransactionResponse>>>any());
		doReturn(instructions).when(jdbcTemplate).query(eq(JdbcDao.getOverdraftInstructions_sql), ArgumentMatchers.<RowMapper<OverdraftInstruction>>any(), anyString());
		doThrow(new RuntimeException()).when(restTemplateProxy).exchange(eq(configProperties.getPostReservationUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<ReservationRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<ReservationResponse>>>any());

		TraceableMessage<TransactionRequest> message = setup_traceable();
				
		// -Execute----------------
		producer.send(new ProducerRecord<>(request_topic, message.getPayload().getAccountNumber(), message));
		producer.flush();
		ConsumerRecord<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> record 
			= KafkaTestUtils.getSingleRecord(consumer, reply_topic, 2000L);

		// -Verify----------------		
		assertNotNull(record);
		assertNotNull(record.value().getPayload());
		assertNotNull(record.value().getPayload().getRequest());
		
		TraceableMessage<?> trace = record.value();
		assertEquals(trace.getBusinessTaxonomyId(), message.getBusinessTaxonomyId());
		assertEquals(trace.getCorrelationId(), message.getCorrelationId());
		assertEquals(trace.getMessageCreationTime(), message.getMessageCreationTime());
		assertEquals(trace.getProducerAit(), message.getProducerAit());
		
		ResponseMessage<?,?> responseMessage = record.value().getPayload();
		assertEquals(ResponseMessage.INTERNAL_ERROR, responseMessage.getStatus());


	}
	
	@Test
	void test_processTransaction_transferThrows() {
		//new

		// -Setup input output----------------
		ResponseEntity<TimedResponse<TransactionResponse>> response = setup_responseEntity();
		List<OverdraftInstruction> instructions = setup_instructions();
		ResponseEntity<TimedResponse<ReservationResponse>> reservation = setup_reservation();

		// -Prepare----------------------------
		doReturn(response).when(restTemplateProxy).exchange(eq(configProperties.getPostTransactionUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<TransactionRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransactionResponse>>>any());
		doReturn(instructions).when(jdbcTemplate).query(eq(JdbcDao.getOverdraftInstructions_sql), ArgumentMatchers.<RowMapper<OverdraftInstruction>>any(), anyString());
		doReturn(reservation).when(restTemplateProxy).exchange(eq(configProperties.getPostReservationUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<ReservationRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<ReservationResponse>>>any());
		doThrow(new RuntimeException()).when(restTemplateProxy).exchange(eq(configProperties.getTransferAndTransactUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<TransferAndTransactRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransferAndTransactReponse>>>any());
		TraceableMessage<TransactionRequest> message = setup_traceable();
				
		// -Execute----------------
		producer.send(new ProducerRecord<>(request_topic, message.getPayload().getAccountNumber(), message));
		producer.flush();
		ConsumerRecord<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> record 
			= KafkaTestUtils.getSingleRecord(consumer, reply_topic, 2000L);

		// -Verify----------------		
		assertNotNull(record);
		assertNotNull(record.value().getPayload());
		assertNotNull(record.value().getPayload().getRequest());
		
		TraceableMessage<?> trace = record.value();
		assertEquals(trace.getBusinessTaxonomyId(), message.getBusinessTaxonomyId());
		assertEquals(trace.getCorrelationId(), message.getCorrelationId());
		assertEquals(trace.getMessageCreationTime(), message.getMessageCreationTime());
		assertEquals(trace.getProducerAit(), message.getProducerAit());
		
		ResponseMessage<?,?> responseMessage = record.value().getPayload();
		assertEquals(ResponseMessage.INTERNAL_ERROR, responseMessage.getStatus());

	}
	
	@Test
	void test_processTransaction_commitThrows() {
		//new

		// -Setup input output----------------
		ResponseEntity<TimedResponse<TransactionResponse>> response = setup_responseEntity();
		List<OverdraftInstruction> instructions = setup_instructions();
		ResponseEntity<TimedResponse<ReservationResponse>> reservation = setup_reservation();
		ResponseEntity<TimedResponse<TransferAndTransactReponse>> transfer = setup_transfer();

		// -Prepare----------------------------
		doReturn(response).when(restTemplateProxy).exchange(eq(configProperties.getPostTransactionUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<TransactionRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransactionResponse>>>any());
		doReturn(instructions).when(jdbcTemplate).query(eq(JdbcDao.getOverdraftInstructions_sql), ArgumentMatchers.<RowMapper<OverdraftInstruction>>any(), anyString());
		doReturn(reservation).when(restTemplateProxy).exchange(eq(configProperties.getPostReservationUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<ReservationRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<ReservationResponse>>>any());
		doReturn(transfer).when(restTemplateProxy).exchange(eq(configProperties.getTransferAndTransactUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<TransferAndTransactRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransferAndTransactReponse>>>any());
		doThrow(new RuntimeException()).when(restTemplateProxy).exchange(eq(configProperties.getCommitReservationUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<CommitReservationRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<CommitReservationResponse>>>any());
		
		TraceableMessage<TransactionRequest> message = setup_traceable();
				
		// -Execute----------------
		producer.send(new ProducerRecord<>(request_topic, message.getPayload().getAccountNumber(), message));
		producer.flush();
		ConsumerRecord<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> record 
			= KafkaTestUtils.getSingleRecord(consumer, reply_topic, 2000L);

		// -Verify----------------		
		assertNotNull(record);
		assertNotNull(record.value().getPayload());
		assertNotNull(record.value().getPayload().getRequest());
		
		TraceableMessage<?> trace = record.value();
		assertEquals(trace.getBusinessTaxonomyId(), message.getBusinessTaxonomyId());
		assertEquals(trace.getCorrelationId(), message.getCorrelationId());
		assertEquals(trace.getMessageCreationTime(), message.getMessageCreationTime());
		assertEquals(trace.getProducerAit(), message.getProducerAit());
		
		ResponseMessage<?,?> responseMessage = record.value().getPayload();
		assertEquals(ResponseMessage.INTERNAL_ERROR, responseMessage.getStatus());

	}
	

	
	TraceableMessage<TransactionRequest> setup_traceable() {
		TraceableMessage<TransactionRequest> message = new TraceableMessage<>();
		message.setPayload(setup_request());
		message.setBusinessTaxonomyId("234234234234");
		message.setCorrelationId("328942834234j23k4");
		message.setMessageCreationTime(LocalDateTime.now());
		message.setProducerAit("27834");
		return message;
	}
	TransactionRequest setup_request() {
		TransactionRequest request = new TransactionRequest();
		request.setAccountNumber("12345634579");
		request.setRequestUuid(UUID.randomUUID());
		request.setTransactionMetaDataJson("{}");
		request.setDebitCardNumber("27834728934729374");
		request.setAuthorizeAgainstBalance(true);
		request.setProtectAgainstOverdraft(true);
		request.setTransactionAmount(782374974L);
		return request;
	}
	
	ResponseEntity<TimedResponse<TransactionResponse>> setup_responseEntity() {
		return new ResponseEntity<TimedResponse<TransactionResponse>>(new TimedResponse<>(123456L, setup_response()), HttpStatus.CREATED);
	}
	ResponseEntity<TimedResponse<TransactionResponse>> setup_failedResponseEntity() {
		return new ResponseEntity<TimedResponse<TransactionResponse>>(new TimedResponse<>(234567L, setup_response()), HttpStatus.INTERNAL_SERVER_ERROR);
	}
	TransactionResponse setup_response() {
		TransactionResource tx = randomTransaction();
		tx.setTransactionTypeCode(TransactionResource.REJECTED_TRANSACTION);
	
		TransactionResponse resourceResponse = new TransactionResponse(TransactionResponse.INSUFFICIENT_FUNDS, tx);
		resourceResponse.getTransactions().get(0).setAccountNumber("12345679");
		resourceResponse.getTransactions().get(0).setDebitCardNumber("7823478239467");
		resourceResponse.setStatus(TransactionResponse.INSUFFICIENT_FUNDS);
		return resourceResponse;
	}
	
	List<OverdraftInstruction> setup_instructions() {
		ArrayList<OverdraftInstruction> list = new ArrayList<>();
		OverdraftInstruction instruction = new OverdraftInstruction();
		instruction.setEffectiveEnd(LocalDateTime.now().plusYears(1L));
		instruction.setEffectiveStart(LocalDateTime.now().minusYears(1L));
		instruction.setInstructionLifecycleStatus("EF");
		instruction.setOverdraftAccount(new Account());
		instruction.getOverdraftAccount().setAccountLifeCycleStatus("EF");
		instruction.getOverdraftAccount().setAccountNumber("Account #1");
		list.add(instruction);
	
		instruction = new OverdraftInstruction();
		instruction.setEffectiveEnd(LocalDateTime.now().plusYears(1L));
		instruction.setEffectiveStart(LocalDateTime.now().minusYears(1L));
		instruction.setInstructionLifecycleStatus("EF");
		instruction.setOverdraftAccount(new Account());
		instruction.getOverdraftAccount().setAccountLifeCycleStatus("EF");
		instruction.getOverdraftAccount().setAccountNumber("Account #2");
		list.add(instruction);
		return list;
	}

	private ResponseEntity<TimedResponse<ReservationResponse>> setup_reservation() {
		ReservationResponse reservation = new ReservationResponse();
		reservation.setStatus(ReservationResponse.SUCCESS);
		reservation.setResource(randomTransaction());
		reservation.getResource().setTransactionTypeCode(TransactionResource.RESERVATION);
		TimedResponse<ReservationResponse> response = new TimedResponse<ReservationResponse>(reservation);
		response.setServiceTimeElapsed(278429L);
		ResponseEntity<TimedResponse<ReservationResponse>> entity = new  ResponseEntity<TimedResponse<ReservationResponse>>(response, HttpStatus.CREATED);
		return entity;
	}

	private ResponseEntity<TimedResponse<CommitReservationResponse>> setup_commit() {
		CommitReservationResponse commit = new CommitReservationResponse();
		commit.setStatus(CommitReservationResponse.SUCCESS);
		commit.setResource(randomTransaction());
		commit.getResource().setTransactionTypeCode(TransactionResource.RESERVATION_COMMIT);
		TimedResponse<CommitReservationResponse> response = new TimedResponse<CommitReservationResponse>(commit);
		response.setServiceTimeElapsed(7387834L);
		ResponseEntity<TimedResponse<CommitReservationResponse>> entity = new ResponseEntity<TimedResponse<CommitReservationResponse>>(response, HttpStatus.CREATED);
		return entity;
	}

	private ResponseEntity<TimedResponse<TransferAndTransactReponse>> setup_transfer() {
		TransferAndTransactReponse transfer = new TransferAndTransactReponse();
		transfer.setStatus(TransferAndTransactReponse.SUCCESS);
		ArrayList<TransactionResource> list = new ArrayList<>();
		list.add(randomTransaction());
		list.add(randomTransaction());
		transfer.setTransactions(list);
		TimedResponse<TransferAndTransactReponse> response = new TimedResponse<TransferAndTransactReponse>(transfer);
		response.setServiceTimeElapsed(72384L);
		ResponseEntity<TimedResponse<TransferAndTransactReponse>> entity = new ResponseEntity<TimedResponse<TransferAndTransactReponse>>(response, HttpStatus.CREATED);
		return entity;
	}
	private TransactionResource randomTransaction() {
		TransactionResource tran = new TransactionResource();
		tran.setAccountNumber(Random.randomDigits(12));
		tran.setDebitCardNumber(Random.randomDigits(16));
		tran.setInsertTimestamp(Timestamp.from(Random.randomLocalDateTime().toInstant(ZoneOffset.UTC)));
		tran.setRequestUuid(UUID.randomUUID());
		tran.setReservationUuid(UUID.randomUUID());
		tran.setRunningBalanceAmount(Random.randomLong(-99999999L,9999999L));
		tran.setTransactionAmount(Random.randomLong(-99999999L, 9999999L));
		tran.setTransactionMetaDataJson(Random.randomString(150));
		tran.setTransactionTypeCode(TransactionResource.NORMAL);
		tran.setTransactionUuid(UUID.randomUUID());
		return tran;
	}
}
