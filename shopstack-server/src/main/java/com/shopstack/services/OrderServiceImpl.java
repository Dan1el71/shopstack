package com.shopstack.services;

import com.shopstack.security.CurrentUser;

/*++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 + MIT License
 +
 + Copyright (c) 2023 Artiom Bozieac
 +
 + Permission is hereby granted, free of charge, to any person obtaining a copy
 + of this software and associated documentation files (the "Software"), to deal
 + in the Software without restriction, including without limitation the rights
 + to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 + copies of the Software, and to permit persons to whom the Software is
 + furnished to do so, subject to the following conditions:
 +
 + The above copyright notice and this permission notice shall be included in all
 + copies or substantial portions of the Software.
 +
 + THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 + IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 + FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 + AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 + LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 + OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 + SOFTWARE.
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

import com.shopstack.model.Order;
import com.shopstack.model.Payment;
import com.shopstack.model.User;
import com.shopstack.model.dto.OrderDTO;
import com.shopstack.model.dto.OrderWithPaymentDTO;
import com.shopstack.model.dto.PaginatedDTO;
import com.shopstack.repositories.OrderRepository;
import com.shopstack.repositories.PaymentRepository;
import com.shopstack.services.interfaces.OrderService;
import com.shopstack.services.interfaces.UserService;
import com.shopstack.utils.RequestBodyToEntityConverter;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation for order service.
 */
@Service
@AllArgsConstructor
public class OrderServiceImpl implements OrderService {

    /**
     * Order repository.
     */
    private final OrderRepository orderRepository;

    /**
     * Payment repository.
     */
    private final PaymentRepository paymentRepository;

    /**
     * User service.
     */
    private final UserService userService;

    /**
     * Places a new order.
     *
     * @param orderDTO is the order DTO.
     *
     * @return the new order's ID.
     */
    @Override
    public Long placeOrder(OrderDTO orderDTO) {
        Order order = RequestBodyToEntityConverter.convertToOrder(orderDTO);

        UserDetails userDetails = CurrentUser.require();
        User user = userService.getByUsername(userDetails.getUsername());
        order.setUser(user);

        order = orderRepository.save(order);

        return order.getId();
    }

    /**
     * Gets all orders for the current user with their payment information.
     *
     * @param page page number
     * @param size page size
     * @return paginated orders with payments.
     */
    @Override
    public PaginatedDTO<OrderWithPaymentDTO> getUserOrders(int page, int size) {
        UserDetails userDetails = CurrentUser.require();
        User user = userService.getByUsername(userDetails.getUsername());

        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orderPage = orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        
        List<OrderWithPaymentDTO> result = new ArrayList<>();

        for (Order order : orderPage.getContent()) {
            OrderWithPaymentDTO dto = new OrderWithPaymentDTO();
            dto.setOrderId(order.getId());
            dto.setTotalAmount(order.getTotalAmount());
            dto.setOrderStatus(order.getStatus());
            dto.setCreatedAt(order.getCreatedAt() != null ? order.getCreatedAt().toInstant(ZoneOffset.UTC) : null);

            Payment payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId());
            if (payment != null) {
                dto.setPaymentId(payment.getId());
                dto.setPaymentAmount(payment.getAmount());
                dto.setPaymentStatus(payment.getStatus());
                dto.setPaymentCreatedAt(payment.getCreatedAt() != null ? payment.getCreatedAt().toInstant(ZoneOffset.UTC) : null);
            }

            result.add(dto);
        }

        Page<OrderWithPaymentDTO> dtoPage = new PageImpl<>(result, pageable, orderPage.getTotalElements());
        return PaginatedDTO.buildPaginatedResponse(dtoPage);
    }
}
