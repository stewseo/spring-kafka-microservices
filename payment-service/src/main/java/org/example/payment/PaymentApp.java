package org.example.payment;

import net.datafaker.Faker;
import org.example.Order;
import org.example.payment.domain.Customer;
import org.example.payment.repository.CustomerRepository;
import org.example.payment.service.OrderManageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;


import javax.annotation.PostConstruct;
import java.util.Random;

// Indicates a configuration class that declares one or more @Bean methods and also triggers auto-configuration and component scanning.
// This is a convenience annotation that is equivalent to declaring @Configuration, @EnableAutoConfiguration and @ComponentScan.
@SpringBootApplication
// Enable Kafka listener annotated endpoints that are created under the covers by a AbstractListenerContainerFactory. To be used on Configuration classes.
// The KafkaListenerContainerFactory is responsible to create the listener container for a particular endpoint.
// Typical implementations such as ConcurrentKafkaListenerContainerFactory provides
// the necessary configuration options that are supported by the underlying MessageListenerContainer.
// @EnableKafka enables detection of KafkaListener annotations on any Spring-managed bean in the container.
@EnableKafka
public class PaymentApp {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentApp.class);

    public static void main(String[] args) {
        SpringApplication.run(PaymentApp.class, args);
    }

    @Autowired
    OrderManageService orderManageService;

    // Annotation that marks a method to be the target of a Kafka message listener on the specified topics.
    // The containerFactory() identifies the KafkaListenerContainerFactory to use to build the Kafka listener container.
    // When defined at the method level, a listener container is created for each method.
    // The org.springframework.kafka.listener.MessageListener is a org.springframework.kafka.listener.adapter.MessagingMessageListenerAdapter, configured with a org.springframework.kafka.config.MethodKafkaListenerEndpoint.
    @KafkaListener(id = "orders", topics = "orders", groupId = "payment")
    public void onEvent(Order o) {
        // Performs reservation on the customer’s account and sends a response with a reservation status to the payment-orders topic.
        // If it receives confirmation of the transaction from the order-service, it commits the transaction or rollbacks it.
        // The following method listens for events on the orders topic and runs in the payment consumer group.
        LOG.info("Received: {}", o);
        if (o.getStatus().equals("NEW")) {
            orderManageService.reserve(o);
        } else {
            orderManageService.confirm(o);
        }
    }

    @Autowired
    private CustomerRepository repository;

    // The PostConstruct annotation is used on a method that needs to be executed after dependency injection is done to perform any initialization.
    // This method must be invoked before the class is put into service. This annotation must be supported on all classes that support dependency injection.
    // The method annotated with PostConstruct must be invoked even if the class does not request any resources to be injected.
    // Only one method in a given class can be annotated with this annotation.
    // The method on which the PostConstruct annotation is applied must fulfill all of the following criteria:
    @PostConstruct
    public void generateData() {
        Random r = new Random();
        Faker faker = new Faker();
        for (int i = 0; i < 100; i++) {
            int count = r.nextInt(1000);
            Customer c = new Customer(null, faker.name().fullName(), count, 0);
            repository.save(c);
        }
    }
}
