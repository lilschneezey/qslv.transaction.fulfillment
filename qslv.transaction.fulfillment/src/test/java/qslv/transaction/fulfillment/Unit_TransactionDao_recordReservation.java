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
import qslv.transaction.request.ReservationRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.ReservationResponse;

@ExtendWith(MockitoExtension.class)

class Unit_TransactionDao_recordReservation {

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
		config.setPostReservationUrl("http://localhost:9091/CommitTransaction");
		transactionDao.setConfig(config);
	}
	
	@BeforeEach
	public void init() {
		transactionDao.setRestTemplateProxy(restTemplateProxy);			
	}
	
	@Test
	void test_recordReservation_success() {
		
		//-Setup -----------
		TraceableMessage<ReservationRequest> message = setup_traceable_message();
		ResponseEntity<TimedResponse<ReservationResponse>> response = setup_responseEntity();
		
		//-Prepare----------------
		doReturn(response).when(restTemplateProxy).exchange(anyString(), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<ReservationRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<ReservationResponse>>>any());
		
		//-Execute----------------
		ReservationResponse callresult = transactionDao.recordReservation(message, message.getPayload());

		//-Verify----------------
		assertSame(response.getBody().getPayload(), callresult);
	}

	TraceableMessage<ReservationRequest> setup_traceable_message() {
		TraceableMessage<ReservationRequest> message = new TraceableMessage<ReservationRequest>();
		message.setBusinessTaxonomyId("jskdfjsdjfls");
		message.setCorrelationId("sdjfsjdlfjslkdfj");
		message.setMessageCreationTime(LocalDateTime.now());
		message.setProducerAit("234234");
		message.setPayload(setup_request());
		return message;
	}
	ReservationRequest setup_request() {
		ReservationRequest request = new ReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setTransactionMetaDataJson("{}");
		return request;

	}
	ResponseEntity<TimedResponse<ReservationResponse>> setup_responseEntity() {
		return new ResponseEntity<TimedResponse<ReservationResponse>>(new TimedResponse<>(123456L, setup_response()), HttpStatus.CREATED);
	}
	ResponseEntity<TimedResponse<ReservationResponse>> setup_failedResponseEntity() {
		return new ResponseEntity<TimedResponse<ReservationResponse>>(new TimedResponse<>(234567L, setup_response()), HttpStatus.INTERNAL_SERVER_ERROR);
	}
	ReservationResponse setup_response() {
		ReservationResponse resourceResponse = new ReservationResponse(ReservationResponse.SUCCESS, new TransactionResource());
		resourceResponse.getResource().setAccountNumber("12345679");
		resourceResponse.getResource().setDebitCardNumber("7823478239467");
		return resourceResponse;
	}
	
	@Test
	void test_recordReservation_failsOnce() {

		//-Setup -----------
		TraceableMessage<ReservationRequest> message = setup_traceable_message();
		ResponseEntity<TimedResponse<ReservationResponse>> response = setup_responseEntity();
		
		//-Prepare----------------
		doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.doReturn(response)
		.when(restTemplateProxy).exchange(anyString(), eq(HttpMethod.POST), 
			ArgumentMatchers.<HttpEntity<TraceableMessage<ReservationRequest>>>any(), 
			ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<ReservationResponse>>>any());
		
		//-Execute----------------
		ReservationResponse callresult = transactionDao.recordReservation(message, message.getPayload());

		//-Verify----------------
		assertSame(response.getBody().getPayload(), callresult);
	}
	
	@Test
	void test_recordReservation_failsTwice() {

		//-Setup -----------
		TraceableMessage<ReservationRequest> message = setup_traceable_message();
		ResponseEntity<TimedResponse<ReservationResponse>> response = setup_responseEntity();
		
		//-Prepare----------------
		doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.doReturn(response)
		.when(restTemplateProxy).exchange(anyString(), eq(HttpMethod.POST), 
			ArgumentMatchers.<HttpEntity<TraceableMessage<ReservationRequest>>>any(), 
			ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<ReservationResponse>>>any());
		
		//-Execute----------------
		ReservationResponse callresult = transactionDao.recordReservation(message, message.getPayload());

		//-Verify----------------
		assertSame(response.getBody().getPayload(), callresult);
	}

	@Test
	void test_recordReservation_failsThrice() {
		//-Setup -----------
		TraceableMessage<ReservationRequest> message = setup_traceable_message();
		
		//-Prepare----------------
		doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.doThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
		.when(restTemplateProxy).exchange(anyString(), eq(HttpMethod.POST), 
			ArgumentMatchers.<HttpEntity<TraceableMessage<ReservationRequest>>>any(), 
			ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<ReservationResponse>>>any());
		
		//-Execute----------------
		assertThrows(TransientDataAccessResourceException.class, () -> {
			transactionDao.recordReservation(message, message.getPayload());
		});

	}
	
	@Test
	void test_recordReservation_throwsNonTransient() {
		
		//-Setup -----------
		TraceableMessage<ReservationRequest> message = setup_traceable_message();
		ResponseEntity<TimedResponse<ReservationResponse>> response = setup_failedResponseEntity();
		
		//-Prepare----------------
		doReturn(response).when(restTemplateProxy).exchange(anyString(), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<TraceableMessage<ReservationRequest>>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<ReservationResponse>>>any());

		//-Execute----------------
		assertThrows(NonTransientDataAccessResourceException.class, () -> {
			transactionDao.recordReservation(message, message.getPayload());
		});

	}
}
