package com.shopstack.services.interfaces;

import com.shopstack.model.dto.OrderDTO;
import com.shopstack.model.dto.OrderWithPaymentDTO;
import com.shopstack.model.dto.PaginatedDTO;

/**
 * Interface for order service.
 */
public interface OrderService {
    /**
     * Places a new order.
     *
     * @param orderDTO is the order DTO.
     *
     * @return the new order's ID.
     */
    Long placeOrder(final OrderDTO orderDTO);

    /**
     * Gets all orders for the current user with their payment information.
     *
     * @param page page number
     * @param size page size
     * @return paginated orders with payments.
     */
    PaginatedDTO<OrderWithPaymentDTO> getUserOrders(int page, int size);
}
