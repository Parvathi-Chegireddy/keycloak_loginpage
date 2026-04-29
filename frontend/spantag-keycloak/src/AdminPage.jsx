import { useState, useEffect, useCallback, useRef } from 'react'
import { PageHeader, StatusBadge, EmptyState } from './Components.jsx'

export default function AdminPage({ authFetch }) {
  const [tab, setTab] = useState('users')

  return (
    <div className="content-page">
      <PageHeader title="Admin Panel" subtitle="Manage users, orders and payments" />

      <div className="tabs tabs--three" style={{maxWidth:'360px',marginBottom:'20px'}}>
        {['users','orders','payments'].map(t => (
          <button key={t}
            className={`tab ${tab === t ? 'tab--active' : ''}`}
            onClick={() => setTab(t)}>
            {t.charAt(0).toUpperCase() + t.slice(1)}
          </button>
        ))}
        <span className="tab-indicator tab-indicator--three" style={{
          left: tab==='users' ? '4px' : tab==='orders' ? 'calc(33.33%)' : 'calc(66.66%)'
        }}/>
      </div>

      {tab === 'users'    && <AdminUsers    authFetch={authFetch}/>}
      {tab === 'orders'   && <AdminOrders   authFetch={authFetch}/>}
      {tab === 'payments' && <AdminPayments authFetch={authFetch}/>}
    </div>
  )
}

/* ── Admin: Users ────────────────────────────────────────── */
function AdminUsers({ authFetch }) {
  const [users,   setUsers]   = useState([])
  const [loading, setLoading] = useState(true)
  const [error,   setError]   = useState('')
  const [msg,     setMsg]     = useState('')
  const fetchRef = useRef(false)   // React 19 double-fire guard

  const load = useCallback(async () => {
    if (fetchRef.current) return
    fetchRef.current = true
    setLoading(true)
    setError('')
    try {
      const res = await authFetch('/api/admin/users')
      if (res.ok) {
        setUsers(await res.json())
      } else if (res.status === 403) {
        // ── FIX: show clear message when role is not ADMIN in Keycloak ──
        setError(
          'Access denied (403). Your account has ROLE_ADMIN in the database but ' +
          'the Keycloak JWT does not contain the "admin" realm role. ' +
          'Fix: Keycloak Admin → Users → ' + 'your username' + ' → Role mappings → ' +
          'Assign "admin" realm role → Re-login.'
        )
        fetchRef.current = false
      } else {
        setError(`Failed to load users (HTTP ${res.status})`)
        fetchRef.current = false
      }
    } catch (e) {
      setError('Network error: ' + e.message)
      fetchRef.current = false
    }
    setLoading(false)
  }, [authFetch])

  useEffect(() => { load() }, [load])

  const refresh = useCallback(() => {
    fetchRef.current = false
    load()
  }, [load])

  const toggleEnable = async (id, enabled) => {
    const res = await authFetch(`/api/admin/users/${id}/enable?enabled=${!enabled}`, { method: 'PUT' })
    if (res.ok) {
      setMsg(`User ${!enabled ? 'enabled' : 'disabled'}.`)
      setTimeout(() => setMsg(''), 2500)
      fetchRef.current = false
      load()
    }
  }

  const deleteUser = async (id, username) => {
    if (!window.confirm(`Delete user "${username}"? This cannot be undone.`)) return
    const res = await authFetch(`/api/admin/users/${id}`, { method: 'DELETE' })
    if (res.ok) {
      setMsg('User deleted.')
      setTimeout(() => setMsg(''), 2500)
      fetchRef.current = false
      load()
    }
  }

  if (loading) return <div className="content-card"><div className="skeleton-block"/></div>

  return (
    <>
      {msg && <div className="success-box" style={{marginBottom:'12px'}}>{msg}</div>}

      {/* ── Show 403 error clearly instead of "No users found" ── */}
      {error && (
        <div className="error-box" style={{marginBottom:'16px',lineHeight:'1.6'}}>
          <strong>⚠ {error}</strong>
          <div style={{marginTop:'8px'}}>
            <button className="btn-action" onClick={refresh}>↻ Retry</button>
          </div>
        </div>
      )}

      {!error && (
        <div className="table-card">
          <table className="data-table">
            <thead>
              <tr>
                <th>ID</th><th>Username</th><th>Email</th>
                <th>Provider</th><th>Roles</th><th>Status</th><th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.length === 0
                ? <tr><td colSpan="7" style={{textAlign:'center',padding:'24px',color:'var(--muted)'}}>
                    No users found
                  </td></tr>
                : users.map(u => (
                  <tr key={u.id}>
                    <td className="mono">#{u.id}</td>
                    <td>{u.username}</td>
                    <td className="muted">{u.email || '—'}</td>
                    <td><StatusBadge text={u.provider || 'local'} color="gray"/></td>
                    <td>
                      {(u.roles || []).map(r => (
                        <StatusBadge key={r} text={r.replace('ROLE_','')}
                          color={r.includes('ADMIN') ? 'purple' : 'teal'}
                          style={{marginRight:'4px'}}/>
                      ))}
                    </td>
                    <td>
                      <StatusBadge text={u.enabled ? 'Active' : 'Disabled'}
                        color={u.enabled ? 'teal' : 'red'}/>
                    </td>
                    <td>
                      <div style={{display:'flex',gap:'6px'}}>
                        <button className="btn-action"
                          onClick={() => toggleEnable(u.id, u.enabled)}>
                          {u.enabled ? 'Disable' : 'Enable'}
                        </button>
                        <button className="btn-action btn-action--danger"
                          onClick={() => deleteUser(u.id, u.username)}>
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              }
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}

/* ── Admin: All Orders ───────────────────────────────────── */
function AdminOrders({ authFetch }) {
  const [orders,  setOrders]  = useState([])
  const [loading, setLoading] = useState(true)
  const [error,   setError]   = useState('')
  const fetchRef = useRef(false)

  useEffect(() => {
    if (fetchRef.current) return
    fetchRef.current = true
    authFetch('/api/orders/admin/all')
      .then(r => {
        if (r.ok) return r.json()
        if (r.status === 403) {
          setError('Access denied — admin role not in Keycloak JWT')
          return []
        }
        return []
      })
      .then(data => { if (data.length >= 0) setOrders(data) })
      .finally(() => setLoading(false))
  }, [authFetch])

  const statusColor = { CONFIRMED:'teal', CANCELLED:'red', PROCESSING:'amber', PENDING:'gray' }

  if (loading) return <div className="content-card"><div className="skeleton-block"/></div>

  return (
    <>
      {error && <div className="error-box" style={{marginBottom:'16px'}}>{error}</div>}
      <div className="table-card">
        <table className="data-table">
          <thead>
            <tr>
              <th>ID</th><th>User</th><th>Product</th>
              <th>Qty</th><th>Amount</th><th>Status</th><th>Payment ID</th><th>Date</th>
            </tr>
          </thead>
          <tbody>
            {orders.length === 0
              ? <tr><td colSpan="8" style={{textAlign:'center',padding:'24px',color:'var(--muted)'}}>No orders</td></tr>
              : orders.map(o => (
                <tr key={o.orderId}>
                  <td className="mono">#{o.orderId}</td>
                  <td>{o.username}</td>
                  <td>{o.productName}</td>
                  <td>{o.quantity}</td>
                  <td className="mono">₹{Number(o.amount).toLocaleString()}</td>
                  <td><StatusBadge text={o.status} color={statusColor[o.status]||'gray'}/></td>
                  <td className="mono" style={{fontSize:'11px'}}>{o.paymentId ? o.paymentId.substring(0,14)+'…' : '—'}</td>
                  <td className="muted">{fmtDate(o.createdAt)}</td>
                </tr>
              ))
            }
          </tbody>
        </table>
      </div>
    </>
  )
}

/* ── Admin: All Payments ─────────────────────────────────── */
function AdminPayments({ authFetch }) {
  const [payments, setPayments] = useState([])
  const [loading,  setLoading]  = useState(true)
  const [error,    setError]    = useState('')
  const fetchRef = useRef(false)

  useEffect(() => {
    if (fetchRef.current) return
    fetchRef.current = true
    authFetch('/api/payment/admin/all')
      .then(r => {
        if (r.ok) return r.json()
        if (r.status === 403) {
          setError('Access denied — admin role not in Keycloak JWT')
          return []
        }
        return []
      })
      .then(data => { if (data.length >= 0) setPayments(data) })
      .finally(() => setLoading(false))
  }, [authFetch])

  const statusColor = { SUCCESS:'teal', FAILED:'red', CANCELLED:'amber', PENDING:'gray' }

  if (loading) return <div className="content-card"><div className="skeleton-block"/></div>

  return (
    <>
      {error && <div className="error-box" style={{marginBottom:'16px'}}>{error}</div>}
      <div className="table-card">
        <table className="data-table">
          <thead>
            <tr>
              <th>Payment ID</th><th>Order</th><th>User</th>
              <th>Amount</th><th>Status</th><th>Reason</th><th>Date</th>
            </tr>
          </thead>
          <tbody>
            {payments.length === 0
              ? <tr><td colSpan="7" style={{textAlign:'center',padding:'24px',color:'var(--muted)'}}>No payments</td></tr>
              : payments.map((p, i) => (
                <tr key={p.paymentId || i}>
                  <td className="mono" style={{fontSize:'11px'}}>{p.paymentId}</td>
                  <td className="mono">#{p.orderId}</td>
                  <td>{p.username}</td>
                  <td className="mono">₹{Number(p.amount).toLocaleString()}</td>
                  <td><StatusBadge text={p.status} color={statusColor[p.status]||'gray'}/></td>
                  <td className="muted" style={{maxWidth:'140px',overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}}>
                    {p.message || '—'}
                  </td>
                  <td className="muted">{fmtDate(p.createdAt)}</td>
                </tr>
              ))
            }
          </tbody>
        </table>
      </div>
    </>
  )
}

function fmtDate(dt) {
  if (!dt) return '—'
  return new Date(dt).toLocaleString('en-IN', {
    day:'2-digit', month:'short', hour:'2-digit', minute:'2-digit'
  })
}

// import { useState, useEffect, useCallback } from 'react'
// import { PageHeader, StatusBadge, EmptyState } from './Components.jsx'

// export default function AdminPage({ authFetch }) {
//   const [tab, setTab] = useState('users')

//   return (
//     <div className="content-page">
//       <PageHeader title="Admin Panel" subtitle="Manage users, orders and payments" />

//       <div className="tabs tabs--three" style={{maxWidth:'360px',marginBottom:'20px'}}>
//         {['users','orders','payments'].map(t => (
//           <button key={t}
//             className={`tab ${tab === t ? 'tab--active' : ''}`}
//             onClick={() => setTab(t)}>
//             {t.charAt(0).toUpperCase() + t.slice(1)}
//           </button>
//         ))}
//         <span className="tab-indicator tab-indicator--three" style={{
//           left: tab==='users' ? '4px' : tab==='orders' ? 'calc(33.33%)' : 'calc(66.66%)'
//         }}/>
//       </div>

//       {tab === 'users'    && <AdminUsers    authFetch={authFetch}/>}
//       {tab === 'orders'   && <AdminOrders   authFetch={authFetch}/>}
//       {tab === 'payments' && <AdminPayments authFetch={authFetch}/>}
//     </div>
//   )
// }

// /* ── Admin: Users ────────────────────────────────────────── */
// function AdminUsers({ authFetch }) {
//   const [users,   setUsers]   = useState([])
//   const [loading, setLoading] = useState(true)
//   const [msg,     setMsg]     = useState('')

//   const load = useCallback(async () => {
//     setLoading(true)
//     try {
//       const res = await authFetch('/api/admin/users')
//       if (res.ok) setUsers(await res.json())
//     } catch (_) {}
//     setLoading(false)
//   }, [authFetch])

//   useEffect(() => { load() }, [load])

//   const toggleEnable = async (id, enabled) => {
//     await authFetch(`/api/admin/users/${id}/enable?enabled=${!enabled}`, { method: 'PUT' })
//     setMsg(`User ${!enabled ? 'enabled' : 'disabled'}.`)
//     setTimeout(() => setMsg(''), 2500)
//     load()
//   }

//   const deleteUser = async (id, username) => {
//     if (!window.confirm(`Delete user "${username}"? This cannot be undone.`)) return
//     await authFetch(`/api/admin/users/${id}`, { method: 'DELETE' })
//     setMsg(`User deleted.`)
//     setTimeout(() => setMsg(''), 2500)
//     load()
//   }

//   if (loading) return <div className="content-card"><div className="skeleton-block"/></div>

//   return (
//     <>
//       {msg && <div className="success-box" style={{marginBottom:'12px'}}>{msg}</div>}
//       <div className="table-card">
//         <table className="data-table">
//           <thead>
//             <tr>
//               <th>ID</th><th>Username</th><th>Email</th>
//               <th>Provider</th><th>Roles</th><th>Status</th><th>Actions</th>
//             </tr>
//           </thead>
//           <tbody>
//             {users.length === 0
//               ? <tr><td colSpan="7" style={{textAlign:'center',padding:'24px',color:'var(--muted)'}}>No users found</td></tr>
//               : users.map(u => (
//                 <tr key={u.id}>
//                   <td className="mono">#{u.id}</td>
//                   <td>{u.username}</td>
//                   <td className="muted">{u.email || '—'}</td>
//                   <td><StatusBadge text={u.provider || 'local'} color="gray"/></td>
//                   <td>
//                     {(u.roles || []).map(r => (
//                       <StatusBadge key={r} text={r.replace('ROLE_','')}
//                         color={r.includes('ADMIN') ? 'purple' : 'teal'}
//                         style={{marginRight:'4px'}}/>
//                     ))}
//                   </td>
//                   <td>
//                     <StatusBadge text={u.enabled ? 'Active' : 'Disabled'}
//                       color={u.enabled ? 'teal' : 'red'}/>
//                   </td>
//                   <td>
//                     <div style={{display:'flex',gap:'6px'}}>
//                       <button className="btn-action" onClick={() => toggleEnable(u.id, u.enabled)}>
//                         {u.enabled ? 'Disable' : 'Enable'}
//                       </button>
//                       <button className="btn-action btn-action--danger"
//                         onClick={() => deleteUser(u.id, u.username)}>
//                         Delete
//                       </button>
//                     </div>
//                   </td>
//                 </tr>
//               ))
//             }
//           </tbody>
//         </table>
//       </div>
//     </>
//   )
// }

// /* ── Admin: All Orders ───────────────────────────────────── */
// function AdminOrders({ authFetch }) {
//   const [orders,  setOrders]  = useState([])
//   const [loading, setLoading] = useState(true)

//   useEffect(() => {
//     authFetch('/api/orders/admin/all')
//       .then(r => r.ok ? r.json() : [])
//       .then(setOrders)
//       .finally(() => setLoading(false))
//   }, [authFetch])

//   const statusColor = { CONFIRMED:'teal', CANCELLED:'red', PROCESSING:'amber', PENDING:'gray' }

//   if (loading) return <div className="content-card"><div className="skeleton-block"/></div>

//   return (
//     <div className="table-card">
//       <table className="data-table">
//         <thead>
//           <tr>
//             <th>ID</th><th>User</th><th>Product</th>
//             <th>Qty</th><th>Amount</th><th>Status</th><th>Payment ID</th><th>Date</th>
//           </tr>
//         </thead>
//         <tbody>
//           {orders.length === 0
//             ? <tr><td colSpan="8" style={{textAlign:'center',padding:'24px',color:'var(--muted)'}}>No orders</td></tr>
//             : orders.map(o => (
//               <tr key={o.orderId}>
//                 <td className="mono">#{o.orderId}</td>
//                 <td>{o.username}</td>
//                 <td>{o.productName}</td>
//                 <td>{o.quantity}</td>
//                 <td className="mono">₹{Number(o.amount).toLocaleString()}</td>
//                 <td><StatusBadge text={o.status} color={statusColor[o.status]||'gray'}/></td>
//                 <td className="mono" style={{fontSize:'11px'}}>{o.paymentId ? o.paymentId.substring(0,14)+'…' : '—'}</td>
//                 <td className="muted">{fmtDate(o.createdAt)}</td>
//               </tr>
//             ))
//           }
//         </tbody>
//       </table>
//     </div>
//   )
// }

// /* ── Admin: All Payments ─────────────────────────────────── */
// function AdminPayments({ authFetch }) {
//   const [payments, setPayments] = useState([])
//   const [loading,  setLoading]  = useState(true)

//   useEffect(() => {
//     authFetch('/api/payment/admin/all')
//       .then(r => r.ok ? r.json() : [])
//       .then(setPayments)
//       .finally(() => setLoading(false))
//   }, [authFetch])

//   const statusColor = { SUCCESS:'teal', FAILED:'red', CANCELLED:'amber', PENDING:'gray' }

//   if (loading) return <div className="content-card"><div className="skeleton-block"/></div>

//   return (
//     <div className="table-card">
//       <table className="data-table">
//         <thead>
//           <tr>
//             <th>Payment ID</th><th>Order</th><th>User</th>
//             <th>Amount</th><th>Status</th><th>Reason</th><th>Date</th>
//           </tr>
//         </thead>
//         <tbody>
//           {payments.length === 0
//             ? <tr><td colSpan="7" style={{textAlign:'center',padding:'24px',color:'var(--muted)'}}>No payments</td></tr>
//             : payments.map((p, i) => (
//               <tr key={p.paymentId || i}>
//                 <td className="mono" style={{fontSize:'11px'}}>{p.paymentId}</td>
//                 <td className="mono">#{p.orderId}</td>
//                 <td>{p.username}</td>
//                 <td className="mono">₹{Number(p.amount).toLocaleString()}</td>
//                 <td><StatusBadge text={p.status} color={statusColor[p.status]||'gray'}/></td>
//                 <td className="muted" style={{maxWidth:'140px',overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}}>
//                   {p.message || '—'}
//                 </td>
//                 <td className="muted">{fmtDate(p.createdAt)}</td>
//               </tr>
//             ))
//           }
//         </tbody>
//       </table>
//     </div>
//   )
// }

// function fmtDate(dt) {
//   if (!dt) return '—'
//   return new Date(dt).toLocaleString('en-IN', {
//     day:'2-digit', month:'short', hour:'2-digit', minute:'2-digit'
//   })
// }