package org.example.foodorderservice.models.services.impl;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.example.foodorderservice.clients.MenuItemClient;
import org.example.foodorderservice.exceptions.MenuItemNotFoundException;
import org.example.foodorderservice.exceptions.MenuItemServiceException;
import org.example.foodorderservice.models.dto.responses.MenuItemResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MenuItemGatewayService {

    private final MenuItemClient menuItemClient;

    @CircuitBreaker(name = "menuItemService", fallbackMethod = "getMenuItemByIdFallback")
    public MenuItemResponse getMenuItemById(Long menuItemId) {
        try {
            return menuItemClient.getMenuItemById(menuItemId);
        } catch (FeignException.NotFound exception) {
            throw new MenuItemNotFoundException(menuItemId);
        } catch (FeignException exception) {
            throw new MenuItemServiceException("Menu item service is unavailable", exception);
        }
    }

    private MenuItemResponse getMenuItemByIdFallback(Long menuItemId, Throwable throwable) {
        if (throwable instanceof MenuItemNotFoundException notFoundException) {
            throw notFoundException;
        }
        if (throwable instanceof MenuItemServiceException serviceException) {
            throw serviceException;
        }
        throw new MenuItemServiceException("Menu item service is unavailable", throwable);
    }
}
