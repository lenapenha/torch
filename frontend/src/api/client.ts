export class ApiError extends Error {
    readonly status: number
    readonly detail: string
    readonly errors?: Record<string, string>

    constructor(status: number, detail: string, errors?: Record<string, string>) {
        super(detail)
        this.name = 'ApiError'
        this.status = status
        this.detail = detail
        this.errors = errors
    }
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const res = await fetch(`/api${path}`, {
        ...options,
        headers: { 'Content-Type': 'application/json', ...options.headers },
    })

    if (!res.ok) {
        // body is a ProblemDetail (RFC 7807), but a proxy error page may not be JSON
        const problem = await res.json().catch(() => null)
        throw new ApiError(res.status, problem?.detail ?? res.statusText, problem?.errors)
    }

    if (res.status === 204) {
        return undefined as T
    }
    return res.json()
}
