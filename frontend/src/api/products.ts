import { request } from './client'
import type {
    CreateProductBody,
    Movement,
    MovementBody,
    PageResponse,
    Product,
    UpdateProductBody,
} from './types'

export function listProducts(filter: { q?: string; lowStock?: boolean } = {}) {
    const params = new URLSearchParams()
    if (filter.q) params.set('q', filter.q)
    if (filter.lowStock) params.set('lowStock', 'true')
    const query = params.size > 0 ? `?${params}` : ''
    return request<Product[]>(`/products${query}`)
}

export function getProduct(id: number) {
    return request<Product>(`/products/${id}`)
}

export function createProduct(body: CreateProductBody) {
    return request<Product>('/products', { method: 'POST', body: JSON.stringify(body) })
}

export function updateProduct(id: number, body: UpdateProductBody) {
    return request<Product>(`/products/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function recordMovement(id: number, body: MovementBody) {
    return request<Movement>(`/products/${id}/movements`, {
        method: 'POST',
        body: JSON.stringify(body),
    })
}

export function listMovements(id: number, page = 0, size = 20) {
    return request<PageResponse<Movement>>(`/products/${id}/movements?page=${page}&size=${size}`)
}
