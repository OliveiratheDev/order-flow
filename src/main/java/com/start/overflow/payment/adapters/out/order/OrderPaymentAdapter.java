package com.start.overflow.payment.adapters.out.order;

import com.start.overflow.catalog.entity.Product;
import com.start.overflow.catalog.repository.ProductRepository;
import com.start.overflow.order.entity.CustomerOrder;
import com.start.overflow.order.entity.OrderItem;
import com.start.overflow.order.entity.OrderStatus;
import com.start.overflow.order.repository.OrderRepository;
import com.start.overflow.payment.ports.out.OrderPaymentPort;
import com.start.overflow.payment.domain.Payer;
import com.start.overflow.shared.exception.BusinessRuleException;
import com.start.overflow.shared.exception.ResourceNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderPaymentAdapter implements OrderPaymentPort {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderPaymentAdapter(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Override
    public PayableOrder loadPayableOrder(Long orderId, Long requesterId, boolean admin) {
        CustomerOrder order = findOrderForUpdate(orderId);
        if (!admin && !order.getCustomer().getId().equals(requesterId)) {
            throw new ResourceNotFoundException("Pedido não encontrado: " + orderId);
        }
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw new BusinessRuleException("O pedido não está aguardando pagamento");
        }
        return new PayableOrder(order.getId(), new Payer(
                order.getCustomer().getId(),
                order.getCustomer().getName(),
                order.getCustomer().getEmail(),
                order.getCustomer().getDocument()), order.getTotal());
    }

    @Override
    public void markOrderPaid(Long orderId) {
        findOrderForUpdate(orderId).markPaid();
    }

    @Override
    @CacheEvict(cacheNames = "products", allEntries = true)
    public void cancelOrderAndRestoreStock(Long orderId) {
        CustomerOrder order = findOrderForUpdate(orderId);
        order.cancel();
        List<OrderItem> items = order.getItems().stream()
                .sorted((left, right) -> Long.compare(
                        left.getProduct().getId(), right.getProduct().getId()))
                .toList();
        for (OrderItem item : items) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Produto não encontrado: " + item.getProduct().getId()));
            product.restoreStock(item.getQuantity());
        }
    }

    private CustomerOrder findOrderForUpdate(Long id) {
        CustomerOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + id));
        order.getItems().size();
        return order;
    }
}
