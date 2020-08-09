package qslv.transaction.fulfillment;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import qslv.common.kafka.TraceableMessage;
import qslv.transaction.request.TransactionRequest;

@Component
public class KafkaTransactionListener {
	private static final Logger log = LoggerFactory.getLogger(KafkaTransactionListener.class);

	@Autowired
	private FulfillmentController fulfillmentController;

	public void setFulfillmentController(FulfillmentController fulfillmentController) {
		this.fulfillmentController = fulfillmentController;
	}

	@KafkaListener(topics = "transaction.fulfillment.request.queue")
	void onCancelMessage(final ConsumerRecord<String, TraceableMessage<TransactionRequest>> data, Acknowledgment acknowledgment) {
		log.trace("onMessage ENTRY");

		fulfillmentController.fulfillTransaction(data.value(), acknowledgment);
		log.error("========================={} {}", data.key(), data.value());

		log.trace("onMessage EXIT");
	}

}
