package com.bistrobyte.menuservice.config;

import com.bistrobyte.menuservice.domain.Category;
import com.bistrobyte.menuservice.domain.MenuItem;
import com.bistrobyte.menuservice.repository.CategoryRepository;
import com.bistrobyte.menuservice.repository.MenuItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Loads a small working menu so the platform can be validated end to end immediately
 * after start-up. Disable with {@code bistrobyte.seed.enabled=false}.
 */
@Configuration
@ConditionalOnProperty(name = "bistrobyte.seed.enabled", havingValue = "true", matchIfMissing = true)
public class SampleMenuInitializer {

    private static final Logger log = LoggerFactory.getLogger(SampleMenuInitializer.class);

    @Bean
    public ApplicationRunner seedMenu(CategoryRepository categories, MenuItemRepository items) {
        return args -> {
            if (categories.count() > 0) {
                return;
            }
            Category starters = category(categories, "Starters", "Small plates to begin", 1);
            Category mains = category(categories, "Mains", "Wood-fired and grilled classics", 2);
            Category desserts = category(categories, "Desserts", "House-made sweets", 3);
            Category drinks = category(categories, "Drinks", "Soft drinks, coffee and juices", 4);

            item(items, starters, "Garlic Focaccia", "Rosemary focaccia, roasted garlic butter",
                    "5.50", null, 10, true, 0);
            item(items, starters, "Chilli Paneer Bites", "Crisp paneer tossed in a chilli glaze",
                    "7.25", 20, 12, true, 2);
            item(items, mains, "Margherita Pizza", "San Marzano tomato, fior di latte, basil",
                    "11.90", null, 18, true, 0);
            item(items, mains, "Butter Chicken", "Tandoori chicken in a tomato and fenugreek gravy",
                    "14.50", 15, 25, false, 1);
            item(items, mains, "Lamb Seekh Platter", "Charcoal-grilled lamb skewers, mint chutney",
                    "17.75", 8, 30, false, 2);
            item(items, desserts, "Tiramisu", "Mascarpone, espresso-soaked savoiardi",
                    "6.40", 12, 5, true, 0);
            item(items, drinks, "Fresh Lime Soda", "Lime, soda, sweet or salted",
                    "3.20", null, 3, true, 0);
            item(items, drinks, "Filter Coffee", "South Indian style, served hot",
                    "2.80", null, 4, true, 0);

            log.info("Seeded {} categories and {} menu items", categories.count(), items.count());
        };
    }

    private Category category(CategoryRepository repository, String name, String description, int order) {
        Category category = new Category();
        category.setName(name);
        category.setDescription(description);
        category.setDisplayOrder(order);
        category.setActive(true);
        return repository.save(category);
    }

    private void item(MenuItemRepository repository, Category category, String name, String description,
                      String price, Integer stock, int prepMinutes, boolean vegetarian, int spiceLevel) {
        MenuItem item = new MenuItem();
        item.setCategory(category);
        item.setName(name);
        item.setDescription(description);
        item.setPrice(new BigDecimal(price));
        item.setStockQuantity(stock);
        item.setPreparationMinutes(prepMinutes);
        item.setVegetarian(vegetarian);
        item.setSpiceLevel(spiceLevel);
        item.setAvailable(true);
        item.setActive(true);
        repository.save(item);
    }
}
