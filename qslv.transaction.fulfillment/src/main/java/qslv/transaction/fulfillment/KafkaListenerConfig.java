package qslv.transaction.fulfillment;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;

import qslv.common.kafka.JacksonAvroDeserializer;
import qslv.common.kafka.TraceableMessage;
import qslv.transaction.request.TransactionRequest;

@Configuration
public class KafkaListenerConfig {
	private static final Logger log = LoggerFactory.getLogger(KafkaListenerConfig.class);

	@Autowired
	ConfigProperties config;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Bean
	@Profile("!test")
	public Map<String,Object> listenerConfig() throws Exception {
		Properties kafkaconfig = new Properties();
		try {
			kafkaconfig.load(new FileInputStream(config.getKafkaConsumerPropertiesPath()));
		} catch (Exception fileEx) {
			try {
				kafkaconfig.load(Thread.currentThread().getContextClassLoader().getResourceAsStream(config.getKafkaConsumerPropertiesPath()));
			} catch (Exception resourceEx) {
				log.error("{} not found.", config.getKafkaConsumerPropertiesPath());
				log.error("File Exception. {}", fileEx.toString());
				log.error("Resource Exception. {}", resourceEx.toString());
				throw resourceEx;
			}
		}
		return new HashMap(kafkaconfig);
	}

	//--Fulfillment Message Consumer
    @Bean
    public ConsumerFactory<String, TraceableMessage<TransactionRequest>> consumerFactory() throws Exception {
    	
    	JacksonAvroDeserializer<TraceableMessage<TransactionRequest>> jad = new JacksonAvroDeserializer<>();
    	jad.configure(listenerConfig());
    	
        return new DefaultKafkaConsumerFactory<>(listenerConfig(), new StringDeserializer(),  jad);
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TraceableMessage<TransactionRequest>> kafkaListenerContainerFactory() throws Exception {
    
        ConcurrentKafkaListenerContainerFactory<String, TraceableMessage<TransactionRequest>> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        //#TODO: can this be batched for better throughput and still retain idempotency?
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
