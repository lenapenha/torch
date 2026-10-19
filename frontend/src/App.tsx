import { Link, Navigate, Route, Routes } from 'react-router'
import ProductDetailPage from './pages/ProductDetailPage'
import ProductListPage from './pages/ProductListPage'
import NewProductPage from './pages/NewProductPage'

export default function App() {
  return (
    <div className="min-h-screen bg-gray-50 text-gray-900">
      <header className="border-b bg-white">
        <div className="mx-auto max-w-4xl px-6 py-4">
          <Link to="/" className="text-lg font-bold">Torch Inventory</Link>
        </div>
      </header>
      <main className="mx-auto max-w-4xl p-6">
        <Routes>
          <Route path="/" element={<ProductListPage />} />
          <Route path="/products/new" element={<NewProductPage />} />
          <Route path="/products/:id" element={<ProductDetailPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  )
}
