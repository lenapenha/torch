import { render, screen } from '@testing-library/react'                                        
import { MemoryRouter } from 'react-router'                                                    
import { describe, expect, it, vi } from 'vitest'                                              
import { listProducts } from '../api/products'                                                 
import type { Product } from '../api/types'                                                    
import ProductListPage from './ProductListPage'

// replace the real API module with fakes, so no backend is needed
vi.mock('../api/products')

// a helper that builds a product; each test only states the fields it cares about
function product(overrides: Partial<Product>): Product {
    return {
        id: 1, sku: 'SKU-1', name: 'Product', description: 'desc', unit: 'pcs',                
        reorderLevel: 10, quantityOnHand: 50, active: true, createdAt: '2026-10-18T10:00:00Z', 
        ...overrides
    }
}

describe('ProductListPage', () => {
    it('shows the products with low-stock and out-of-stock badges', async () => {
        vi.mocked(listProducts).mockResolvedValue([
            product({id:1, name: 'Healthy item', quantityOnHand:50, reorderLevel:10}),
            product({ id: 2, name: 'Low item', quantityOnHand: 10, reorderLevel: 10 }),         
            product({ id: 3, name: 'Empty item', quantityOnHand: 0, reorderLevel: 10 }),
        ])
        render(<MemoryRouter><ProductListPage/></MemoryRouter>)

        expect(await screen.findByText('Healthy item')).toBeInTheDocument()
        expect(screen.getByText('Low item')).toBeInTheDocument()
        expect(screen.getByText('Empty item')).toBeInTheDocument()

        expect(screen.getAllByText('Low stock')).toHaveLength(1)
        expect(screen.getAllByText('Out of stock')).toHaveLength(1)
    })
})