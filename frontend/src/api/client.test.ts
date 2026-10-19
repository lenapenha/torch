import { afterEach, describe, expect, it, vi } from "vitest"
import { ApiError, request } from "./client"

function mockFetch(status: number, body: unknown) {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
        new Response(JSON.stringify(body), {
            status,
            headers: { 'Content-Type': 'application/problem+json' },
        }),
    ))
}

afterEach(() => vi.unstubAllGlobals())

describe('request', () => {
    it('throws ApiError with the server detail on 409', async () => {
        mockFetch(409, { status: 409, title: 'Conflict', detail: 'Insufficient stock' })

        const error = await request('/products/9/movements').catch((e) => e)

        expect(error).toBeInstanceOf(ApiError)
        expect(error).toMatchObject({ status: 409, detail: 'Insufficient stock' })
    })

    it('exposes per-field errors on 400', async () => {
        mockFetch(400, { status: 400, detail: 'Validation failed', errors: { sku: 'must not be blank' } })

        await expect(request('/products')).rejects.toMatchObject({
            status: 400,
            errors: { sku: 'must not be blank' },
        })
    })
})
