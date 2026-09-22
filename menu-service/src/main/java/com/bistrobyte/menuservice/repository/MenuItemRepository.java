package com.bistrobyte.menuservice.repository;

import com.bistrobyte.menuservice.domain.MenuItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    boolean existsByNameIgnoreCaseAndCategoryId(String name, Long categoryId);

    List<MenuItem> findByIdInAndActiveTrue(List<Long> ids);

    long countByCategoryIdAndActiveTrue(Long categoryId);

    /**
     * Catalogue query used by both the customer app and the staff console. Every filter is
     * optional; {@code orderableOnly} hides dishes that are inactive, 86'd or out of stock.
     */
    @Query("""
            SELECT i FROM MenuItem i
            WHERE (:categoryId IS NULL OR i.category.id = :categoryId)
              AND (:includeInactive = TRUE OR i.active = TRUE)
              AND (:orderableOnly = FALSE
                   OR (i.active = TRUE AND i.available = TRUE
                       AND (i.stockQuantity IS NULL OR i.stockQuantity > 0)))
              AND (:vegetarianOnly = FALSE OR i.vegetarian = TRUE)
              AND (:search IS NULL
                   OR LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(i.description) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<MenuItem> search(@Param("categoryId") Long categoryId,
                          @Param("search") String search,
                          @Param("orderableOnly") boolean orderableOnly,
                          @Param("vegetarianOnly") boolean vegetarianOnly,
                          @Param("includeInactive") boolean includeInactive,
                          Pageable pageable);

    /**
     * Row-level lock taken while reserving stock, so two concurrent orders for the last
     * portion cannot both succeed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM MenuItem i WHERE i.id = :id")
    Optional<MenuItem> findByIdForUpdate(@Param("id") Long id);
}
