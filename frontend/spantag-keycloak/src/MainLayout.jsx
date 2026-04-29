import ProfilePage  from './ProfilePage.jsx'
import OrdersPage   from './OrdersPage.jsx'
import PaymentsPage from './PaymentsPage.jsx'
import AdminPage    from './AdminPage.jsx'

export default function MainLayout({
  profile, setProfile, accessToken, authFetch,
  page, setPage, onLogout
}) {
  const isAdmin = profile.role === 'ROLE_ADMIN' ||
                  (profile.roles && profile.roles.includes('ROLE_ADMIN'))

  const nav = [
    { id: 'profile',  label: 'Profile',  icon: UserIcon },
    { id: 'orders',   label: 'Orders',   icon: OrderIcon },
    { id: 'payments', label: 'Payments', icon: PayIcon },
    ...(isAdmin ? [{ id: 'admin', label: 'Admin', icon: AdminIcon }] : []),
  ]

  return (
    <div className="layout">
      {/* ── Sidebar ───────────────────────── */}
      <aside className="sidebar">
        <div className="sidebar-brand">
          <span className="logo-mark" style={{fontSize:'20px',marginBottom:0}}>⬡</span>
          <span className="sidebar-brand-name">SpanTag</span>
        </div>

        <nav className="sidebar-nav">
          {nav.map(item => (
            <button
              key={item.id}
              className={`nav-item ${page === item.id ? 'nav-item--active' : ''}`}
              onClick={() => setPage(item.id)}
            >
              <item.icon />
              <span>{item.label}</span>
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="sidebar-user">
            {profile.avatar
              ? <img src={profile.avatar} alt="" className="sidebar-avatar"/>
              : <div className="sidebar-avatar-placeholder">
                  {(profile.displayName || profile.username || '?')[0].toUpperCase()}
                </div>
            }
            <div className="sidebar-user-info">
              <span className="sidebar-username">{profile.displayName || profile.username}</span>
              <span className="sidebar-role">{profile.roleLabel || 'USER'}</span>
            </div>
          </div>
          <button className="nav-item nav-item--logout" onClick={onLogout}>
            <LogoutIcon /> <span>Sign out</span>
          </button>
        </div>
      </aside>

      {/* ── Main Content ──────────────────── */}
      <main className="main-content">
        {page === 'profile'  && (
          <ProfilePage
            profile={profile}
            setProfile={setProfile}
            authFetch={authFetch}
          />
        )}
        {page === 'orders'   && <OrdersPage   authFetch={authFetch} />}
        {page === 'payments' && <PaymentsPage authFetch={authFetch} />}
        {page === 'admin'    && <AdminPage    authFetch={authFetch} />}
      </main>

      <div className="bg-grid" aria-hidden="true"/>
    </div>
  )
}

/* ── Icons ───────────────────────────────────────────────── */
function UserIcon()   { return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7"/></svg> }
function OrderIcon()  { return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="3" width="20" height="18" rx="2"/><path d="M8 10h8M8 14h5"/></svg> }
function PayIcon()    { return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/></svg> }
function AdminIcon()  { return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg> }
function LogoutIcon() { return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg> }
