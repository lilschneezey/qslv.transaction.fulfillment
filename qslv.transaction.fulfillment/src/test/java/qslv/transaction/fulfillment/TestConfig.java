package qslv.transaction.fulfillment;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;

import com.fasterxml.jackson.databind.JavaType;

import qslv.common.kafka.JacksonAvroDeserializer;
import qslv.common.kafka.JacksonAvroSerializer;
import qslv.common.kafka.ResponseMessage;
import qslv.common.kafka.TraceableMessage;
import qslv.transaction.request.TransactionRequest;
import qslv.transaction.response.TransactionResponse;

@Configuration
public class TestConfig {

	@Autowired EmbeddedKafkaBroker embeddedKafka;

	@Bean
	public Map<String,Object> listenerConfig() throws Exception {
		HashMap<String, Object> props = new HashMap<>();
		props.put("bootstrap.servers", embeddedKafka.getBrokersAsString());
		props.put("group.id", "whatever");
		props.put("schema.registry.url", "http://localhost:8081");
	
		return props;
	}
	
	@Bean
	public Map<String,Object> producerConfig() throws Exception {
		HashMap<String, Object> props = new HashMap<>();
		props.put("bootstrap.servers", embeddedKafka.getBrokersAsString());
		props.put("group.id", "whatever");
		props.put("schema.registry.url", "http://localhost:8081");
	
		return props;
	}
	
	@Bean
	Producer<String, TraceableMessage<TransactionRequest>> producer() {return buildProducer();}
	
	@Bean
	Consumer<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> consumer() {
		Consumer<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> consumer = buildConsumer();
		embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "transaction.reply.queue");
		return consumer;
	}

	//------------------------------------------------------
	// Mock producer and consumer to test end-to-end flow
	//------------------------------------------------------
	private Producer<String, TraceableMessage<TransactionRequest>> buildProducer() {
		Map<String, Object> configs = embeddedConfig();
		
    	JacksonAvroSerializer<TraceableMessage<TransactionRequest>> jas = new JacksonAvroSerializer<>();
		JavaType type = jas.getTypeFactory().constructParametricType(TraceableMessage.class, TransactionRequest.class);
    	jas.configure(configs, false, type);
		
		return new DefaultKafkaProducerFactory<>(configs, new StringSerializer(), jas ).createProducer();
	}
	private Consumer<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> buildConsumer() {
		Map<String, Object> configs = embeddedConfig();
		configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		configs.put("group.id", "junit.consumer");
		
    	JacksonAvroDeserializer<TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> jad = new JacksonAvroDeserializer<>();
    	jad.configure(configs);

		return new DefaultKafkaConsumerFactory<>(configs, new StringDeserializer(), jad).createConsumer();
	}
	//----------------
	// Kafka Reconfigure
	//----------------
	private Map<String,Object> embeddedConfig() {
		HashMap<String, Object> props = new HashMap<>();
		props.put("bootstrap.servers", embeddedKafka.getBrokersAsString());
		props.put("schema.registry.url", "http://localhost:8081");
		return props;
	}
}
