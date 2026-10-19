// renderHook runs a hook inside a throwaway component (hooks can't be called outside one)
// waitFor retries an assertion until async state updates land; act wraps updates we trigger ourselves
import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { useApi } from './useApi'

describe('useApi', () => {
    it('starts loading, then exposes the data', async () => {
        // vi.fn() is a fake function; mockResolvedValue makes it return a promise that resolves to this value
        const fetcher = vi.fn().mockResolvedValue(['a', 'b'])

        // render the hook; [] = no dependencies, so it fetches once on mount
        const { result } = renderHook(() => useApi(fetcher, []))

        // right after mount the promise has not resolved yet
        expect(result.current.loading).toBe(true)
        // wait until the hook finishes the request and flips loading to false
        await waitFor(() => expect(result.current.loading).toBe(false))
        // toEqual compares contents (toBe would compare references)
        expect(result.current.data).toEqual(['a', 'b'])
        expect(result.current.error).toBeNull()
    })

    it('exposes the error when the request fails', async () => {
        // mockRejectedValue makes the fake function return a promise that rejects, like a failed request
        const fetcher = vi.fn().mockRejectedValue(new ApiError(404, 'Product not found'))

        const { result } = renderHook(() => useApi(fetcher, []))

        await waitFor(() => expect(result.current.loading).toBe(false))
        // ?. because error is null until a failure happens
        expect(result.current.error?.detail).toBe('Product not found')
        // a failed request must not leave old data behind
        expect(result.current.data).toBeNull()
    })

    it('fetches again when reload is called', async () => {
        // first call resolves to 'first', second call to 'second' (Once = consumed in order)
        const fetcher = vi.fn().mockResolvedValueOnce('first').mockResolvedValueOnce('second')

        const { result } = renderHook(() => useApi(fetcher, []))
        await waitFor(() => expect(result.current.data).toBe('first'))

        // reload changes hook state, so React needs it wrapped in act
        act(() => result.current.reload())

        await waitFor(() => expect(result.current.data).toBe('second'))
        // once on mount, once on reload
        expect(fetcher).toHaveBeenCalledTimes(2)
    })

    it('ignores a stale response that arrives after a newer request', async () => {
        // a promise we resolve by hand later: this plays the slow answer to the OLD search
        let resolveSlow: (value: string) => void = () => {}
        const slow = new Promise<string>((resolve) => { resolveSlow = resolve })
        // 1st call (q='old') returns the pending promise, 2nd call (q='new') answers immediately
        const fetcher = vi.fn()
            .mockReturnValueOnce(slow)
            .mockResolvedValueOnce('fresh')

        // initialProps lets us change the dependency later, like typing in the search box
        const { result, rerender } = renderHook(({ q }) => useApi(fetcher, [q]), {
            initialProps: { q: 'old' },
        })
        // change q: the hook starts a second request while the first is still pending
        rerender({ q: 'new' })
        await waitFor(() => expect(result.current.data).toBe('fresh'))

        // now the old, slow request finally answers
        act(() => resolveSlow('stale'))

        // its late answer must be ignored, the newer data stays
        expect(result.current.data).toBe('fresh')
    })
})
