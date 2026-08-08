package com.start.overflow.order.controller;

import com.start.overflow.order.dto.CreateOrderRequest;
import com.start.overflow.order.dto.OrderResponse;
import com.start.overflow.order.service.OrderService;
import com.start.overflow.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(response.id()).toUri())
                .body(response);
    }

    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable Long id) {
        return orderService.findById(id);
    }

    @GetMapping
    public PageResponse<OrderResponse> search(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return orderService.search(pageable);
    }

    @PatchMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id) {
        return orderService.cancel(id);
    }

    @PatchMapping("/{id}/ship")
    public OrderResponse ship(@PathVariable Long id) {
        return orderService.ship(id);
    }

    @PatchMapping("/{id}/deliver")
    public OrderResponse deliver(@PathVariable Long id) {
        return orderService.deliver(id);
    }
}
