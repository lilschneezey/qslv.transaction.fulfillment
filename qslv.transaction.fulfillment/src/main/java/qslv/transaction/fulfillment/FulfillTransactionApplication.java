package qslv.transaction.fulfillment;

import java.util.Arrays;
import java.util.TreeMap;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;

@SpringBootApplication
public class FulfillTransactionApplication {
	private static final Logger log = LoggerFactory.getLogger(FulfillTransactionApplication.class);
	
	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(FulfillTransactionApplication.class);
        application.run(args);
	}
	
	@Autowired
	private ConfigurableEnvironment  myEnv;

	@PostConstruct
	void postConstruct() {
		TreeMap<String, Object> map = new TreeMap<>();
		log.debug("-----------------");
		myEnv.getPropertySources().forEach(ps -> {
			if ( ps instanceof EnumerablePropertySource<?> ) {
				EnumerablePropertySource<?> eps = (EnumerablePropertySource<?>) ps;
				Arrays.asList( eps.getPropertyNames() ).forEach(key -> {
					map.put(key, eps.getProperty(key));
				});
			} else {
				log.debug("Another type of property {}", ps.getClass().getCanonicalName());
			}
		});

		map.forEach((key,value)->{
			log.debug("{} -- {}", key, value);
		});
		log.debug("-----------------");
	}
}
