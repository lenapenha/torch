import { Link } from "react-router"
import { listProducts } from "../api/products"
import EmptyState from "../components/EmptyState"
import ErrorMessage from "../components/ErrorMessage"
import Loading from "../components/Loading"
import { useApi } from "../hooks/useApi"
import LowStockBadge from "../components/LowStockBadge"
import { useState } from "react"

export default function ProductListPage() {
  const [q, setQ] = useState('')
  const {data: products, error, loading } = useApi(() => listProducts({q}), [q])

  return (
    <div>
      <h1 className="text-2xl font-bold">Products</h1>
      <Link to="/products/new">New product</Link>
      <input 
        type="search"
        placeholder="Search by product name or SKU"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        className="mt-4 block w-full rounded border px-3 py-2">
      </input>

      {loading && <Loading/>}
      {error && <ErrorMessage error={error}/>}
      {!loading && !error && products?.length === 0 && (
        <EmptyState message="No products found"/>
      )}
      {products && products.length > 0 && (
        <table className="mt-4 w-full text-left">
          <thead>
            <tr>
              <th>Name</th>
              <th>SKU</th>
              <th>Stock</th>
            </tr>
          </thead>
          <tbody>
            {products.map((product) => (
              <tr key={product.id}>
                <td><Link to={`/products/${product.id}`}>{product.name}</Link></td>
                <td>{product.sku}</td>
                <td>{product.quantityOnHand} {product.unit} <LowStockBadge product={product}/></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
