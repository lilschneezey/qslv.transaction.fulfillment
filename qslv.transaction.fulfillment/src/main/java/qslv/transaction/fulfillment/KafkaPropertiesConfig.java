package qslv.transaction.fulfillment;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class KafkaPropertiesConfig {
	private static final Logger log = LoggerFactory.getLogger(KafkaPropertiesConfig.class);
	
	@Autowired
	ConfigProperties config;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Bean
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
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
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
}
