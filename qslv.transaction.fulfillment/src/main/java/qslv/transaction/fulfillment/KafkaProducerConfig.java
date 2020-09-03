package qslv.transaction.fulfillment;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import com.fasterxml.jackson.databind.JavaType;

import qslv.common.kafka.JacksonAvroSerializer;
import qslv.common.kafka.ResponseMessage;
import qslv.common.kafka.TraceableMessage;
import qslv.transaction.request.TransactionRequest;
import qslv.transaction.response.TransactionResponse;

@Configuration
public class KafkaProducerConfig {
	private static final Logger log = LoggerFactory.getLogger(KafkaProducerConfig.class);
	
	@Autowired
	ConfigProperties config;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Profile("!test")
	@Bean
	public Map<String,Object> producerConfig() throws Exception {
		Properties kafkaconfig = new Properties();
		try {
			kafkaconfig.load(new FileInputStream(config.getKafkaProducerPropertiesPath()));
		} catch (Exception fileEx) {
			try {
				kafkaconfig.load(Thread.currentThread().getContextClassLoader().getResourceAsStream(config.getKafkaProducerPropertiesPath()));
			} catch (Exception resourceEx) {
				log.error("{} not found.", config.getKafkaProducerPropertiesPath());
				log.error("File Exception. {}", fileEx.toString());
				log.error("Resource Exception. {}", resourceEx.toString());
				throw resourceEx;
			}
		}
		return new HashMap(kafkaconfig);
	}

	@Bean
	public ProducerFactory<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> transactionProducerFactory() throws Exception {
		
    	JacksonAvroSerializer<TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> jas = new JacksonAvroSerializer<>();
		JavaType type = jas.getTypeFactory().constructParametricType(TraceableMessage.class, 
				jas.getTypeFactory().constructParametricType(ResponseMessage.class, TransactionRequest.class, TransactionResponse.class));
    	jas.configure(producerConfig(), false, type);
	
		return new DefaultKafkaProducerFactory<>(producerConfig(), new StringSerializer(), jas);
	}

	@Bean
	public KafkaTemplate<String, TraceableMessage<ResponseMessage<TransactionRequest,TransactionResponse>>> transactionKafkaTemplate() throws Exception {
		return new KafkaTemplate<>(transactionProducerFactory(), true); // auto-flush true, to force each message to broker.
	}

}
