import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { recordMovement } from '../api/products'
import MovementForm from './MovementForm'

// fake the API module; the real ApiError class from client.ts is not affected
vi.mock('../api/products')

describe('MovementForm', () => {
    it('shows the server message when an issue is rejected, and keeps the form usable', async () => {
        // the backend answers 409 with this detail when stock is too low
        vi.mocked(recordMovement).mockRejectedValue(
            new ApiError(409, 'Insufficient stock: requested 100, available 5'),
        )
        const onSuccess = vi.fn()
        // userEvent simulates a real user: focus, typing key by key, clicking
        const user = userEvent.setup()

        render(<MovementForm productId={9} onSuccess={onSuccess} />)

        await user.selectOptions(screen.getByLabelText('Type'), 'ISSUE')
        await user.type(screen.getByLabelText('Quantity'), '100')
        await user.click(screen.getByRole('button', { name: 'Save movement' }))

        // the request was sent with the values from the form
        expect(recordMovement).toHaveBeenCalledWith(9, { type: 'ISSUE', quantity: 100, note: undefined })
        // the server's own message is shown (role="alert" comes from ErrorMessage)
        expect(await screen.findByRole('alert')).toHaveTextContent('Insufficient stock: requested 100, available 5')
        // a rejected movement must not trigger the reload of the product
        expect(onSuccess).not.toHaveBeenCalled()
        // the form is still usable: the typed quantity is kept and the button is enabled again
        expect(screen.getByLabelText('Quantity')).toHaveValue(100)
        expect(screen.getByRole('button', { name: 'Save movement' })).toBeEnabled()
    })
})
