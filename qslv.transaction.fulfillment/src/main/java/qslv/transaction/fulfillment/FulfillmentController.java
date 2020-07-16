package qslv.transaction.fulfillment;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.NonTransientDataAccessResourceException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import qslv.common.kafka.ResponseMessage;
import qslv.common.kafka.TraceableMessage;
import qslv.transaction.request.TransactionRequest;
import qslv.transaction.response.TransactionResponse;
import qslv.util.ServiceLevelIndicator;

@Service
public class FulfillmentController {
	private static final Logger log = LoggerFactory.getLogger(FulfillmentController.class);
	
	@Autowired
	private ConfigProperties config;
	@Autowired
	FulfillmentService fulfillmentService;
	@Autowired
	private KafkaDao kafkaDao;

	public void setKafkaDao(KafkaDao kafkaDao) {
		this.kafkaDao = kafkaDao;
	}
	public void setConfig(ConfigProperties config) {
		this.config = config;
	}
	public void setFulfillmentService(FulfillmentService fulfillmentService) {
		this.fulfillmentService = fulfillmentService;
	}

	public void fulfillTransaction(TraceableMessage<TransactionRequest> message, Acknowledgment acknowledgment) {
		log.warn("ENTRY FulfillmentController::fulfillTransaction");
		
		TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>> traceableResponse = 
				new TraceableMessage<>(message, new ResponseMessage<TransactionRequest,TransactionResponse>(message.getPayload()));

		try {
			validateMessage(message);
			validateTransactionRequest(message.getPayload());	

			TransactionResponse cancelResponse = fulfillmentService.processTransaction(message, message.getPayload());

			traceableResponse.getPayload().setResponse( cancelResponse );
			traceableResponse.setMessageCompletionTime(LocalDateTime.now());

			kafkaDao.produceResponse(traceableResponse);
			ServiceLevelIndicator.logAsyncServiceElapsedTime(log, "TransferFulfillment::fulfillCancel", 
					config.getAitid(), message.getMessageCreationTime());
		} catch (TransientDataAccessException ex) {
			log.warn("Recoverable error. Return message to Kafka and sleep for {} ms.", config.getKafkaTimeout());
			acknowledgment.nack(10000L);
			return;	

		} catch (Exception ex) {
			log.error("Unrecoverable exception thrown. {}", ex.getLocalizedMessage());

			traceableResponse.getPayload().setMessage(ex.getLocalizedMessage());
			traceableResponse.getPayload().setStatus(ResponseMessage.INTERNAL_ERROR);
			
			try {
				kafkaDao.produceResponse(traceableResponse);
			} catch (Exception iex) {
				log.error("Additional unexpected exception caught while processing unexpected exception. Keep message on Kafka. {}", iex.getLocalizedMessage());
				acknowledgment.nack(10000L);
				return;	
			}
		}

		acknowledgment.acknowledge();
		log.warn("EXIT FulfillmentController::fulfillTransaction");
	}
	public class MalformedMessageException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public MalformedMessageException(String msg) {
			super(msg);
		}
	}
	private void validateTransactionRequest( TransactionRequest request) {
		if (request.getRequestUuid() == null) {
			throw new MalformedMessageException("Malformed message payload. Missing From Request UUID.");
		}
		if (request.getAccountNumber() == null || request.getAccountNumber().isEmpty()) {
			throw new MalformedMessageException("Malformed message payload. Missing Account Number.");
		}
		if (request.getTransactionMetaDataJson() == null || request.getTransactionMetaDataJson().isEmpty()) {
			throw new MalformedMessageException("Malformed message payload. Missing Meta Data.");
		}
	}
	private void validateMessage(TraceableMessage<?> data) throws NonTransientDataAccessResourceException {
		if (null == data.getProducerAit()) {
			throw new MalformedMessageException("Malformed message. Missing Producer AIT Id.");
		}
		if (null == data.getCorrelationId()) {
			throw new MalformedMessageException("Malformed message. Missing Correlation Id.");
		}
		if (null == data.getBusinessTaxonomyId()) {
			throw new MalformedMessageException("Malformed message. Missing Business Taxonomy Id.");
		}
		if (null == data.getMessageCreationTime()) {
			throw new MalformedMessageException("Malformed message. Missing Message Creation Time.");
		}
		if (null == data.getPayload()) {
			throw new MalformedMessageException("Malformed message. Missing Fulfillment Message.");
		}
	}
}
