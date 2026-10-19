import { useEffect, useState } from 'react'
import type { ApiError } from '../api/client'

export function useApi<T>(fetcher: () => Promise<T>, deps: unknown[]) {
    // state = values React remembers between renders; changing one re-renders the component
    const [data, setData] = useState<T | null>(null)
    const [error, setError] = useState<ApiError | null>(null)
    const [loading, setLoading] = useState(true)
    // a counter we bump to force a new request (see reload below)
    const [reloadCount, setReloadCount] = useState(0)

    useEffect(() => {
        // set to true by the cleanup below when this request becomes outdated
        let cancelled = false

        async function load() {
            setLoading(true)
            try {
                const result = await fetcher()
                if (!cancelled) {
                    setData(result)
                    setError(null)
                }
            } catch (e) {
                // request() only ever throws ApiError
                if (!cancelled) setError(e as ApiError)
            } finally {
                if (!cancelled) setLoading(false)
            }
        }
        load()

        // cleanup: React calls this before running the effect again (or on unmount)
        // so a slow, old response is ignored if a newer request has started
        return () => {
            cancelled = true
        }
        // the list is dynamic (deps come from the caller), so the linter can't check it
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [...deps, reloadCount])

    // changing reloadCount re-runs the effect, which fetches again
    const reload = () => setReloadCount((count) => count + 1)

    return { data, error, loading, reload }
}
