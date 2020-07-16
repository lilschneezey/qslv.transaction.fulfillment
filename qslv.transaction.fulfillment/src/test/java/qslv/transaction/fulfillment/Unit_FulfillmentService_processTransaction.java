package qslv.transaction.fulfillment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.TransientDataAccessResourceException;
import qslv.common.kafka.ResponseMessage;
import qslv.common.kafka.TraceableMessage;
import qslv.transaction.request.TransactionRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.TransactionResponse;


@ExtendWith(MockitoExtension.class)
class Unit_FulfillmentService_processTransaction {
	FulfillmentService fulfillmentService = new FulfillmentService();
	@Mock
	TransactionDao transactionDao;
	@Mock
	private JdbcDao jdbcDao;
	@Captor
	ArgumentCaptor<TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> captor;
	
	@BeforeEach
	public void setup() {
		MockitoAnnotations.initMocks(this);
		fulfillmentService.setTransactionDao(transactionDao);
		fulfillmentService.setJdbcDao(jdbcDao);
	}

	@Test
	void test_processTransaction_simpleSuccess() {
		//-- Setup ------------------
		TraceableMessage<TransactionRequest> request = setup_request();
		TransactionResponse response = setup_response();

		//--Prepare----------------------
		doReturn(response).when(transactionDao).recordTransaction(any(), any());
		
		//--Execute-----------------------
		TransactionResponse output = fulfillmentService.processTransaction(request, request.getPayload());
		
		//--Verify------------------------
		assertSame(output, response);
	}


	@Test
	void test_processTransaction_restNotAvailable() {		
		//-- Setup ------------------
		TraceableMessage<TransactionRequest> request = setup_request();

		//--Prepare----------------------
		doThrow(new TransientDataAccessResourceException("werwer")).when(transactionDao).recordTransaction(any(), any());
		
		//--Execute-----------------------
		assertThrows(TransientDataAccessResourceException.class, () -> {
			fulfillmentService.processTransaction(request, request.getPayload());
		});
		
		//--Verify------------------------
	}
	
	@Test
	void test_fulfillCancel_restFailure() {
		
		//-- Setup ------------------
		TraceableMessage<TransactionRequest> request = setup_request();

		//--Prepare----------------------
		doThrow(new RuntimeException("werwer")).when(transactionDao).recordTransaction(any(), any());
		
		//--Execute-----------------------
		assertThrows(RuntimeException.class, () -> {
			fulfillmentService.processTransaction(request, request.getPayload());
		});
		
		//--Verify------------------------
	}
	
	TransactionResponse setup_response() {
		TransactionResponse cancelResponse = new TransactionResponse(TransactionResponse.SUCCESS, new TransactionResource());
		return cancelResponse;
	}

	TransactionResponse setup_nsf_response() {
		TransactionResponse cancelResponse = new TransactionResponse(TransactionResponse.INSUFFICIENT_FUNDS, new TransactionResource());
		return cancelResponse;
	}
	
	private TraceableMessage<TransactionRequest> setup_request() {
		TraceableMessage<TransactionRequest> request = new TraceableMessage<>();
		request.setBusinessTaxonomyId("38923748273482");
		request.setCorrelationId("2387429837428374");
		request.setMessageCreationTime(LocalDateTime.now());
		request.setProducerAit("2345");
		request.setPayload(new TransactionRequest());
		request.getPayload().setAccountNumber("23874923749823");
		request.getPayload().setRequestUuid(UUID.randomUUID());
		request.getPayload().setTransactionMetaDataJson("{}");
		request.getPayload().setProtectAgainstOverdraft(true);
		request.getPayload().setAuthorizeAgainstBalance(true);
		return request;
	}

}
