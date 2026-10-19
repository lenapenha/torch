import { useState } from "react"
import type { SubmitEvent } from "react"
import type { Product } from "../api/types"
import { createProduct } from "../api/products"
import type { ApiError } from "../api/client"
import ErrorMessage from "../components/ErrorMessage"

export default function ProductForm({onSuccess}: {
    onSuccess: (product: Product) => void
}) {
    const [sku, setSku] = useState('')
    const [name, setName] = useState('')
    const [description, setDescription] = useState('')
    const [unit, setUnit] = useState('')
    const [reorderLevel, setReorderLevel] = useState('')
    const [error, setError] = useState<ApiError | null>(null)
    const [submitting, setSubmitting] = useState(false)

    async function handleSubmit(event: SubmitEvent) {
        event.preventDefault()
        setSubmitting(true)
        setError(null)
        try {
            const created = await createProduct({
                sku, name, description, unit, reorderLevel: Number(reorderLevel),
            })
            onSuccess(created)
        } catch (err) {
            setError(err as ApiError)
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <form onSubmit={handleSubmit} className="mt-6 space-y-3">
            <div>
                <label htmlFor="sku">SKU</label>
                <input id="sku" type="text" required value={sku} onChange={(e) => setSku(e.target.value)} />
            </div>
            <div>
                <label htmlFor="name">Name</label>
                <input id="name" type="text" required value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div>
                <label htmlFor="description">Description</label>
                <input id="description" type="text" required value={description}
                    onChange={(e) => setDescription(e.target.value)} />
            </div>
            <div>
                <label htmlFor="unit">Unit</label>
                <input id="unit" type="text" required value={unit} onChange={(e) => setUnit(e.target.value)} />
            </div>
            <div>
                <label htmlFor="reorderLevel">Reorder level</label>
                <input id="reorderLevel" type="number" min="0" required value={reorderLevel}
                    onChange={(e) => setReorderLevel(e.target.value)} />
            </div>

            {error && <ErrorMessage error={error} />}
            {error?.errors && (
                <ul>
                    {Object.entries(error.errors).map(([field, message]) => (
                        <li key={field}>{field}: {message}</li>
                    ))}
                </ul>
            )}

            <button type="submit" disabled={submitting}>Create product</button>
        </form>
    )
}
