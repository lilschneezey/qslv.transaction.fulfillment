package qslv.transaction.fulfillment;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class KafkaPropertiesConfig {
	private static final Logger log = LoggerFactory.getLogger(KafkaPropertiesConfig.class);

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Bean
	public Map<String,Object> listenerConfig() throws Exception {
		Properties props = new Properties();
		try {
			props
					.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("app-listener-kafka.properties"));
		} catch (Exception ex) {
			log.debug("app-listener-kafka.properties not found.");
			throw ex;
		}
		props.setProperty("enable.auto.commit", "false");
		return new HashMap(props);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Bean
	public Map<String,Object> producerConfig() throws Exception {
		Properties props = new Properties();
		try {
			props
					.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("app-cancel-kafka.properties"));
		} catch (Exception ex) {
			log.debug("app-cancel-kafka.properties not found.");
			throw ex;
		}
		return new HashMap(props);
	}
}
