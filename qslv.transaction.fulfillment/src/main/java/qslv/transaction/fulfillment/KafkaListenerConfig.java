package qslv.transaction.fulfillment;

import java.util.Map;
import javax.annotation.Resource;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;

import qslv.common.kafka.JacksonAvroDeserializer;
import qslv.common.kafka.TraceableMessage;
import qslv.transaction.request.TransactionRequest;

@Configuration
public class KafkaListenerConfig {

	@Autowired
	ConfigProperties config;

	@Resource(name="listenerConfig")
	public Map<String,Object> listenerConfig;	

	//--Fulfillment Message Consumer
    @Bean
    public ConsumerFactory<String, TraceableMessage<TransactionRequest>> consumerFactory() throws Exception {
    	
    	JacksonAvroDeserializer<TraceableMessage<TransactionRequest>> jad = new JacksonAvroDeserializer<>();
    	jad.configure(listenerConfig);
    	
        return new DefaultKafkaConsumerFactory<>(listenerConfig, new StringDeserializer(),  jad);
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
