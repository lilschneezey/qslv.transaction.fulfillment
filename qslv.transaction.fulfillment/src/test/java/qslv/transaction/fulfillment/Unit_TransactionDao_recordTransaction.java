package qslv.transaction.fulfillment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.NonTransientDataAccessResourceException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;
import qslv.common.TimedResponse;
import qslv.common.kafka.TraceableMessage;
import qslv.transaction.request.TransactionRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.TransactionResponse;

@ExtendWith(MockitoExtension.class)
class Unit_TransactionDao_recordTransaction {

	@Mock
	RestTemplateProxy restTemplateProxy;
	
	ConfigProperties config = new ConfigProperties();
	TransactionDao transactionDao = new TransactionDao();
	RetryTemplate retryTemplate = new RetryTemplate() ;
	
	{
		SimpleRetryPolicy srp = new SimpleRetryPolicy();
		srp.setMaxAttempts(3);
		retryTemplate.setThrowLastExceptionOnExhausted(true);
		retryTemplate.setRetryPolicy(srp);
		transactionDao.setRetryTemplate(retryTemplate);
		config.setAitid("723842");
		config.setPostTransactionUrl("http://localhost:9091/CommitTransaction");
		transactionDao.setConfig(config);
	}
	
	@BeforeEach
	public void init() {
		transactionDao.setRestTemplateProxy(restTemplateProxy);			
	}
	
	@Test
	void test_recordTransaction_success() {
		
		//-Setup -----------
		TraceableMessage<TransactionRequest> message = setup_traceable_message();
		ResponseEntity<TimedResponse<TransactionResponse>> response = setup_responseEntity();
		
		//-Prepare----------------
		doReturn(response).when(restTemplateProxy).exchange(anyString(), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<TransactionRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransactionResponse>>>any());
		
		//-Execute----------------
		TransactionResponse callresult = transactionDao.recordTransaction(message, message.getPayload());

		//-Verify----------------
		assertSame(response.getBody().getPayload(), callresult);
	}

	TraceableMessage<TransactionRequest> setup_traceable_message() {
		TraceableMessage<TransactionRequest> message = new TraceableMessage<TransactionRequest>();
		message.setBusinessTaxonomyId("jskdfjsdjfls");
		message.setCorrelationId("sdjfsjdlfjslkdfj");
		message.setMessageCreationTime(LocalDateTime.now());
		message.setProducerAit("234234");
		message.setPayload(setup_request());
		return message;
	}
	TransactionRequest setup_request() {
		TransactionRequest request = new TransactionRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setTransactionMetaDataJson("{}");
		return request;

	}
	ResponseEntity<TimedResponse<TransactionResponse>> setup_responseEntity() {
		return new ResponseEntity<TimedResponse<TransactionResponse>>(new TimedResponse<>(123456L, setup_response()), HttpStatus.CREATED);
	}
	ResponseEntity<TimedResponse<TransactionResponse>> setup_failedResponseEntity() {
		return new ResponseEntity<TimedResponse<TransactionResponse>>(new TimedResponse<>(234567L, setup_response()), HttpStatus.INTERNAL_SERVER_ERROR);
	}
	TransactionResponse setup_response() {
		TransactionResponse resourceResponse = new TransactionResponse(TransactionResponse.SUCCESS, new TransactionResource());
		resourceResponse.getTransactions().get(0).setAccountNumber("12345679");
		resourceResponse.getTransactions().get(0).setDebitCardNumber("7823478239467");
		return resourceResponse;
	}
	
	@Test
	void test_recordTransaction_failsOnce() {

		//-Setup -----------
		TraceableMessage<TransactionRequest> message = setup_traceable_message();
		ResponseEntity<TimedResponse<TransactionResponse>> response = setup_responseEntity();
		
		//-Prepare----------------
		doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.doReturn(response)
		.when(restTemplateProxy).exchange(anyString(), eq(HttpMethod.POST), 
			ArgumentMatchers.<HttpEntity<TraceableMessage<TransactionRequest>>>any(), 
			ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransactionResponse>>>any());
		
		//-Execute----------------
		TransactionResponse callresult = transactionDao.recordTransaction(message, message.getPayload());

		//-Verify----------------
		assertSame(response.getBody().getPayload(), callresult);
	}
	
	@Test
	void test_recordTransaction_failsTwice() {

		//-Setup -----------
		TraceableMessage<TransactionRequest> message = setup_traceable_message();
		ResponseEntity<TimedResponse<TransactionResponse>> response = setup_responseEntity();
		
		//-Prepare----------------
		doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.doReturn(response)
		.when(restTemplateProxy).exchange(anyString(), eq(HttpMethod.POST), 
			ArgumentMatchers.<HttpEntity<TraceableMessage<TransactionRequest>>>any(), 
			ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransactionResponse>>>any());
		
		//-Execute----------------
		TransactionResponse callresult = transactionDao.recordTransaction(message, message.getPayload());

		//-Verify----------------
		assertSame(response.getBody().getPayload(), callresult);
	}

	@Test
	void test_recordTransaction_failsThrice() {
		//-Setup -----------
		TraceableMessage<TransactionRequest> message = setup_traceable_message();
		
		//-Prepare----------------
		doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.when(restTemplateProxy).exchange(anyString(), eq(HttpMethod.POST), 
			ArgumentMatchers.<HttpEntity<TraceableMessage<TransactionRequest>>>any(), 
			ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransactionResponse>>>any());
		
		//-Execute----------------
		assertThrows(TransientDataAccessResourceException.class, () -> {
			transactionDao.recordTransaction(message, message.getPayload());
		});

	}
	
	@Test
	void test_recordTransaction_throwsNonTransient() {
		
		//-Setup -----------
		TraceableMessage<TransactionRequest> message = setup_traceable_message();
		ResponseEntity<TimedResponse<TransactionResponse>> response = setup_failedResponseEntity();
		
		//-Prepare----------------
		doReturn(response).when(restTemplateProxy).exchange(anyString(), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<TransactionRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransactionResponse>>>any());

		//-Execute----------------
		assertThrows(NonTransientDataAccessResourceException.class, () -> {
			transactionDao.recordTransaction(message, message.getPayload());
		});

	}
}
