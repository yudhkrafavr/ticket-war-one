import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { Activity, LayoutDashboard, LogOut, Ticket } from 'lucide-react'
import { useAdminAuth } from '../contexts/AdminAuthContext'

const navigation = [
  { to: '/admin', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/admin/events', label: 'Events', icon: Ticket },
]

export function AdminLayout() {
  const navigate = useNavigate()
  const { signOut } = useAdminAuth()

  function exitAdmin() {
    signOut()
    navigate('/admin/login', { replace: true })
  }

  return (
    <div className="admin-shell">
      <aside className="sidebar">
        <a className="brand" href="/admin"><span className="brand-mark"><Activity size={18} /></span><span>ticket<span className="brand-accent">war</span><small>MINI GAME</small></span></a>
        <div className="sidebar-label">MENU</div>
        <nav className="sidebar-nav" aria-label="Admin navigation">
          {navigation.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              <Icon size={18} /><span>{label}</span>
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="admin-main">
        <header className="topbar"><span>Online Ticket War</span><button className="button button-secondary button-small" onClick={exitAdmin}><LogOut size={14} /> Sign out</button></header>
        <main className="content-area"><Outlet /></main>
      </div>
    </div>
  )
}
