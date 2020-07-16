package qslv.transaction.fulfillment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.JavaType;

import qslv.common.TimedResponse;
import qslv.common.kafka.JacksonAvroDeserializer;
import qslv.common.kafka.JacksonAvroSerializer;
import qslv.common.kafka.ResponseMessage;
import qslv.common.kafka.TraceableMessage;
import qslv.transaction.request.TransactionRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.TransactionResponse;

@SpringBootTest
@Import(value = { TestConfig.class })
@DirtiesContext
@EmbeddedKafka(partitions = 1,topics = { "transaction.request.queue", "transaction.reply.queue" })
@ActiveProfiles("test")
public class Unit_EmbeddedKakfa {
	private static String request_topic = "transaction.request.queue";
	private static String reply_topic = "transaction.reply.queue";
	@Autowired EmbeddedKafkaBroker embeddedKafka;
	@Autowired KafkaTransactionListener kafkaCancelListener;
	@Autowired KafkaListenerConfig kafkaListenerConfig;	
	@Autowired TransactionDao transactionDao;
	@Autowired ConfigProperties configProperties;
	
	@Mock
	RestTemplateProxy restTemplateProxy;
	
	@BeforeEach
	public void init() {
		transactionDao.setRestTemplateProxy(restTemplateProxy);	
		configProperties.setKafkaTransactionRequestQueue(request_topic);
		configProperties.setKafkaTransactionReplyQueue(reply_topic);
	}
	
	//----------------
	// Kafka Reconfigure
	//----------------
	private Map<String,Object> embeddedConfig() {
		HashMap<String, Object> props = new HashMap<>();
		props.put("bootstrap.servers", embeddedKafka.getBrokersAsString());
		props.put("group.id", "Service.listener.consumer");
		props.put("schema.registry.url", "http://localhost:8081");
		return props;
	}
	
	@Test
	void test_processTransaction_success() {

		// -Setup input output----------------
		Producer<String, TraceableMessage<TransactionRequest>> producer = buildProducer();
		Consumer<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> consumer = buildConsumer();
		ResponseEntity<TimedResponse<TransactionResponse>> response = setup_responseEntity();
		
		// -Prepare----------------------------
		doReturn(response).when(restTemplateProxy).exchange(anyString(), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<TransactionRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransactionResponse>>>any());
		//doNothing().when(fulfillmentController).fulfillCancel(any(), any());
		
		TraceableMessage<TransactionRequest> message = setup_traceable();
		
		// -Execute----------------
		producer.send(new ProducerRecord<>(request_topic, message.getPayload().getAccountNumber(), message));
		producer.flush();
		embeddedKafka.consumeFromAnEmbeddedTopic(consumer, reply_topic);
		ConsumerRecord<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> record 
			= KafkaTestUtils.getSingleRecord(consumer, reply_topic, 2000L);

		// -Verify----------------
		verify(restTemplateProxy).exchange(anyString(), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<TransactionRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransactionResponse>>>any());
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
		assertNull(responseMessage.getMessage());

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
		assertEquals(tranResponse.getTransactions().size(), 1);
		
		TransactionResource transaction = tranResponse.getTransactions().get(0);
		assertEquals(transaction.getAccountNumber(), response.getBody().getPayload().getTransactions().get(0).getAccountNumber());
		assertEquals(transaction.getDebitCardNumber(), response.getBody().getPayload().getTransactions().get(0).getDebitCardNumber());
		assertEquals(transaction.getInsertTimestamp(), response.getBody().getPayload().getTransactions().get(0).getInsertTimestamp());
		assertEquals(transaction.getRequestUuid(), response.getBody().getPayload().getTransactions().get(0).getRequestUuid());
		assertEquals(transaction.getReservationUuid(), response.getBody().getPayload().getTransactions().get(0).getReservationUuid());
		assertEquals(transaction.getRunningBalanceAmount(), response.getBody().getPayload().getTransactions().get(0).getRunningBalanceAmount());
		assertEquals(transaction.getTransactionAmount(), response.getBody().getPayload().getTransactions().get(0).getTransactionAmount());
		assertEquals(transaction.getTransactionMetaDataJson(), response.getBody().getPayload().getTransactions().get(0).getTransactionMetaDataJson());
		assertEquals(transaction.getTransactionTypeCode(), response.getBody().getPayload().getTransactions().get(0).getTransactionTypeCode());
		assertEquals(transaction.getTransactionUuid(), response.getBody().getPayload().getTransactions().get(0).getTransactionUuid());
	}

	//------------------------------------------------------
	// Mock producer and consumer to test end-to-end flow
	//------------------------------------------------------
	private Producer<String, TraceableMessage<TransactionRequest>> buildProducer() {
		Map<String, Object> configs = embeddedConfig();
		
    	JacksonAvroSerializer<TraceableMessage<TransactionRequest>> jas = new JacksonAvroSerializer<>();
		JavaType type = jas.getTypeFactory().constructParametricType(TraceableMessage.class, TransactionRequest.class);
    	jas.configure(configs, false, type);
		
		return new DefaultKafkaProducerFactory<>(configs, new StringSerializer(), jas ).createProducer();
	}
	private Consumer<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> buildConsumer() {
		Map<String, Object> configs = embeddedConfig();
		configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		configs.put("group.id", "somevalue");
		
    	JacksonAvroDeserializer<TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> jad = new JacksonAvroDeserializer<>();
    	jad.configure(configs);

		return new DefaultKafkaConsumerFactory<>(configs, new StringDeserializer(), jad).createConsumer();
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
		TransactionResource tx = new TransactionResource();
		tx.setAccountNumber("234235235");
		tx.setDebitCardNumber("2837428937492834");
		tx.setInsertTimestamp(Timestamp.from(LocalDateTime.now().toInstant(ZoneOffset.UTC)));
		tx.setRequestUuid(UUID.randomUUID());
		tx.setReservationUuid(UUID.randomUUID());
		tx.setRunningBalanceAmount(2738492L);
		tx.setTransactionAmount(9834534L);
		tx.setTransactionMetaDataJson("n;zjshfap8sydfasdupfausdif");
		tx.setTransactionTypeCode(TransactionResource.REJECTED_TRANSACTION);
		tx.setTransactionUuid(UUID.randomUUID());
		
		TransactionResponse resourceResponse = new TransactionResponse(TransactionResponse.SUCCESS, tx);
		resourceResponse.getTransactions().get(0).setAccountNumber("12345679");
		resourceResponse.getTransactions().get(0).setDebitCardNumber("7823478239467");
		resourceResponse.setStatus(TransactionResponse.SUCCESS);
		return resourceResponse;
	}
}
