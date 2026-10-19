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
      <Link to="/products/new" className="mt-4 inline-block rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700">New product</Link>
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
        <table className="mt-4 w-full overflow-hidden rounded bg-white text-left shadow-sm">
          <thead className="bg-gray-100 text-sm text-gray-600">
            <tr>
              <th className="px-3 py-2">Name</th>
              <th className="px-3 py-2">SKU</th>
              <th className="px-3 py-2">Stock</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {products.map((product) => (
              <tr key={product.id}>
                <td className="px-3 py-2"><Link to={`/products/${product.id}`}>{product.name}</Link></td>
                <td className="px-3 py-2">{product.sku}</td>
                <td className="px-3 py-2">{product.quantityOnHand} {product.unit} <LowStockBadge product={product}/></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
