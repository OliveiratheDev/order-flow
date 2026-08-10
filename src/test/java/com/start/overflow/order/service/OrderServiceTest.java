package com.start.overflow.order.service;

import com.start.overflow.identity.entity.AppUser;
import com.start.overflow.identity.entity.UserRole;
import com.start.overflow.identity.service.UserService;
import com.start.overflow.order.dto.CreateOrderItemRequest;
import com.start.overflow.order.dto.CreateOrderRequest;
import com.start.overflow.order.entity.CustomerOrder;
import com.start.overflow.order.entity.OrderProductSnapshot;
import com.start.overflow.order.entity.OrderStatus;
import com.start.overflow.order.mapper.OrderMapper;
import com.start.overflow.order.port.out.OrderCatalogPort;
import com.start.overflow.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock OrderRepository orderRepository;
    @Mock OrderCatalogPort orderCatalogPort;
    @Mock UserService userService;
    @Mock OrderMapper orderMapper;
    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, orderCatalogPort, userService, orderMapper);
    }

    @Test
    void aggregatesItemsAndReservesStockOnceThroughTheCatalogPort() {
        AppUser customer = new AppUser("Maria", "maria@example.com", "52998224725",
                "$2a$12$hash", UserRole.CUSTOMER);
        when(userService.currentUserEntity()).thenReturn(customer);
        when(orderCatalogPort.reserveStock(1L, 5))
                .thenReturn(new OrderProductSnapshot(1L, "Produto", "SKU-1", BigDecimal.TEN));
        when(orderRepository.saveAndFlush(any(CustomerOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(new CreateOrderRequest("Rua A, 123", List.of(
                new CreateOrderItemRequest(1L, 2),
                new CreateOrderItemRequest(1L, 3))));

        verify(orderCatalogPort).reserveStock(1L, 5);
        verify(orderRepository).saveAndFlush(any(CustomerOrder.class));
    }

    @Test
    void createdOrderStartsAwaitingPayment() {
        AppUser customer = new AppUser("Maria", "maria@example.com", "52998224725",
                "$2a$12$hash", UserRole.CUSTOMER);
        when(userService.currentUserEntity()).thenReturn(customer);
        when(orderCatalogPort.reserveStock(1L, 1))
                .thenReturn(new OrderProductSnapshot(1L, "Produto", "SKU-1", BigDecimal.TEN));
        when(orderRepository.saveAndFlush(any(CustomerOrder.class)))
                .thenAnswer(invocation -> {
                    CustomerOrder order = invocation.getArgument(0);
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
                    return order;
                });

        service.create(new CreateOrderRequest("Rua A, 123",
                List.of(new CreateOrderItemRequest(1L, 1))));
    }
}
