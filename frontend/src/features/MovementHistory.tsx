import type { Movement } from "../api/types";
import EmptyState from "../components/EmptyState";

export default function MovementHistory({ movements }: {movements: Movement[]}) {
    if (movements.length === 0) {
        return <EmptyState message="No movements yet."/>
    }

    return (
        <table className="mt-4 w-full overflow-hidden rounded bg-white text-left shadow-sm">
            <thead className="bg-gray-100 text-sm text-gray-600">
                <tr>
                    <th className="px-3 py-2">When</th>
                    <th className="px-3 py-2">Type</th>
                    <th className="px-3 py-2">Change</th>
                    <th className="px-3 py-2">Balance after</th>
                    <th className="px-3 py-2">Note</th>
                </tr>
            </thead>
            <tbody className="divide-y">
                {movements.map((movement) => (
                    <tr key={movement.id}>
                        <td className="px-3 py-2">{new Date(movement.createdAt).toLocaleString()}</td>
                        <td className="px-3 py-2">{movement.type}</td>
                        <td className={`px-3 py-2 ${movement.quantityDelta > 0 ? 'text-green-700' : 'text-red-700'}`}>{movement.quantityDelta > 0 ? '+' : ''}{movement.quantityDelta}</td>
                        <td className="px-3 py-2">{movement.balanceAfter}</td>
                        <td className="px-3 py-2">{movement.note}</td>
                    </tr>
                ))}
            </tbody>
        </table>
    )
}