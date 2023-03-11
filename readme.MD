## Architecture
![Screenshot_20230311_072535](https://user-images.githubusercontent.com/54422342/224492881-ede50ff0-efbc-4875-8359-a34a7ffca9a7.png)
- The order-service acting as an order gateway and a saga pattern orchestrator, sends orders to the Kafka topic: orders. 
- Both other microservices, stock-service and payment-service, listen for incoming order events, receive, and verify if it is possible to execute the order.

## Determine the status of our transaction
![Screenshot_20230311_072547](https://user-images.githubusercontent.com/54422342/224492888-6c2fe669-2f9d-4d8b-853f-b58cfce9e296.png)
- The order-service receives events from the payment-service (in the payment-events topic) and from the stock-service (in the stock-events topic). Every single event contains the id previously set by the order-service.
- Join both streams into a single stream by order’s id to determine the final status of our transaction.
  - CONFIRMED – Both payment-service and stock-service accepted the order. 
  - REJECTED – Both services rejected the order. 
  - ROLLBACK – One service accepted the order, and one service rejected it. 

## Convert Kafka stream to a Kafka table and materialize it in a persistent store. Query the store from our REST controller.
![Screenshot_20230311_072559](https://user-images.githubusercontent.com/54422342/224492889-dd5f8c9f-d8ff-44ff-9bcb-2bf4d5bae1ad.png)
