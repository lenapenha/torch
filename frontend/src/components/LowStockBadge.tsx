import type { Product } from "../api/types";

export default function LowStockBadge({product}: {product: Product}) {
    if(product.quantityOnHand === 0) {
        return <span className="rounded bg-red-100 px-2 py-0.5 text-sm text-red-800">
            Out of stock
            </span>
    }

    if(product.quantityOnHand <= product.reorderLevel) {
        return <span className="rounded bg-amber-100 px-2 py-0.5 text-sm text-amber-800">
            Low stock
            </span>
    }

    return null
}