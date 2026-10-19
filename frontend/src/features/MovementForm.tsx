import { useState } from "react"
import type { SubmitEvent } from "react"
import type { MovementType } from "../api/types"
import { ApiError } from "../api/client"
import { recordMovement } from "../api/products"
import ErrorMessage from "../components/ErrorMessage"
import Button from "../components/Button"
import Field from "../components/Field"

export default function MovementForm({productId, onSuccess}: {
    productId: number
    onSuccess: () => void
}) {
    const [type, setType] = useState<MovementType>('RECEIPT')
    const [quantity, setQuantity] = useState('')
    const [note, setNote] = useState('')
    const [error, setError] = useState<ApiError | null>(null)
    const [submitting, setSubmitting] = useState(false)

    async function handleSubmit(event:SubmitEvent) {
        event.preventDefault()
        setSubmitting(true)
        setError(null)
        try {
            await recordMovement(productId, {type, quantity: Number(quantity), note: note || undefined})
            setQuantity('')
            setNote('')
            onSuccess()
        } catch (err) {
            setError(err as ApiError)
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <form onSubmit={handleSubmit} className="mt-6 space-y-3">
            <h2 className="text-lg font-semibold">Record stock movement</h2>

            <div>
                <label htmlFor="type" className="block text-sm font-medium">Type</label>
                <select id="type" value={type} onChange={(e) => setType(e.target.value as MovementType)}
                    className="mt-1 block w-full rounded border border-gray-300 bg-white px-3 py-2">
                    <option value='RECEIPT'>Receive stock</option>
                    <option value='ISSUE'>Issue stock</option>
                </select>
            </div>

            <Field id="quantity" label="Quantity" type="number" min="1" required value={quantity}
                onChange={(e) => setQuantity(e.target.value)} />
            <Field id="note" label="Note (optional)" type="text" value={note}
                onChange={(e) => setNote(e.target.value)} />
            {error && <ErrorMessage error={error} />}                                                                                              
            {error?.errors && (                                                                                                                    
                <ul className="list-disc pl-5 text-red-700">                                                                                                                               
                    {Object.entries(error.errors).map(([field, message]) => (                                                                      
                        <li key={field}>{field}: {message}</li>                                                                                    
                    ))}                                                                                                                            
                </ul>                                                                                                                              
            )}

            <Button type="submit" disabled={submitting}>Save movement</Button>
        </form>
    )
}