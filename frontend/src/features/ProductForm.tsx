import { useState } from "react"
import type { SubmitEvent } from "react"
import type { Product } from "../api/types"
import { createProduct, updateProduct } from "../api/products"
import type { ApiError } from "../api/client"
import ErrorMessage from "../components/ErrorMessage"
import Button from "../components/Button"
import Field from "../components/Field"

export default function ProductForm({product, onSuccess}: {
    product?: Product
    onSuccess: (product: Product) => void
}) {
    const isEdit = product !== undefined
    const [sku, setSku] = useState(product?.sku ?? '')
    const [name, setName] = useState(product?.name ?? '')
    const [description, setDescription] = useState(product?.description ?? '')
    const [unit, setUnit] = useState(product?.unit ?? '')
    const [reorderLevel, setReorderLevel] = useState(product ? String(product.reorderLevel) : '')
    const [error, setError] = useState<ApiError | null>(null)
    const [submitting, setSubmitting] = useState(false)

    async function handleSubmit(event: SubmitEvent) {
        event.preventDefault()
        setSubmitting(true)
        setError(null)
        try {
            const body = { name, description, unit, reorderLevel: Number(reorderLevel) }
            const saved = isEdit
                ? await updateProduct(product.id, body)
                : await createProduct({ sku, ...body })
            onSuccess(saved)
        } catch (err) {
            setError(err as ApiError)
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <form onSubmit={handleSubmit} className="mt-6 space-y-3">
            <Field id="sku" label="SKU" type="text" required readOnly={isEdit} value={sku} onChange={(e) => setSku(e.target.value)} />
            <Field id="name" label="Name" type="text" required value={name} onChange={(e) => setName(e.target.value)} />
            <Field id="description" label="Description" type="text" required value={description}
                onChange={(e) => setDescription(e.target.value)} />
            <Field id="unit" label="Unit" type="text" required value={unit} onChange={(e) => setUnit(e.target.value)} />
            <Field id="reorderLevel" label="Reorder level" type="number" min="0" required value={reorderLevel}
                onChange={(e) => setReorderLevel(e.target.value)} />

            {error && <ErrorMessage error={error} />}
            {error?.errors && (
                <ul className="list-disc pl-5 text-red-700">
                    {Object.entries(error.errors).map(([field, message]) => (
                        <li key={field}>{field}: {message}</li>
                    ))}
                </ul>
            )}

            <Button type="submit" disabled={submitting}>{isEdit ? 'Save changes' : 'Create product'}</Button>
        </form>
    )
}
