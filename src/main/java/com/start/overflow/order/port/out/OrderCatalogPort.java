package com.start.overflow.order.port.out;

import com.start.overflow.order.entity.OrderProductSnapshot;

public interface OrderCatalogPort {
    OrderProductSnapshot reserveStock(Long productId, int quantity);

    void restoreStock(Long productId, int quantity);
}
