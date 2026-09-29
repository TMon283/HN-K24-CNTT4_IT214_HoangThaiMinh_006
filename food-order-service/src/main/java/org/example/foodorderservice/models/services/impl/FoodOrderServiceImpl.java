package org.example.foodorderservice.models.services.impl;

import lombok.RequiredArgsConstructor;
import org.example.foodorderservice.models.constants.FoodOrderStatus;
import org.example.foodorderservice.models.dto.requests.CreateFoodOrderDetailRequest;
import org.example.foodorderservice.models.dto.requests.CreateFoodOrderRequest;
import org.example.foodorderservice.models.dto.responses.FoodOrderDetailResponse;
import org.example.foodorderservice.models.dto.responses.FoodOrderResponse;
import org.example.foodorderservice.models.dto.responses.MenuItemResponse;
import org.example.foodorderservice.models.entities.FoodOrder;
import org.example.foodorderservice.models.entities.FoodOrderDetail;
import org.example.foodorderservice.models.repositories.FoodOrderDetailRepository;
import org.example.foodorderservice.models.repositories.FoodOrderRepository;
import org.example.foodorderservice.models.services.FoodOrderService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodOrderServiceImpl implements FoodOrderService {

        private final FoodOrderRepository foodOrderRepository;
        private final FoodOrderDetailRepository foodOrderDetailRepository;
        private final MenuItemGatewayService menuItemGatewayService;
        private final KafkaTemplate<String, String> kafkaTemplate;

        private static final String FOOD_ORDER_CREATED_TOPIC = "food-order-created";

        @Override
        @Transactional
        public FoodOrderResponse createFoodOrder(CreateFoodOrderRequest request) {
                FoodOrder foodOrder = FoodOrder.builder()
                                .customerName(request.customerName())
                                .customerEmail(request.customerEmail())
                                .totalAmount(0D)
                                .status(FoodOrderStatus.PENDING)
                                .build();
                foodOrder = foodOrderRepository.save(foodOrder);

                List<FoodOrderDetailResponse> itemResponses = new ArrayList<>();
                double totalAmount = 0D;

                for (CreateFoodOrderDetailRequest item : request.items()) {
                        MenuItemResponse menuItem = menuItemGatewayService.getMenuItemById(item.menuItemId());
                        if (item.quantity() > menuItem.stock()) {
                                throw new IllegalArgumentException(
                                                "Menu item does not have enough stock: " + menuItem.menuItemId());
                        }

                        double lineTotal = menuItem.currentPrice() * item.quantity();
                        FoodOrderDetail detail = FoodOrderDetail.builder()
                                        .foodOrder(foodOrder)
                                        .menuItemId(menuItem.menuItemId())
                                        .quantity(item.quantity())
                                        .currentPrice(menuItem.currentPrice())
                                        .build();
                        detail = foodOrderDetailRepository.save(detail);

                        itemResponses.add(new FoodOrderDetailResponse(
                                        detail.getId(), menuItem.menuItemId(), menuItem.name(), item.quantity(),
                                        menuItem.currentPrice(), lineTotal));
                        totalAmount += lineTotal;
                }

                foodOrder.setTotalAmount(totalAmount);
                foodOrder = foodOrderRepository.save(foodOrder);
                kafkaTemplate.send(FOOD_ORDER_CREATED_TOPIC, request.customerEmail());

                return new FoodOrderResponse(foodOrder.getId(), foodOrder.getCustomerName(),
                                foodOrder.getCustomerEmail(), foodOrder.getTotalAmount(), foodOrder.getStatus(),
                                itemResponses);
        }
}
