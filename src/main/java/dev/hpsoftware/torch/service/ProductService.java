package dev.hpsoftware.torch.service;

import dev.hpsoftware.torch.dto.CreateProductRequest;
import dev.hpsoftware.torch.dto.ProductResponse;
import dev.hpsoftware.torch.repository.ProductRepository;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductResponse createProduct(CreateProductRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }


}
