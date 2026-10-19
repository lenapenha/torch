import { Navigate, Route, Routes } from 'react-router'
import ProductDetailPage from './pages/ProductDetailPage'
import ProductListPage from './pages/ProductListPage'
import NewProductPage from './pages/NewProductPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<ProductListPage />} />
      <Route path="/products/new" element={<NewProductPage />} />
      <Route path="/products/:id" element={<ProductDetailPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
