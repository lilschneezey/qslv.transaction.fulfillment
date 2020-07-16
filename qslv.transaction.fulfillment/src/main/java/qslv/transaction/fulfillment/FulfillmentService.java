package qslv.transaction.fulfillment;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

@Service
public class FulfillmentService {
	private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);

	@Autowired
	JdbcDao jdbcDao;
	@Autowired
	private TransactionDao transactionDao;

	public void setJdbcDao(JdbcDao jdbcDao) {
		this.jdbcDao = jdbcDao;
	}
	public void setTransactionDao(TransactionDao transactionDao) {
		this.transactionDao = transactionDao;
	}
	
	public TransactionResponse processTransaction(final TraceableMessage<?> tracedata, TransactionRequest request) {
		log.trace("FulfillmentService.processTransaction ENTRY");
		TransactionResponse response = transactionDao.recordTransaction(tracedata, request);
		
		if ( response.getStatus() == TransactionResponse.INSUFFICIENT_FUNDS  && request.isProtectAgainstOverdraft() ) {
			
			//-------Step 0 - switch Transaction List over to a mutable ArrayList
			ArrayList<TransactionResource> accumulatedTransactions = new ArrayList<>();
			accumulatedTransactions.addAll(response.getTransactions());
			response.setTransactions(accumulatedTransactions);

			//-------Step 1 - create a Reservation in an Overdraft Account
			List<TransactionResource> reservations = processOverdraftInstructions(tracedata, request);

			accumulatedTransactions.addAll(reservations);
			
			if ( reservations.size() > 0 && TransactionResource.RESERVATION 
					== reservations.get(reservations.size()-1).getTransactionTypeCode()) {
				TransactionResource reservation = reservations.get(reservations.size()-1);
				
				//-------Step 2 - Multi-step Database Transaction: 1) transfer funds into account, 2) post the transaction
				TransferAndTransactRequest tRequest = new TransferAndTransactRequest();
				tRequest.setTransferReservation(reservation);
				tRequest.setTransactionRequest(request);
				TransferAndTransactReponse tResponse = transactionDao.transferAndTransact(tracedata, tRequest);
				
				accumulatedTransactions.addAll(tResponse.getTransactions());

				// --------------------------------------------------------------------
				// We use the Reservation's Transaction UUID as the Request UUID because:
				// 1) it provides a consistent UUID for idempotency
				// 2) Its safe because: Transaction ID's are generated internally, not by clients.
				// --------------------------------------------------------------------
				CommitReservationRequest commitRequest = new CommitReservationRequest();
				commitRequest.setRequestUuid(reservation.getTransactionUuid());
				commitRequest.setReservationUuid(reservation.getTransactionUuid());
				commitRequest.setTransactionAmount(reservation.getTransactionAmount());
				commitRequest.setTransactionMetaDataJson(reservation.getTransactionMetaDataJson());
				
				//---------Step 3 - Commit the Reservation in the Overdraft Account
				CommitReservationResponse commitResponse = transactionDao.commitReservation(tracedata, commitRequest);
				accumulatedTransactions.add(commitResponse.getResource());
				
				response.setStatus(TransactionResponse.SUCCESS);
			} else {
				response.setStatus(TransactionResponse.INSUFFICIENT_FUNDS);
			}
		}
		log.trace("FulfillmentService.processTransaction EXIT");
		return response;
	}
	
	private List<TransactionResource> processOverdraftInstructions(final TraceableMessage<?> tracedata, TransactionRequest request ) {
		log.trace("FulfillmentService.processOverdraftAccount ENTRY");

		List<OverdraftInstruction> overdraftInstructions = jdbcDao.getOverdraftInstructions(request.getAccountNumber());

		ReservationRequest reservationRequest = new ReservationRequest();
		reservationRequest.setDebitCardNumber(request.getDebitCardNumber());
		reservationRequest.setRequestUuid(request.getRequestUuid());
		reservationRequest.setTransactionAmount(request.getTransactionAmount());
		reservationRequest.setTransactionMetaDataJson(request.getTransactionMetaDataJson());

		ArrayList<TransactionResource> responses = new ArrayList<>();
		for ( OverdraftInstruction instruction : overdraftInstructions) {
			if (false == instructionEffective(instruction) ||
				false == accountInGoodStanding(instruction.getOverdraftAccount())) {
				log.debug("Overdraft Instruction not valid. {}", instruction);
			} else {
				reservationRequest.setAccountNumber(instruction.getOverdraftAccount().getAccountNumber());
				ReservationResponse reservationResponse = transactionDao.recordReservation(tracedata, reservationRequest);
				responses.add(reservationResponse.getResource());

				if ( reservationResponse.getStatus() == ReservationResponse.INSUFFICIENT_FUNDS ) {
					log.debug("Overdraft Instruction failed. {}", instruction.toString());
				} else {
					break;
				}
			}			
		}

		log.trace("FulfillmentService.processOverdraftAccount EXIT");
		return responses;
	}
	private boolean instructionEffective(OverdraftInstruction instruction) {
		return ( instruction.getInstructionLifecycleStatus() == "EF" &&
				 java.time.LocalDateTime.now().compareTo(instruction.getEffectiveStart()) > 0 &&
				 ( instruction.getEffectiveEnd() == null ||
				 java.time.LocalDateTime.now().compareTo(instruction.getEffectiveEnd()) < 0) );
	}

	private boolean accountInGoodStanding(Account account) {
		return (account.getAccountLifeCycleStatus() == "EF");
	}

}