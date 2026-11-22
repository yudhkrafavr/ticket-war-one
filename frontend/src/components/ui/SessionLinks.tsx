import { Link } from 'react-router-dom'
import { ArrowUpRight } from 'lucide-react'

export function SessionLinks({ id }: { id: string }) {
  return (
    <div className="quick-links">
      <Link to={`/register/${id}`}>Registration page <ArrowUpRight size={14} /></Link>
      <Link to={`/admin/war-sessions/${id}/participants`}>Participants <ArrowUpRight size={14} /></Link>
      <Link to={`/admin/war-sessions/${id}/leaderboard`}>Leaderboard <ArrowUpRight size={14} /></Link>
    </div>
  )
}
