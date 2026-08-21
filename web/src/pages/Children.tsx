import useSWR from 'swr'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import type { components } from '../api/schema'

type ChildResponse = components['schemas']['ChildResponse']

export function Children() {
  const { data, error } = useSWR<ChildResponse[]>('/v1/children', api.get)

  if (error) return <p role="alert">failed to load children</p>
  if (!data) return <p>Loading…</p>
  if (data.length === 0) {
    return <p className="p-6">No children yet. Add one to pair a device.</p>
  }

  return (
    <ul className="flex flex-col gap-2 p-6">
      {data.map((child) => (
        <li key={child.id}>
          <Link className="text-sky-700 underline" to={`/children/${child.id}`}>
            {child.display_name}
          </Link>
        </li>
      ))}
    </ul>
  )
}
