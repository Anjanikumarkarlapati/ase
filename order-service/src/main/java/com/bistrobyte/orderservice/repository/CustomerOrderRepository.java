package com.bistrobyte.orderservice.repository;

import com.bistrobyte.orderservice.domain.CustomerOrder;
import com.bistrobyte.orderservice.domain.OrderChannel;
import com.bistrobyte.orderservice.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    @EntityGraph(attributePaths = {"items", "statusHistory"})
    Optional<CustomerOrder> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"items", "statusHistory"})
    Optional<CustomerOrder> findByOrderReference(String orderReference);

    boolean existsByOrderReference(String orderReference);

    /**
     * Console and customer history query. Every filter is optional; {@code customerId} is
     * forced by the service for customers so they can only see their own tickets.
     */
    @Query("""
            SELECT o FROM CustomerOrder o
            WHERE (:customerId IS NULL OR o.customerId = :customerId)
              AND (:status IS NULL OR o.status = :status)
              AND (:channel IS NULL OR o.channel = :channel)
              AND (:from IS NULL OR o.placedAt >= :from)
              AND (:to IS NULL OR o.placedAt <= :to)
            """)
    Page<CustomerOrder> search(@Param("customerId") Long customerId,
                               @Param("status") OrderStatus status,
                               @Param("channel") OrderChannel channel,
                               @Param("from") Instant from,
                               @Param("to") Instant to,
                               Pageable pageable);

    /** Kitchen display board: oldest live ticket first. */
    @EntityGraph(attributePaths = {"items"})
    List<CustomerOrder> findByStatusInOrderByPlacedAtAsc(Collection<OrderStatus> statuses);

    long countByStatus(OrderStatus status);

    long countByChannelAndPlacedAtAfter(OrderChannel channel, Instant after);
}
