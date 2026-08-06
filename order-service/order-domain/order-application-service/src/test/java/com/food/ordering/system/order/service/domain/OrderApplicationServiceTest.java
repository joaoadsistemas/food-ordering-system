package com.food.ordering.system.order.service.domain;

import com.food.ordering.system.domain.valueObject.*;
import com.food.ordering.system.order.service.domain.dto.create.CreateOrderCommand;
import com.food.ordering.system.order.service.domain.dto.create.CreateOrderResponse;
import com.food.ordering.system.order.service.domain.dto.create.OrderAddress;
import com.food.ordering.system.order.service.domain.dto.create.OrderItem;
import com.food.ordering.system.order.service.domain.dto.track.TrackOrderQuery;
import com.food.ordering.system.order.service.domain.dto.track.TrackOrderResponse;
import com.food.ordering.system.order.service.domain.entity.Customer;
import com.food.ordering.system.order.service.domain.entity.Order;
import com.food.ordering.system.order.service.domain.entity.Product;
import com.food.ordering.system.order.service.domain.entity.Restaurant;
import com.food.ordering.system.order.service.domain.exception.OrderDomainException;
import com.food.ordering.system.order.service.domain.exception.OrderNotFoundException;
import com.food.ordering.system.order.service.domain.mapper.OrderDataMapper;
import com.food.ordering.system.order.service.domain.ports.input.service.OrderApplicationService;
import com.food.ordering.system.order.service.domain.ports.output.ai.order.noteInterpreter.OrderNoteInterpreter;
import com.food.ordering.system.order.service.domain.ports.output.repository.CustomerRepository;
import com.food.ordering.system.order.service.domain.ports.output.repository.OrderRepository;
import com.food.ordering.system.order.service.domain.ports.output.repository.RestaurantRepository;
import com.food.ordering.system.order.service.domain.valueObject.OrderItemId;
import com.food.ordering.system.order.service.domain.valueObject.StreetAddress;
import com.food.ordering.system.order.service.domain.valueObject.TrackingId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = OrderTestConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OrderApplicationServiceTest {

    @Autowired
    private OrderApplicationService orderApplicationService;
    @Autowired
    private OrderDataMapper orderDataMapper;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private RestaurantRepository restaurantRepository;
    @Autowired
     private OrderNoteInterpreter orderNoteInterpreter;

    private CreateOrderCommand createOrderCommand;
    private CreateOrderCommand createOrderCommandWrongPrice;
    private CreateOrderCommand createOrderCommandWrongProductPrice;
    private final UUID CUSTOMER_ID = UUID.fromString("6ca94e66-1036-4e4f-a32a-6033edc9eb14");
    private final UUID RESTAURANT_ID = UUID.fromString("48d87807-a849-404b-a94f-5d1fdae9a6f7");
    private final UUID PRODUCT_ID = UUID.fromString("399435ef-7d59-4683-9ed5-f555f90d8a6a");
    private final UUID ORDER_ID = UUID.fromString("402cb1a8-2861-452e-9031-bf2aa2ed867b");
    private final BigDecimal PRICE = new BigDecimal("200.00");

    private TrackOrderQuery trackOrderQuery;
    private final UUID TRACK_ORDER_ID = UUID.fromString("6ca94e66-1036-4e4f-a32a-6033edc9eb14");
    private final UUID STREET_ID = UUID.fromString("6ca94e66-1036-4e4f-a32a-6033edc9eb14");
    private final Long ORDER_ITEM_ID = 1L;
    private TrackOrderResponse trackOrderResponse;

    @BeforeAll
    public void init() {

        String orderNotes = "no onions pls, with pickles, extra spicy but not too spicy. Leave at the door!";

        createOrderCommand = CreateOrderCommand.builder()
                .customerId(CUSTOMER_ID)
                .restaurantId(RESTAURANT_ID)
                .address(OrderAddress.builder()
                        .street("street_1")
                        .postalCode("1000AB")
                        .city("Paris")
                        .build())
                .price(PRICE)
                .items(List.of(OrderItem.builder()
                                .productId(PRODUCT_ID)
                                .quantity(1)
                                .price(new BigDecimal("50.00"))
                                .subTotal(new BigDecimal("50.00"))
                                .build(),
                        OrderItem.builder()
                                .productId(PRODUCT_ID)
                                .quantity(3)
                                .price(new BigDecimal("50.00"))
                                .subTotal(new BigDecimal("150.00"))
                                .build()
                        ))
                .orderNotes(orderNotes)
                .build();

        createOrderCommandWrongPrice = CreateOrderCommand.builder()
                .customerId(CUSTOMER_ID)
                .restaurantId(RESTAURANT_ID)
                .address(OrderAddress.builder()
                        .street("street_1")
                        .postalCode("1000AB")
                        .city("Paris")
                        .build())
                .price(new BigDecimal("250.00"))
                .items(List.of(OrderItem.builder()
                                .productId(PRODUCT_ID)
                                .quantity(1)
                                .price(new BigDecimal("50.00"))
                                .subTotal(new BigDecimal("50.00"))
                                .build(),
                        OrderItem.builder()
                                .productId(PRODUCT_ID)
                                .quantity(3)
                                .price(new BigDecimal("50.00"))
                                .subTotal(new BigDecimal("150.00"))
                                .build()
                ))
                .build();

        createOrderCommandWrongProductPrice = CreateOrderCommand.builder()
                .customerId(CUSTOMER_ID)
                .restaurantId(RESTAURANT_ID)
                .address(OrderAddress.builder()
                        .street("street_1")
                        .postalCode("1000AB")
                        .city("Paris")
                        .build())
                .price(new BigDecimal("210.00"))
                .items(List.of(OrderItem.builder()
                                .productId(PRODUCT_ID)
                                .quantity(1)
                                .price(new BigDecimal("60.00"))
                                .subTotal(new BigDecimal("60.00"))
                                .build(),
                        OrderItem.builder()
                                .productId(PRODUCT_ID)
                                .quantity(3)
                                .price(new BigDecimal("50.00"))
                                .subTotal(new BigDecimal("150.00"))
                                .build()
                ))
                .build();

        Customer customer = new Customer();
        customer.setId(new CustomerId(CUSTOMER_ID));

        Restaurant restaurantResponse = Restaurant.Builder.builder()
                .restaurantId(new RestaurantId(createOrderCommand.getRestaurantId()))
                .products(List.of(new Product(new ProductId(PRODUCT_ID), "product-1", new Money(new BigDecimal("50.00"))),
                        new Product(new ProductId(PRODUCT_ID), "product-2", new Money(new BigDecimal("50.00")))))
                .active(true)
                .build();

        OrderPreferences orderPreferences = OrderPreferences.builder()
                .addIngredients(List.of("pickle"))
                .removeIngredientes(List.of("onion"))
                .spiceLevel(SpiceLevel.MEDIUM)
                .deliveryInstructions("Leave at the door!")
                .build();
        Order order = orderDataMapper.createOrderCommandToOrder(createOrderCommand);
        order.updateOrderPreferences(orderPreferences);
        order.setId(new OrderId(ORDER_ID));

        when(customerRepository.findCustomer(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(restaurantRepository.findRestaurantInformation(orderDataMapper.createOrderCommandToRestaurant(createOrderCommand)))
                .thenReturn(Optional.of(restaurantResponse));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(orderNoteInterpreter.interpret(orderNotes)).thenReturn(orderPreferences);

        // TRACK ORDER
        trackOrderQuery = TrackOrderQuery.builder()
                .orderTrackingId(TRACK_ORDER_ID)
                .build();

        trackOrderResponse = TrackOrderResponse.builder()
                .orderTrackingId(TRACK_ORDER_ID)
                .failureMessages(List.of())
                .orderStatus(OrderStatus.PAID)
                .build();

        Order orderTrack = Order.Builder.builder()
                .orderId(new OrderId(ORDER_ID))
                .customerId(new CustomerId(CUSTOMER_ID))
                .restaurantId(new RestaurantId(RESTAURANT_ID))
                .deliveryAddress(new StreetAddress(
                        STREET_ID,
                        "street",
                        "000",
                        "city"
                ))
                .price(new Money(PRICE))
                .items(List.of(com.food.ordering.system.order.service.domain.entity.OrderItem.Builder.builder()
                                        .orderItemId(new OrderItemId(ORDER_ITEM_ID))
                                        .orderId(new OrderId(ORDER_ID))
                                .quantity(1)
                                .price(new Money(new BigDecimal(50)))
                                .subTotal(new Money(new BigDecimal(50)))
                                .build(),
                        com.food.ordering.system.order.service.domain.entity.OrderItem.Builder.builder()
                                .orderItemId(new OrderItemId(ORDER_ITEM_ID))
                                .orderId(new OrderId(ORDER_ID))
                                .quantity(2)
                                .price(new Money(new BigDecimal(50)))
                                .subTotal(new Money(new BigDecimal(100)))
                                .build()
                ))
                .trackingId(new TrackingId(TRACK_ORDER_ID))
                .orderStatus(OrderStatus.PAID)
                .failureMessages(List.of())
                .build();

        when(orderRepository.findByTrackingId(new TrackingId(TRACK_ORDER_ID))).thenReturn(Optional.of(orderTrack));
    }

    @Test
     void testCreateOrder(){
        CreateOrderResponse createOrderResponse = orderApplicationService.createOrder(createOrderCommand);
        assertEquals(OrderStatus.PENDING, createOrderResponse.getOrderStatus());
        assertEquals("Order created successfully", createOrderResponse.getMessage());
        assertNotNull(createOrderResponse.getOrderTrackingId());
    }

    @Test
    void testCreateOrderWithWrongTotalPrice() {
        OrderDomainException orderDomainException = assertThrows(OrderDomainException.class,
                () -> orderApplicationService.createOrder(createOrderCommandWrongPrice));
        assertEquals("Total price: 250.00 is not equal to Order items total: 200.00",
                orderDomainException.getMessage());
    }

    @Test
    void testCreateOrderWithWrongProductPrice() {
        OrderDomainException orderDomainException = assertThrows(OrderDomainException.class,
                () -> orderApplicationService.createOrder(createOrderCommandWrongProductPrice));
        assertEquals("Order item price: 60.00 is not valid for product: " + PRODUCT_ID,
                orderDomainException.getMessage());
    }

    @Test
    void testCreateOrderWithPassiveRestaurant() {
        Restaurant restaurantResponse = Restaurant.Builder.builder()
                .restaurantId(new RestaurantId(createOrderCommand.getRestaurantId()))
                .products(List.of(new Product(new ProductId(PRODUCT_ID), "product-1", new Money(new BigDecimal("50.00"))),
                        new Product(new ProductId(PRODUCT_ID), "product-2", new Money(new BigDecimal("50.00")))))
                .active(false)
                .build();
        when(restaurantRepository.findRestaurantInformation(orderDataMapper.createOrderCommandToRestaurant(createOrderCommand)))
                .thenReturn(Optional.of(restaurantResponse));
        OrderDomainException orderDomainException = assertThrows(OrderDomainException.class,
                () -> orderApplicationService.createOrder(createOrderCommand));
        assertEquals("Restaurant with id " + RESTAURANT_ID + " is currently not active!",
                orderDomainException.getMessage());

    }

    @Test
    void testTrackOrderQueryWithValidId() {
        TrackOrderResponse newTrackOrderResponse = orderApplicationService.trackOrder(trackOrderQuery);
        assertEquals(trackOrderResponse.getOrderTrackingId(), newTrackOrderResponse.getOrderTrackingId());
        assertEquals(List.of(), newTrackOrderResponse.getFailureMessages());
        assertEquals(TRACK_ORDER_ID, newTrackOrderResponse.getOrderTrackingId());
    }

    @Test
    void testTrackOrderQueryWithInvalidId() {
        when(orderRepository.findByTrackingId(any())).thenReturn(Optional.empty());
        OrderNotFoundException orderNotFoundException = assertThrows(OrderNotFoundException.class, () -> orderApplicationService.trackOrder(trackOrderQuery));
        assertEquals("Could not find order with tracking id: " + TRACK_ORDER_ID, orderNotFoundException.getMessage());
    }

}
