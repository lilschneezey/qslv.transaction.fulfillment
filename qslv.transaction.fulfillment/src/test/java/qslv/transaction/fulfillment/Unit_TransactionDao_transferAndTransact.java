package qslv.transaction.fulfillment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import qslv.transaction.request.TransferAndTransactRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.TransferAndTransactResponse;

@ExtendWith(MockitoExtension.class)

class Unit_TransactionDao_transferAndTransact {

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
		config.setTransferAndTransactUrl("http://localhost:9091/CommitTransaction");
		transactionDao.setConfig(config);
	}
	
	@BeforeEach
	public void init() {
		transactionDao.setRestTemplateProxy(restTemplateProxy);			
	}
	
	@Test
	void test_transferAndTransact_success() {
		
		//-Setup -----------
		TraceableMessage<TransferAndTransactRequest> message = setup_traceable_message();
		ResponseEntity<TimedResponse<TransferAndTransactResponse>> response = setup_responseEntity();
		
		//-Prepare----------------
		doReturn(response).when(restTemplateProxy).exchange(anyString(), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<TransferAndTransactRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransferAndTransactResponse>>>any());
		
		//-Execute----------------
		TransferAndTransactResponse callresult = transactionDao.transferAndTransact(message, message.getPayload());

		//-Verify----------------
		assertSame(response.getBody().getPayload(), callresult);
	}

	TraceableMessage<TransferAndTransactRequest> setup_traceable_message() {
		TraceableMessage<TransferAndTransactRequest> message = new TraceableMessage<TransferAndTransactRequest>();
		message.setBusinessTaxonomyId("jskdfjsdjfls");
		message.setCorrelationId("sdjfsjdlfjslkdfj");
		message.setMessageCreationTime(LocalDateTime.now());
		message.setProducerAit("234234");
		message.setPayload(setup_request());
		return message;
	}

	TransferAndTransactRequest setup_request() {
		TransferAndTransactRequest tandt = new TransferAndTransactRequest();
		tandt.setRequestUuid(UUID.randomUUID());
		TransactionResource reservation = new TransactionResource();
		reservation.setAccountNumber("27384729834");
		tandt.setTransferReservation(reservation);
		TransactionRequest request = new TransactionRequest();
		request.setRequestUuid(UUID.randomUUID());
		tandt.setTransactionRequest(request);
		return tandt;
	}
	ResponseEntity<TimedResponse<TransferAndTransactResponse>> setup_responseEntity() {
		return new ResponseEntity<TimedResponse<TransferAndTransactResponse>>(new TimedResponse<>(123456L, setup_response()), HttpStatus.CREATED);
	}
	ResponseEntity<TimedResponse<TransferAndTransactResponse>> setup_failedResponseEntity() {
		return new ResponseEntity<TimedResponse<TransferAndTransactResponse>>(new TimedResponse<>(234567L, setup_response()), HttpStatus.INTERNAL_SERVER_ERROR);
	}
	TransferAndTransactResponse setup_response() {
		ArrayList<TransactionResource> list = new ArrayList<>();
		TransactionResource resource = new TransactionResource();
		resource.setAccountNumber("27384729834");
		list.add(resource);
		resource = new TransactionResource();
		resource.setAccountNumber("9872398427394");
		list.add(resource);
		TransferAndTransactResponse resourceResponse = new TransferAndTransactResponse(TransferAndTransactResponse.SUCCESS, list);
		return resourceResponse;
	}
	
	@Test
	void test_transferAndTransact_failsOnce() {

		//-Setup -----------
		TraceableMessage<TransferAndTransactRequest> message = setup_traceable_message();
		ResponseEntity<TimedResponse<TransferAndTransactResponse>> response = setup_responseEntity();
		
		//-Prepare----------------
		doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.doReturn(response)
		.when(restTemplateProxy).exchange(anyString(), eq(HttpMethod.POST), 
			ArgumentMatchers.<HttpEntity<TraceableMessage<TransferAndTransactRequest>>>any(), 
			ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransferAndTransactResponse>>>any());
		
		//-Execute----------------
		TransferAndTransactResponse callresult = transactionDao.transferAndTransact(message, message.getPayload());

		//-Verify----------------
		assertSame(response.getBody().getPayload(), callresult);
	}
	
	@Test
	void test_transferAndTransact_failsTwice() {

		//-Setup -----------
		TraceableMessage<TransferAndTransactRequest> message = setup_traceable_message();
		ResponseEntity<TimedResponse<TransferAndTransactResponse>> response = setup_responseEntity();
		
		//-Prepare----------------
		doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.doReturn(response)
		.when(restTemplateProxy).exchange(anyString(), eq(HttpMethod.POST), 
			ArgumentMatchers.<HttpEntity<TraceableMessage<TransferAndTransactRequest>>>any(), 
			ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransferAndTransactResponse>>>any());
		
		//-Execute----------------
		TransferAndTransactResponse callresult = transactionDao.transferAndTransact(message, message.getPayload());

		//-Verify----------------
		assertSame(response.getBody().getPayload(), callresult);
	}

	@Test
	void test_transferAndTransact_failsThrice() {
		//-Setup -----------
		TraceableMessage<TransferAndTransactRequest> message = setup_traceable_message();
		
		//-Prepare----------------
		doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.when(restTemplateProxy).exchange(anyString(), eq(HttpMethod.POST), 
			ArgumentMatchers.<HttpEntity<TraceableMessage<TransferAndTransactRequest>>>any(), 
			ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransferAndTransactResponse>>>any());
		
		//-Execute----------------
		assertThrows(TransientDataAccessResourceException.class, () -> {
			transactionDao.transferAndTransact(message, message.getPayload());
		});

	}
	
	@Test
	void test_transferAndTransact_throwsNonTransient() {
		
		//-Setup -----------
		TraceableMessage<TransferAndTransactRequest> message = setup_traceable_message();
		ResponseEntity<TimedResponse<TransferAndTransactResponse>> response = setup_failedResponseEntity();
		
		//-Prepare----------------
		doReturn(response).when(restTemplateProxy).exchange(anyString(), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<TransferAndTransactRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransferAndTransactResponse>>>any());

		//-Execute----------------
		assertThrows(NonTransientDataAccessResourceException.class, () -> {
			transactionDao.transferAndTransact(message, message.getPayload());
		});

	}
}
