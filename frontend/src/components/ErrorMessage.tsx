import type { ApiError } from "../api/client";

export default function ErrorMessage({ error }: {error: ApiError}) {
    return (
        <p role="alert" className="text-red-700">
            {error.detail}
        </p>
    );
}