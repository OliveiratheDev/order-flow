package com.start.overflow.order.service;

import com.start.overflow.catalog.entity.Product;
import com.start.overflow.catalog.repository.ProductRepository;
import com.start.overflow.identity.entity.AppUser;
import com.start.overflow.identity.entity.UserRole;
import com.start.overflow.identity.service.UserService;
import com.start.overflow.order.dto.CreateOrderItemRequest;
import com.start.overflow.order.dto.CreateOrderRequest;
import com.start.overflow.order.dto.OrderResponse;
import com.start.overflow.order.entity.CustomerOrder;
import com.start.overflow.order.entity.OrderItem;
import com.start.overflow.order.mapper.OrderMapper;
import com.start.overflow.order.repository.OrderRepository;
import com.start.overflow.shared.dto.PageResponse;
import com.start.overflow.shared.exception.ResourceNotFoundException;
import com.start.overflow.shared.exception.ValidationException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserService userService;
    private final OrderMapper orderMapper;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        UserService userService, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userService = userService;
        this.orderMapper = orderMapper;
    }

    @Transactional
    @CacheEvict(cacheNames = "products", allEntries = true)
    public OrderResponse create(CreateOrderRequest request) {
        AppUser customer = userService.currentUserEntity();
        Map<Long, Integer> quantities = aggregateItems(request.items());
        CustomerOrder.Builder builder = CustomerOrder.builder()
                .customer(customer)
                .shippingAddress(request.shippingAddress());

        quantities.keySet().stream().sorted().forEach(productId -> {
            Product product = findProductForUpdateOrThrow(productId);
            int quantity = quantities.get(productId);
            product.reserveStock(quantity);
            builder.addItem(product, quantity);
        });

        CustomerOrder order = builder.build();
        order.awaitPayment();
        return orderMapper.toResponse(orderRepository.saveAndFlush(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        AppUser currentUser = userService.currentUserEntity();
        CustomerOrder order = findDetailedOrThrow(id);
        ensureOwnerOrAdmin(order, currentUser);
        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> search(Pageable pageable) {
        AppUser currentUser = userService.currentUserEntity();
        Pageable bounded = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100),
                pageable.getSort());
        Page<CustomerOrder> orders = currentUser.getRole() == UserRole.ADMIN
                ? orderRepository.findAll(bounded)
                : orderRepository.findByCustomerId(currentUser.getId(), bounded);
        return PageResponse.from(orders.map(orderMapper::toResponse));
    }

    @Transactional
    @CacheEvict(cacheNames = "products", allEntries = true)
    public OrderResponse cancel(Long id) {
        AppUser currentUser = userService.currentUserEntity();
        CustomerOrder order = findForUpdateOrThrow(id);
        ensureOwnerOrAdmin(order, currentUser);
        order.cancel();
        restoreStock(order);
        return orderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse ship(Long id) {
        CustomerOrder order = findForUpdateOrThrow(id);
        order.ship();
        return orderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse deliver(Long id) {
        CustomerOrder order = findForUpdateOrThrow(id);
        order.deliver();
        return orderMapper.toResponse(order);
    }

    private Map<Long, Integer> aggregateItems(List<CreateOrderItemRequest> items) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (CreateOrderItemRequest item : items) {
            try {
                quantities.merge(item.productId(), item.quantity(), Math::addExact);
            } catch (ArithmeticException ex) {
                throw new ValidationException("A quantidade total de um item excede o limite permitido");
            }
        }
        return quantities;
    }

    private void restoreStock(CustomerOrder order) {
        List<OrderItem> items = order.getItems().stream()
                .sorted((left, right) -> Long.compare(
                        left.getProduct().getId(), right.getProduct().getId()))
                .toList();
        for (OrderItem item : items) {
            Product product = findProductForUpdateOrThrow(item.getProduct().getId());
            product.restoreStock(item.getQuantity());
        }
    }

    private CustomerOrder findDetailedOrThrow(Long id) {
        return orderRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + id));
    }

    private CustomerOrder findForUpdateOrThrow(Long id) {
        CustomerOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + id));
        order.getItems().size();
        return order;
    }

    private Product findProductForUpdateOrThrow(Long id) {
        return productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + id));
    }

    private void ensureOwnerOrAdmin(CustomerOrder order, AppUser user) {
        if (user.getRole() != UserRole.ADMIN && !order.getCustomer().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Pedido não encontrado: " + order.getId());
        }
    }
}
