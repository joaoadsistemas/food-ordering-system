package com.food.ordering.system.order.service.messaging.publisher.kafka;

import com.food.ordering.system.kafka.order.avro.model.PaymentRequestAvroModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;

@Component
@Slf4j
public class OrderKafkaMessageHelper {

    public <T> BiConsumer<SendResult<String, T>, Throwable> getKafkaCallback(String responseTopicName, T requestAvroModel, String orderId, String requestAvroModelName) {
        return (result, throwable) -> {
            if (throwable != null) {
                log.error("Error while sending "+ requestAvroModelName + " message {} to topic {}",
                        requestAvroModel.toString(), responseTopicName, throwable);
            } else {
                RecordMetadata metadata = result.getRecordMetadata();
                log.info("Received successful response from Kafka for order id: {} Topic: {} Partition: {} Offset: {} Timestamp: {}",
                        orderId,
                        metadata.topic(),
                        metadata.partition(),
                        metadata.offset(),
                        metadata.timestamp());
            }
        };
    }

}
