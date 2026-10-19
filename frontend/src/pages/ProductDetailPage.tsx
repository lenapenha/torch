import { Link, useParams } from 'react-router'
import { useApi } from '../hooks/useApi'
import { getProduct } from '../api/products'
import Loading from '../components/Loading'
import ErrorMessage from '../components/ErrorMessage'
import LowStockBadge from '../components/LowStockBadge'
import MovementForm from '../features/MovementForm'

export default function ProductDetailPage() {
  const { id } = useParams()
  const productId = Number(id)
  const { data: product, error, loading, reload } = useApi(() => getProduct(productId), [productId])

  return (
    <div>
      <Link to="/">&larr; Back to products</Link>
      {loading && !product && <Loading/>}
      {error && <ErrorMessage error={error}/>}

      {product && (
        <div className='mt-4'>
          <h1 className="text-2xl font-bold">{product.name}</h1>
          <p>SKU: {product.sku}</p>                                                                                                      
          <p>{product.description}</p>                                                                                                   
          <p>Unit: {product.unit}</p>                                                                                                    
          <p>Reorder level: {product.reorderLevel}</p>                                                                                   
          <p className="mt-2 text-xl">                                                                                                   
              In stock: {product.quantityOnHand} {product.unit}{' '}                                                                     
              <LowStockBadge product={product} />                                                                                        
          </p>                                   
        </div>
      )}

      {product && <MovementForm productId={product.id} onSuccess={reload} />}
    </div>
  )
}
