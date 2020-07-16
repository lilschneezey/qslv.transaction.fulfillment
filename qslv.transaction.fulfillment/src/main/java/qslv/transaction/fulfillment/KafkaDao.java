package qslv.transaction.fulfillment;

import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Repository;
import qslv.common.kafka.ResponseMessage;
import qslv.common.kafka.TraceableMessage;
import qslv.transaction.request.TransactionRequest;
import qslv.transaction.response.TransactionResponse;

@Repository
public class KafkaDao {
	private static final Logger log = LoggerFactory.getLogger(KafkaDao.class);

	@Autowired
	private ConfigProperties config;

	@Autowired
	private KafkaTemplate<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> transactionKafkaTemplate;

	public void setConfig(ConfigProperties config) {
		this.config = config;
	}
	public void setTransactionKafkaTemplate(
			KafkaTemplate<String, TraceableMessage<ResponseMessage<TransactionRequest, TransactionResponse>>> transactionKafkaTemplate) {
		this.transactionKafkaTemplate = transactionKafkaTemplate;
	}

	public void produceResponse(TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>> message) throws DataAccessException {
		log.trace("ENTRY produceResponse");
		try {
			String key = message.getPayload().getRequest().getAccountNumber();
			transactionKafkaTemplate.send(config.getKafkaTransactionReplyQueue(), key, message).get();
			log.debug("Kakfa Produce {}", message);
		} catch ( ExecutionException ex ) {
			log.debug(ex.getLocalizedMessage());
			throw new TransientDataAccessResourceException("Kafka Producer failure", ex);
		} catch ( InterruptedException  ex) {
			log.debug(ex.getLocalizedMessage());
			throw new TransientDataAccessResourceException("Kafka Producer failure", ex);
		}
		// TODO: log time it took
		log.trace("EXIT produceResponse");
	}
}
