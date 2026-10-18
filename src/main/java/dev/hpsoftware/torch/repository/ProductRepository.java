package dev.hpsoftware.torch.repository;

import dev.hpsoftware.torch.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    List<Product> findByActiveTrueOrderByNameAsc();

    @Query("""
        select p from Product p
            where p.active = true
                and (lower(p.name) like lower(concat('%', :q, '%') ) 
                    or lower(p.sku) like  lower(concat('%', :q, '%') ) )
             order by p.name asc 
        """)
    List<Product> search(@Param("q") String query);

    @Query("""                                                                      
      select p from Product p
          where p.active = true
            and p.quantityOnHand <= p.reorderLevel
          order by p.name asc
      """)
    List<Product> findLowStock();

    @Query("""                                                                      
    select p from Product p
        where p.active = true
            and p.quantityOnHand <= p.reorderLevel
            and (lower(p.name) like lower(concat('%', :q, '%'))
                or lower(p.sku) like lower(concat('%', :q, '%')))
        order by p.name asc
    """)
    List<Product> searchLowStock(@Param("q") String query);
}
