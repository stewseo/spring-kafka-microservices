package org.example;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;
import org.apache.kafka.streams.state.Stores;

import org.example.service.OrderManageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;


import java.time.Duration;
import java.util.concurrent.Executor;

@SpringBootApplication
// Indicates a configuration class that declares one or more @Bean methods and also triggers auto-configuration and component scanning.
// equivalent to declaring @Configuration, @EnableAutoConfiguration and @ComponentScan.
@EnableKafkaStreams
// Enable default Kafka Streams components. To be used on Configuration classes as follows:
@EnableAsync
// Enables Spring's asynchronous method execution capability. To be used together with @Configuration classes as follows,
// enabling annotation-driven async processing for an entire Spring application context:
// By default, Spring will be searching for an associated thread pool definition:
// either a unique org.springframework.core.task.TaskExecutor bean in the context,
// or an java.util.concurrent.Executor bean named "taskExecutor" otherwise.
// If neither of the two is resolvable, a org.springframework.core.task.SimpleAsyncTaskExecutor will be used to process async method invocations.
// Besides, annotated methods having a void return type cannot transmit any exception back to the caller. By default, such uncaught exceptions are only logged.
public class OrderApp {

    private static final Logger LOG = LoggerFactory.getLogger(OrderApp.class);

    // The order-service is the most important microservice in our scenario. It acts as an order gateway and a saga pattern orchestrator.
    // It requires all the three topics used in our architecture.
    public static void main(String[] args) {
        SpringApplication.run(OrderApp.class, args);
    }

    // Automatically create orders topic on application startup by defining orders bean
    @Bean
    public NewTopic orders() {
        return TopicBuilder.name("orders")
                .partitions(3)
                .compact()
                .build();
    }

    // Automatically create payment topic on application startup by defining paymentTopic bean
    @Bean
    public NewTopic paymentTopic() {
        return TopicBuilder.name("payment-orders")
                .partitions(3)
                .compact()
                .build();
    }

    // Automatically create stock topic on application startup by defining stockTopic bean
    @Bean
    public NewTopic stockTopic() {
        return TopicBuilder.name("stock-orders")
                .partitions(3)
                .compact()
                .build();
    }

    // Autowire orderManageService
    @Autowired
    OrderManageService orderManageService;

    // Define the Kafka stream using the StreamsBuilder bean
    @Bean
    public KStream<Long, Order> stream(StreamsBuilder builder) {
        JsonSerde<Order> orderSerde = new JsonSerde<>(Order.class);
        // We are joining two streams using the join method of KStream. The joining window is 10 seconds.
        // As the result, we are setting the status of the order and sending a new order to the orders topic. We use the same topic as for sending new orders.
        KStream<Long, Order> stream = builder
                .stream("payment-orders", Consumed.with(Serdes.Long(), orderSerde));

        stream.join(
                        builder.stream("stock-orders"),
                        orderManageService::confirm,
                        JoinWindows.of(Duration.ofSeconds(10)),
                        StreamJoined.with(Serdes.Long(), orderSerde, orderSerde))
                .peek((k, o) -> LOG.info("Output: {}", o))
                .to("orders");

        return stream;
    }

    // Ok, so let’s define another Kafka Streams bean in the order-service.
    // We are getting the same orders topic as a stream.
    // We will convert it to the Kafka table and materialize it in a persistent store.
    // We will be able to query the store from the REST controller.

    @Bean
    public KTable<Long, Order> table(StreamsBuilder builder) {
        KeyValueBytesStoreSupplier store =
                Stores.persistentKeyValueStore("orders");
        JsonSerde<Order> orderSerde = new JsonSerde<>(Order.class);
        KStream<Long, Order> stream = builder
                .stream("orders", Consumed.with(Serdes.Long(), orderSerde));
        return stream.toTable(Materialized.<Long, Order>as(store)
                .withKeySerde(Serdes.Long())
                .withValueSerde(orderSerde));
    }

    // JavaBean that allows for configuring a ThreadPoolExecutor in bean style (through its "corePoolSize", "maxPoolSize", "keepAliveSeconds", "queueCapacity" properties)
    // and exposing it as a Spring org.springframework.core.task.TaskExecutor.
    // This class is also well suited for management and monitoring (e.g. through JMX),
    // providing several useful attributes: "corePoolSize", "maxPoolSize", "keepAliveSeconds" (all supporting updates at runtime); "poolSize", "activeCount" (for introspection only).
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(5);
        executor.setThreadNamePrefix("kafkaSender-");
        executor.initialize();
        return executor;
    }
}
