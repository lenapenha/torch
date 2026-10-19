import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { updateProduct } from '../api/products'
import type { Product } from '../api/types'
import ProductForm from './ProductForm'

vi.mock('../api/products')

const product: Product = {
    id: 7, sku: 'PEN-BLK', name: 'Black pen', description: 'Ballpoint', unit: 'pcs',
    reorderLevel: 10, quantityOnHand: 42, active: true, createdAt: '2026-10-01T00:00:00Z',
}

describe('ProductForm (edit mode)', () => {
    it('prefills the product, keeps the SKU read-only and sends the update without the SKU', async () => {
        vi.mocked(updateProduct).mockResolvedValue({ ...product, name: 'Blue pen' })
        const onSuccess = vi.fn()
        const user = userEvent.setup()

        render(<ProductForm product={product} onSuccess={onSuccess} />)

        expect(screen.getByLabelText('SKU')).toHaveValue('PEN-BLK')
        expect(screen.getByLabelText('SKU')).toHaveAttribute('readonly')

        await user.clear(screen.getByLabelText('Name'))
        await user.type(screen.getByLabelText('Name'), 'Blue pen')
        await user.click(screen.getByRole('button', { name: 'Save changes' }))

        expect(updateProduct).toHaveBeenCalledWith(7, {
            name: 'Blue pen', description: 'Ballpoint', unit: 'pcs', reorderLevel: 10,
        })
        expect(onSuccess).toHaveBeenCalledWith(expect.objectContaining({ name: 'Blue pen' }))
    })
})
