import { Link, useParams } from 'react-router'
import { useApi } from '../hooks/useApi'
import { getProduct, listMovements } from '../api/products'
import Loading from '../components/Loading'
import ErrorMessage from '../components/ErrorMessage'
import LowStockBadge from '../components/LowStockBadge'
import MovementForm from '../features/MovementForm'
import MovementHistory from '../features/MovementHistory'

export default function ProductDetailPage() {
  const { id } = useParams()
  const productId = Number(id)
  const { data: product, error, loading, reload: reloadProduct } = useApi(() => getProduct(productId), [productId])
  const { data: history, error: historyError, reload: reloadHistory } = useApi(() => listMovements(productId, 0, 20), [productId])

  function handleMovementSaved() {
    reloadProduct()
    reloadHistory()
  }

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

      {product && <MovementForm productId={product.id} onSuccess={handleMovementSaved} />}
      {product && (
        <section className='mt-6'>
          <h2 className='text-lg font-semibold'>Recent movements</h2>
          {historyError && <ErrorMessage error={historyError}/>}
          {history && <MovementHistory movements={history.content}/>}
        </section>
      )}
    </div>
  )
}
