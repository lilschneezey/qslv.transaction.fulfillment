package qslv.transaction.fulfillment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FulfillTransactionApplication {
	
	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(FulfillTransactionApplication.class);
        application.run(args);
	}

}
