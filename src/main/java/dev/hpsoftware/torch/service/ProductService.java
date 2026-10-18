package dev.hpsoftware.torch.service;

import dev.hpsoftware.torch.dto.CreateProductRequest;
import dev.hpsoftware.torch.dto.ProductFilter;
import dev.hpsoftware.torch.dto.ProductResponse;
import dev.hpsoftware.torch.dto.UpdateProductRequest;
import dev.hpsoftware.torch.exception.DuplicateSkuException;
import dev.hpsoftware.torch.exception.ResourceNotFoundException;
import dev.hpsoftware.torch.model.Product;
import dev.hpsoftware.torch.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

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

    public ProductResponse getProduct(Long id) {
        return ProductResponse.from(productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id " + id + " not found")));
    }

    public List<ProductResponse> listProducts(ProductFilter filter) {
        String sku = filter.sku();
        if(sku != null && !sku.isBlank() ) {
            return productRepository.findBySku(sku)
                    .map(ProductResponse::from)
                    .stream().toList();
        }

        String q = filter.q();
        boolean hasQuery = q != null && !q.isBlank();
        boolean lowStock = Boolean.TRUE.equals(filter.lowStock());

        if (lowStock) {
            List<Product> products = hasQuery
                    ? productRepository.searchLowStock(q.trim())
                    : productRepository.findLowStock();
            return products.stream().map(ProductResponse::from).toList();
        }

        if (hasQuery) {
            return productRepository.search(q.trim()).stream()
                    .map(ProductResponse::from)
                    .toList();
        }

        return productRepository.findByActiveTrueOrderByNameAsc()
            .stream()
            .map(ProductResponse::from)
            .toList();
    }

    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id " + id + " not found"));

        product.setName(request.name());
        product.setDescription(request.description());
        product.setUnit(request.unit());
        product.setReorderLevel(request.reorderLevel());
        //productRepository.save(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public void deactivateProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id " + id + " not found"));
        product.setActive(false);
//        productRepository.save(product);
    }
}
