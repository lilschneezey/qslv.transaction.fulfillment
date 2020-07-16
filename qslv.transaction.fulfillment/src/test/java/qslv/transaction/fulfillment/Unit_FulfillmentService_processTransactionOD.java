package qslv.transaction.fulfillment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import qslv.common.kafka.ResponseMessage;
import qslv.common.kafka.TraceableMessage;
import qslv.data.Account;
import qslv.data.OverdraftInstruction;
import qslv.transaction.request.TransactionRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.CommitReservationResponse;
import qslv.transaction.response.ReservationResponse;
import qslv.transaction.response.TransactionResponse;
import qslv.transaction.response.TransferAndTransactReponse;


@ExtendWith(MockitoExtension.class)
class Unit_FulfillmentService_processTransactionOD {
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
	void test_processTransaction_NSFSuccess() {
		//-- Setup ------------------
		TraceableMessage<TransactionRequest> request = setup_request();
		TransactionResponse response = setup_response();
		List<OverdraftInstruction> instructions = setup_instructions();
		ReservationResponse reservation = setup_OverDraftReservation();
		TransferAndTransactReponse tResponse = setupTransferAndTransactResponse();
		CommitReservationResponse commitResponse = setupCommitResponse();
		
		//--Prepare----------------------
		doReturn(response).when(transactionDao).recordTransaction(any(), any());
		doReturn(instructions).when(jdbcDao).getOverdraftInstructions(anyString());
		doReturn(reservation).when(transactionDao).recordReservation(any(), any());
		doReturn(tResponse).when(transactionDao).transferAndTransact(any(), any());
		doReturn(commitResponse).when(transactionDao).commitReservation(any(), any());

		//--Execute-----------------------
		TransactionResponse output = fulfillmentService.processTransaction(request, request.getPayload());
		
		//--Verify------------------------
		assertSame(output.getTransactions().get(0), response.getTransactions().get(0));
		assertSame(output.getTransactions().get(1), reservation.getResource());
		assertSame(output.getTransactions().get(2), tResponse.getTransactions().get(0));
		assertSame(output.getTransactions().get(3), tResponse.getTransactions().get(1));
		assertSame(output.getTransactions().get(4), commitResponse.getResource());
	}

	@Test
	void test_processTransaction_NSF_JDBC_Fails() {
		//-- Setup ------------------
		TraceableMessage<TransactionRequest> request = setup_request();
		TransactionResponse response = setup_response();
		
		//--Prepare----------------------
		doReturn(response).when(transactionDao).recordTransaction(any(), any());
		doThrow(new RuntimeException("msg")).when(jdbcDao).getOverdraftInstructions(anyString());

		//--Execute-----------------------
		assertThrows(RuntimeException.class, () -> {
			fulfillmentService.processTransaction(request, request.getPayload());
		});
	}
	
	@Test
	void test_processTransaction_NSF_reservationThrows() {
		//-- Setup ------------------
		TraceableMessage<TransactionRequest> request = setup_request();
		TransactionResponse response = setup_response();
		List<OverdraftInstruction> instructions = setup_instructions();
		
		//--Prepare----------------------
		doReturn(response).when(transactionDao).recordTransaction(any(), any());
		doReturn(instructions).when(jdbcDao).getOverdraftInstructions(anyString());
		doThrow(new RuntimeException("sdfsdf")).when(transactionDao).recordReservation(any(), any());

		//--Execute-----------------------
		assertThrows(RuntimeException.class, () -> {
			fulfillmentService.processTransaction(request, request.getPayload());
		});
		
		//--Verify------------------------
	}
	
	@Test
	void test_processTransaction_NSF_transferThrows() {
		//-- Setup ------------------
		TraceableMessage<TransactionRequest> request = setup_request();
		TransactionResponse response = setup_response();
		List<OverdraftInstruction> instructions = setup_instructions();
		ReservationResponse reservation = setup_OverDraftReservation();
		
		//--Prepare----------------------
		doReturn(response).when(transactionDao).recordTransaction(any(), any());
		doReturn(instructions).when(jdbcDao).getOverdraftInstructions(anyString());
		doReturn(reservation).when(transactionDao).recordReservation(any(), any());
		doThrow(new RuntimeException("sdfsdf")).when(transactionDao).transferAndTransact(any(), any());

		//--Execute-----------------------
		assertThrows(RuntimeException.class, () -> {
			fulfillmentService.processTransaction(request, request.getPayload());
		});
		
		//--Verify------------------------
	}
	
	@Test
	void test_processTransaction_NSF_commitThrows() {
		//-- Setup ------------------
		TraceableMessage<TransactionRequest> request = setup_request();
		TransactionResponse response = setup_response();
		List<OverdraftInstruction> instructions = setup_instructions();
		ReservationResponse reservation = setup_OverDraftReservation();
		TransferAndTransactReponse tResponse = setupTransferAndTransactResponse();
		
		//--Prepare----------------------
		doReturn(response).when(transactionDao).recordTransaction(any(), any());
		doReturn(instructions).when(jdbcDao).getOverdraftInstructions(anyString());
		doReturn(reservation).when(transactionDao).recordReservation(any(), any());
		doReturn(tResponse).when(transactionDao).transferAndTransact(any(), any());
		doThrow(new RuntimeException("sdfsdf")).when(transactionDao).commitReservation(any(), any());

		//--Execute-----------------------
		assertThrows(RuntimeException.class, () -> {
			fulfillmentService.processTransaction(request, request.getPayload());
		});
		
		//--Verify------------------------
	}
	
	@Test
	void test_processTransaction_NSF_instructionInvalid() {
		//-- Setup ------------------
		TraceableMessage<TransactionRequest> request = setup_request();
		TransactionResponse response = setup_response();
		List<OverdraftInstruction> instructions = setup_invalid_instructions();
		
		//--Prepare----------------------
		doReturn(response).when(transactionDao).recordTransaction(any(), any());
		doReturn(instructions).when(jdbcDao).getOverdraftInstructions(anyString());

		//--Execute-----------------------
		TransactionResponse output = fulfillmentService.processTransaction(request, request.getPayload());
		
		//--Verify------------------------
		assertEquals(TransactionResponse.INSUFFICIENT_FUNDS, output.getStatus());
		assertEquals(1, output.getTransactions().size());
		assertSame(output.getTransactions().get(0), response.getTransactions().get(0));
	}
	
	@Test
	void test_processTransaction_NSF_instructionExpired() {
		//-- Setup ------------------
		TraceableMessage<TransactionRequest> request = setup_request();
		TransactionResponse response = setup_response();
		List<OverdraftInstruction> instructions = setup_expired_instructions();
		
		//--Prepare----------------------
		doReturn(response).when(transactionDao).recordTransaction(any(), any());
		doReturn(instructions).when(jdbcDao).getOverdraftInstructions(anyString());

		//--Execute-----------------------
		TransactionResponse output = fulfillmentService.processTransaction(request, request.getPayload());
		
		//--Verify------------------------
		assertEquals(TransactionResponse.INSUFFICIENT_FUNDS, output.getStatus());
		assertEquals(1, output.getTransactions().size());
		assertSame(output.getTransactions().get(0), response.getTransactions().get(0));
	}
	
	@Test
	void test_processTransaction_NSF_accountClosed() {
		//-- Setup ------------------
		TraceableMessage<TransactionRequest> request = setup_request();
		TransactionResponse response = setup_response();
		List<OverdraftInstruction> instructions = setup_closedAccount_instructions();
		
		//--Prepare----------------------
		doReturn(response).when(transactionDao).recordTransaction(any(), any());
		doReturn(instructions).when(jdbcDao).getOverdraftInstructions(anyString());

		//--Execute-----------------------
		TransactionResponse output = fulfillmentService.processTransaction(request, request.getPayload());
		
		//--Verify------------------------
		assertEquals(TransactionResponse.INSUFFICIENT_FUNDS, output.getStatus());
		assertEquals(1, output.getTransactions().size());
		assertSame(output.getTransactions().get(0), response.getTransactions().get(0));
	}
	
	private ReservationResponse setup_OverDraftReservation() {
		ReservationResponse response = new ReservationResponse();
		response.setStatus(ReservationResponse.SUCCESS);
		response.setResource(new TransactionResource());
		response.getResource().setTransactionTypeCode(TransactionResource.RESERVATION);
		return response;
	}

	TransactionResponse setup_response() {
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

	List<OverdraftInstruction> setup_expired_instructions() {
		List<OverdraftInstruction> list = setup_instructions();
		
		list.get(0).setEffectiveStart(LocalDateTime.now().plusYears(1L));
		list.get(1).setEffectiveEnd(LocalDateTime.now().minusYears(1L));
		return list;
	}
	
	List<OverdraftInstruction> setup_invalid_instructions() {
		List<OverdraftInstruction> list = setup_instructions();
		
		list.get(0).setInstructionLifecycleStatus("CL");
		list.get(1).setInstructionLifecycleStatus("CL");
		return list;
	}
	
	List<OverdraftInstruction> setup_closedAccount_instructions() {
		List<OverdraftInstruction> list = setup_instructions();
		
		list.get(0).getOverdraftAccount().setAccountLifeCycleStatus("CL");
		list.get(1).getOverdraftAccount().setAccountLifeCycleStatus("CL");
		return list;
	}
	
	private CommitReservationResponse setupCommitResponse() {
		CommitReservationResponse response = new CommitReservationResponse();
		response.setStatus(CommitReservationResponse.SUCCESS);
		response.setResource(new TransactionResource());
		return response;
	}

	private TransferAndTransactReponse setupTransferAndTransactResponse() {
		TransferAndTransactReponse response = new TransferAndTransactReponse();
		response.setStatus(TransferAndTransactReponse.SUCCESS);
		
		ArrayList<TransactionResource> list = new ArrayList<>();
		response.setTransactions(list);
		TransactionResource resource = new TransactionResource();
		resource.setAccountNumber("28394288934729");
		resource.setTransactionTypeCode(TransactionResource.NORMAL);
		list.add(resource);
		resource = new TransactionResource();
		resource.setTransactionTypeCode(TransactionResource.NORMAL);
		list.add(resource);
		
		return response;
	}
}
