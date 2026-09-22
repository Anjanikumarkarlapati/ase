package com.bistrobyte.menuservice;

import com.bistrobyte.common.security.JwtService;
import com.bistrobyte.common.security.Role;
import com.bistrobyte.menuservice.domain.Category;
import com.bistrobyte.menuservice.domain.MenuItem;
import com.bistrobyte.menuservice.repository.CategoryRepository;
import com.bistrobyte.menuservice.repository.MenuItemRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

/** Shared fixtures for the menu-service tests. */
public abstract class MenuTestSupport {

    @Autowired
    protected CategoryRepository categoryRepository;

    @Autowired
    protected MenuItemRepository menuItemRepository;

    @Autowired
    protected JwtService jwtService;

    protected void clearMenu() {
        menuItemRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    protected Category givenCategory(String name) {
        Category category = new Category();
        category.setName(name);
        category.setDescription(name + " section");
        category.setDisplayOrder(1);
        category.setActive(true);
        return categoryRepository.save(category);
    }

    /** @param stock {@code null} for an unlimited, made-to-order dish. */
    protected MenuItem givenItem(Category category, String name, String price, Integer stock) {
        MenuItem item = new MenuItem();
        item.setCategory(category);
        item.setName(name);
        item.setDescription(name);
        item.setPrice(new BigDecimal(price));
        item.setStockQuantity(stock);
        item.setPreparationMinutes(15);
        item.setAvailable(true);
        item.setActive(true);
        return menuItemRepository.save(item);
    }

    protected String tokenFor(Role role) {
        return jwtService.generateToken(1L, role.name().toLowerCase() + "-user",
                role.name().toLowerCase() + "@bistrobyte.com", role);
    }

    protected String bearer(Role role) {
        return "Bearer " + tokenFor(role);
    }
}
