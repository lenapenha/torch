import type { Movement } from "../api/types";
import EmptyState from "../components/EmptyState";

export default function MovementHistory({ movements }: {movements: Movement[]}) {
    if (movements.length === 0) {
        return <EmptyState message="No movements yet."/>
    }

    return (
        <table className="mt-2 w-full text-left">
            <thead>
                <tr>
                    <th>When</th>
                    <th>Type</th>
                    <th>Change</th>
                    <th>Balance after</th>
                    <th>Note</th>
                </tr>
            </thead>
            <tbody>
                {movements.map((movement) => (
                    <tr key={movement.id}>
                        <td>{new Date(movement.createdAt).toLocaleString()}</td>
                        <td>{movement.type}</td>
                        <td>{movement.quantityDelta > 0 ? '+' : ''}{movement.quantityDelta}</td>
                        <td>{movement.balanceAfter}</td>
                        <td>{movement.note}</td>
                    </tr>
                ))}
            </tbody>
        </table>
    )
}