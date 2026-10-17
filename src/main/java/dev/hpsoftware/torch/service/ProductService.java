package dev.hpsoftware.torch.service;

import dev.hpsoftware.torch.dto.CreateProductRequest;
import dev.hpsoftware.torch.dto.ProductResponse;
import dev.hpsoftware.torch.exception.DuplicateSkuException;
import dev.hpsoftware.torch.model.Product;
import dev.hpsoftware.torch.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final Clock clock;

    public ProductService(ProductRepository productRepository, Clock clock) {
        this.productRepository = productRepository;
        this.clock = clock;
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        if(productRepository.existsBySku(request.sku())) {
            throw new DuplicateSkuException(request.sku());
        }

        Product product = new Product(
                request.sku(),
                request.name(),
                request.description(),
                request.unit(),
                request.reorderLevel(),
                clock.instant()
        );

        return ProductResponse.from(productRepository.save(product));
    }



}
