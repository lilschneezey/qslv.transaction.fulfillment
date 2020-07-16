package qslv.transaction.fulfillment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;

import qslv.common.kafka.ResponseMessage;
import qslv.common.kafka.TraceableMessage;
import qslv.transaction.request.TransactionRequest;
import qslv.transaction.response.TransactionResponse;

@ExtendWith(MockitoExtension.class)
public class Unit_KafkaDao_produceResponse {
	KafkaDao kafkaDao = new KafkaDao();
	ConfigProperties config = new ConfigProperties();
	
	@Mock
	KafkaTemplate<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> transactionKafkaTemplate;
	@Mock
	ListenableFuture<SendResult<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>>> future;
	
	{
		config.setKafkaTransactionReplyQueue("reply.queue");
		kafkaDao.setConfig(config);
	}
	
	@BeforeEach
	public void setup() {
		kafkaDao.setTransactionKafkaTemplate(transactionKafkaTemplate);
	}
	
	TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>> setup_message() {
		TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>> message = 
				new TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>> ();
		
		message.setPayload(new ResponseMessage<TransactionRequest,TransactionResponse>());
		message.getPayload().setRequest(new TransactionRequest());
		message.getPayload().getRequest().setAccountNumber("2839420384902");
		
		return message;
	}
	
	@Test
	public void test_produceResponse_success() throws InterruptedException, ExecutionException {
		
		//-Setup---------------
		TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>> setup_message = setup_message();
		
		//-Prepare---------------
		ProducerRecord<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> producerRecord 
			= new ProducerRecord<>("mockTopicName", setup_message);
		
		SendResult<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> sendResult 
			= new SendResult<>(producerRecord, new RecordMetadata(new TopicPartition("mockTopic", 1), 1, 1, 1, 1L, 1, 1));
		
		doReturn(sendResult).when(future).get();
		doReturn(future).when(transactionKafkaTemplate).send(anyString(), anyString(), any());
	
		//-Execute----------------------------		
		kafkaDao.produceResponse(setup_message);
		
		//-Verify----------------------------		
		verify(future).get();
		ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
		verify(transactionKafkaTemplate).send(anyString(), arg.capture(), any());
		assertEquals( arg.getValue(), setup_message.getPayload().getRequest().getAccountNumber());
	}
	
	@Test
	public void test_produceResponse_throwsTransient() throws InterruptedException, ExecutionException, TimeoutException {
		
		//-Prepare---------------
		TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>> setup_message = setup_message();

		//-Prepare---------------
		doThrow(new InterruptedException()).when(future).get();
		doReturn(future).when(transactionKafkaTemplate).send(anyString(), anyString(), any());
		
		//--Execute--------------	
		assertThrows(TransientDataAccessResourceException.class, () -> {
			kafkaDao.produceResponse(setup_message);
		});
	}

}
