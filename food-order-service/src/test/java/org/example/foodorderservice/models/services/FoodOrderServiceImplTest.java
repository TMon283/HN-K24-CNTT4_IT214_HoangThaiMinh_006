package org.example.foodorderservice.models.services;

import org.example.foodorderservice.models.dto.requests.CreateFoodOrderDetailRequest;
import org.example.foodorderservice.models.dto.requests.CreateFoodOrderRequest;
import org.example.foodorderservice.models.dto.responses.FoodOrderResponse;
import org.example.foodorderservice.models.dto.responses.MenuItemResponse;
import org.example.foodorderservice.models.entities.FoodOrder;
import org.example.foodorderservice.models.entities.FoodOrderDetail;
import org.example.foodorderservice.models.repositories.FoodOrderDetailRepository;
import org.example.foodorderservice.models.repositories.FoodOrderRepository;
import org.example.foodorderservice.models.services.impl.FoodOrderServiceImpl;
import org.example.foodorderservice.models.services.impl.MenuItemGatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoodOrderServiceImplTest {

    @Test
    void createsFoodOrderAndCalculatesTotal() {
        FoodOrderRepository orderRepository = mock(FoodOrderRepository.class);
        FoodOrderDetailRepository detailRepository = mock(FoodOrderDetailRepository.class);
        MenuItemGatewayService gateway = mock(MenuItemGatewayService.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        FoodOrderService service = new FoodOrderServiceImpl(
                orderRepository, detailRepository, gateway, kafkaTemplate);

        when(gateway.getMenuItemById(1L))
                .thenReturn(new MenuItemResponse(1L, "Phở bò", 50_000D, 10));
        when(orderRepository.save(any(FoodOrder.class))).thenAnswer(invocation -> {
            FoodOrder order = invocation.getArgument(0);
            order.setId(100L);
            return order;
        });
        when(detailRepository.save(any(FoodOrderDetail.class))).thenAnswer(invocation -> {
            FoodOrderDetail detail = invocation.getArgument(0);
            detail.setId(200L);
            return detail;
        });

        FoodOrderResponse response = service.createFoodOrder(new CreateFoodOrderRequest(
                "Nguyễn Văn A", "student@example.com",
                List.of(new CreateFoodOrderDetailRequest(1L, 2))));

        assertEquals(100_000D, response.totalAmount());
        assertEquals("Phở bò", response.items().getFirst().menuItemName());
        assertEquals(100_000D, response.items().getFirst().lineTotal());
        verify(kafkaTemplate).send("food-order-created", "student@example.com");
    }

    @Test
    void rejectsQuantityGreaterThanStock() {
        FoodOrderRepository orderRepository = mock(FoodOrderRepository.class);
        FoodOrderDetailRepository detailRepository = mock(FoodOrderDetailRepository.class);
        MenuItemGatewayService gateway = mock(MenuItemGatewayService.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        FoodOrderService service = new FoodOrderServiceImpl(
                orderRepository, detailRepository, gateway, kafkaTemplate);

        when(orderRepository.save(any(FoodOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gateway.getMenuItemById(1L))
                .thenReturn(new MenuItemResponse(1L, "Phở bò", 50_000D, 1));

        assertThrows(IllegalArgumentException.class, () -> service.createFoodOrder(
                new CreateFoodOrderRequest("Nguyễn Văn A", "student@example.com",
                        List.of(new CreateFoodOrderDetailRequest(1L, 2)))));
        verify(detailRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any());
    }
}
