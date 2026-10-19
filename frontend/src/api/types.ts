export type MovementType = 'RECEIPT' | 'ISSUE'                                           
                                                                                           
export interface Product {                                                               
id: number; sku: string; name: string; description: string; unit: string               
reorderLevel: number; quantityOnHand: number; active: boolean; createdAt: string       
}                                                                                        
export interface Movement {                                                              
id: number; productId: number; type: MovementType | 'ADJUSTMENT'                       
quantityDelta: number; balanceAfter: number; note: string | null; createdAt: string    
}                                                                                        
export interface PageResponse<T> {                                                       
content: T[]; page: number; size: number; totalElements: number; totalPages: number    
}

export interface CreateProductBody {
    sku: string
    name: string
    description: string
    unit: string
    reorderLevel: number
}

export type UpdateProductBody = Omit<CreateProductBody, 'sku'>

export interface MovementBody {
    type: MovementType
    quantity: number
    note?: string
}
