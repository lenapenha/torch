import { Link, useNavigate } from "react-router"
import ProductForm from "../features/ProductForm"

export default function NewProductPage() {
  const navigate = useNavigate()

  return (
    <div>
      <Link to="/">← Products</Link>
      <h1 className="text-2xl font-bold">New product</h1>
      <p className="mt-2">Stock starts at 0. Receive stock after creating the product.</p>
      <ProductForm onSuccess={(product) => navigate(`/products/${product.id}`)} />
    </div>
  )
}
