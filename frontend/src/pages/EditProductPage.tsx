import { Link, useNavigate, useParams } from "react-router"
import { useApi } from "../hooks/useApi"
import { getProduct } from "../api/products"
import Loading from "../components/Loading"
import ErrorMessage from "../components/ErrorMessage"
import ProductForm from "../features/ProductForm"

export default function EditProductPage() {
  const { id } = useParams()
  const productId = Number(id)
  const navigate = useNavigate()
  const { data: product, error, loading } = useApi(() => getProduct(productId), [productId])

  return (
    <div>
      <Link to={`/products/${productId}`}>&larr; Back to product</Link>
      <h1 className="text-2xl font-bold">Edit product</h1>
      <p className="mt-2">The SKU and the stock level cannot be changed here. Use receive/issue to change stock.</p>
      {loading && !product && <Loading />}
      {error && <ErrorMessage error={error} />}
      {product && <ProductForm product={product} onSuccess={() => navigate(`/products/${productId}`)} />}
    </div>
  )
}
