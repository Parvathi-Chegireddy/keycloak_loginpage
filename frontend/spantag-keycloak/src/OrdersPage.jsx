import { useState, useEffect, useCallback, useRef } from 'react'
import { PageHeader, StatusBadge, EmptyState } from './Components.jsx'

export default function OrdersPage({ authFetch }) {
  const [orders,        setOrders]        = useState([])
  const [loading,       setLoading]       = useState(true)
  const [placing,       setPlacing]       = useState(false)
  const [showForm,      setShowForm]      = useState(false)
  const [form,          setForm]          = useState({ productName:'', quantity:1, amount:'' })
  const [msg,           setMsg]           = useState({ text:'', type:'' })
  const [selected,      setSelected]      = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)

  /* ── THE FIX for React 19 double-firing ─────────────────────────────────
     React 19 concurrent mode fires useEffect twice per mount.
     Setting fetchRef.current = true SYNCHRONOUSLY (before any await) means
     the second invocation sees it immediately and returns without fetching.
     Only ONE network request is ever made on mount.
  ── */
  const fetchRef = useRef(false)

  const loadOrders = useCallback(async () => {
    if (fetchRef.current) return        // second invocation → exit immediately
    fetchRef.current = true             // ← SYNCHRONOUS, before any await

    setLoading(true)
    try {
      const res = await authFetch('/api/orders')
      if (res.ok) {
        setOrders(await res.json())
      } else {
        fetchRef.current = false        // allow retry on failure
      }
    } catch (_) {
      fetchRef.current = false
    }
    setLoading(false)
  }, [authFetch])

  useEffect(() => { loadOrders() }, [loadOrders])

  /* Manual refresh from button */
  const refresh = useCallback(() => {
    fetchRef.current = false
    loadOrders()
  }, [loadOrders])

  const handlePlaceOrder = async () => {
    if (!form.productName.trim()) { setMsg({ text:'Product name is required.', type:'error' }); return }
    if (!form.amount || Number(form.amount) <= 0) { setMsg({ text:'Amount must be > 0.', type:'error' }); return }
    if (!form.quantity || Number(form.quantity) <= 0) { setMsg({ text:'Qty must be >= 1.', type:'error' }); return }

    setMsg({ text:'', type:'' }); setPlacing(true)
    try {
      const res = await authFetch('/api/orders', {
        method:'POST',
        body: JSON.stringify({
          productName: form.productName.trim(),
          quantity:    Number(form.quantity),
          amount:      Number(form.amount),
        })
      })
      const data = await res.json()
      if (res.ok || res.status === 201) {
        setMsg({
          text: data.status === 'CONFIRMED' ? `✓ Order #${data.orderId} confirmed`
              : data.status === 'CANCELLED' ? `✗ Cancelled — ${data.cancellationReason || 'Saga compensation'}`
              : `Order created (${data.status})`,
          type: data.status === 'CANCELLED' ? 'error' : 'success'
        })
        setForm({ productName:'', quantity:1, amount:'' }); setShowForm(false)
        fetchRef.current = false; loadOrders()
      } else {
        setMsg({ text: data.message || `Failed (HTTP ${res.status})`, type:'error' })
      }
    } catch (_) { setMsg({ text:'Network error', type:'error' }) }
    finally { setPlacing(false) }
  }

  const viewOrder = async (orderId) => {
    setSelected({ orderId }); setDetailLoading(true)
    try { const r = await authFetch(`/api/orders/${orderId}`); if (r.ok) setSelected(await r.json()) }
    catch (_) {}
    setDetailLoading(false)
  }

  const stats = orders.reduce((a, o) => {
    if (o.status==='CONFIRMED') a.confirmed++
    if (o.status==='CANCELLED') a.cancelled++
    a.total += Number(o.amount||0); return a
  }, { confirmed:0, cancelled:0, total:0 })

  return (
    <div className="content-page">
      <PageHeader
        title="Orders"
        subtitle="Place and track your orders — Saga pattern with automatic compensation"
        action={
          <div style={{ display:'flex', gap:'8px', alignItems:'center' }}>
            <button className="btn-action" onClick={refresh}>↻ Refresh</button>
            <button className="btn-primary btn-sm"
              onClick={() => { setShowForm(s => !s); setMsg({ text:'', type:'' }) }}>
              {showForm ? '✕ Cancel' : '+ New Order'}
            </button>
          </div>
        }
      />

      {orders.length > 0 && (
        <div className="stats-row" style={{ marginBottom:'20px' }}>
          <StatCard label="Total Orders" value={orders.length}                              color="gray" />
          <StatCard label="Confirmed"    value={stats.confirmed}                            color="teal" />
          <StatCard label="Cancelled"    value={stats.cancelled}                            color="red" />
          <StatCard label="Total Value"  value={`₹${stats.total.toLocaleString('en-IN')}`} color="amber" />
        </div>
      )}

      {msg.text && (
        <div className={msg.type==='error' ? 'error-box' : 'success-box'}
          style={{ marginBottom:'16px', display:'flex', justifyContent:'space-between', alignItems:'center' }}>
          <span>{msg.text}</span>
          <button style={{ background:'none', border:'none', cursor:'pointer', color:'inherit', opacity:.6 }}
            onClick={() => setMsg({ text:'', type:'' })}>✕</button>
        </div>
      )}

      {showForm && (
        <>
          <div style={{ background:'#0d1424', border:'1px solid #1b3a6e', borderRadius:'6px',
            padding:'10px 14px', marginBottom:'12px', fontSize:'.74rem', color:'#85B7EB', display:'flex', gap:'8px' }}>
            <span>ℹ</span>
            <span>Saga Orchestrator: PENDING → payment → CONFIRMED or CANCELLED. Amount &gt; ₹10,000 triggers cancellation.</span>
          </div>
          <div className="content-card" style={{ marginBottom:'20px' }}>
            <h3 className="card-section-title">New Order</h3>
            <div className="form-inline">
              <div className="field" style={{ flex:'2', minWidth:'150px' }}>
                <label className="label">Product Name</label>
                <input className="input" placeholder="e.g. Laptop"
                  value={form.productName}
                  onChange={e => setForm(f => ({ ...f, productName: e.target.value }))}
                  onKeyDown={e => e.key==='Enter' && handlePlaceOrder()} />
              </div>
              <div className="field" style={{ flex:'0 0 80px' }}>
                <label className="label">Qty</label>
                <input className="input" type="number" min="1" value={form.quantity}
                  onChange={e => setForm(f => ({ ...f, quantity: e.target.value }))} />
              </div>
              <div className="field" style={{ flex:'1', minWidth:'120px' }}>
                <label className="label">Amount (₹)</label>
                <input className="input" type="number" min="1" placeholder="e.g. 4999"
                  value={form.amount}
                  onChange={e => setForm(f => ({ ...f, amount: e.target.value }))} />
              </div>
              <button className="btn-primary" onClick={handlePlaceOrder} disabled={placing}
                style={{ flex:'0 0 140px', alignSelf:'flex-end', marginBottom:0, height:'40px' }}>
                {placing ? <><span className="spinner" />&nbsp;Processing…</> : 'Place Order'}
              </button>
            </div>
          </div>
        </>
      )}

      {loading ? (
        <div className="content-card">
          {[1,2,3].map(i => <div key={i} className="skeleton-block" style={{ height:'36px', marginBottom:'8px' }} />)}
        </div>
      ) : orders.length === 0 ? (
        <EmptyState message="No orders yet. Place your first order above." />
      ) : (
        <div className="table-card">
          <table className="data-table">
            <thead><tr>
              <th>ID</th><th>Product</th><th>Qty</th>
              <th>Amount</th><th>Status</th><th>Payment ID</th><th>Created</th><th></th>
            </tr></thead>
            <tbody>
              {orders.map(o => (
                <tr key={o.orderId}>
                  <td className="mono" style={{ color:'var(--muted)' }}>#{o.orderId}</td>
                  <td style={{ fontWeight:500 }}>{o.productName}</td>
                  <td className="mono">{o.quantity}</td>
                  <td className="mono">₹{Number(o.amount).toLocaleString('en-IN')}</td>
                  <td><OrderBadge status={o.status} /></td>
                  <td className="mono" style={{ fontSize:'11px' }}>
                    {o.paymentId
                      ? <span title={o.paymentId} style={{ cursor:'help' }}>{o.paymentId.substring(0,14)}…</span>
                      : <span className="muted">—</span>}
                  </td>
                  <td className="muted">{fmt(o.createdAt)}</td>
                  <td><button className="btn-action" onClick={() => viewOrder(o.orderId)}>Details</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {selected && (
        <Drawer onClose={() => setSelected(null)}>
          {detailLoading
            ? <div style={{ padding:'32px', textAlign:'center' }}><span className="spinner" style={{ display:'inline-block' }} /></div>
            : <OrderDetail order={selected} onClose={() => setSelected(null)} />}
        </Drawer>
      )}
    </div>
  )
}

function OrderDetail({ order, onClose }) {
  const ok  = order.status==='CONFIRMED'
  const bad = order.status==='CANCELLED'
  return (
    <div style={{ padding:'28px 24px' }}>
      <div style={{ display:'flex', justifyContent:'space-between', alignItems:'flex-start', marginBottom:'20px' }}>
        <div>
          <h2 style={{ fontFamily:'var(--font-head)', fontSize:'1.1rem', fontWeight:700 }}>Order #{order.orderId}</h2>
          <p className="muted" style={{ fontSize:'.74rem', marginTop:'2px' }}>Full order details</p>
        </div>
        <button className="btn-action" onClick={onClose}>✕</button>
      </div>
      <div style={{
        background: ok?'#0a1f18': bad?'#1f0a0a':'#141414',
        border: `1px solid ${ok?'#0f6e56':bad?'#A32D2D':'#2a2a2a'}`,
        borderRadius:'6px', padding:'12px 16px', marginBottom:'20px',
        display:'flex', alignItems:'center', gap:'10px' }}>
        <span style={{ fontSize:'18px' }}>{ok?'✓':bad?'✗':'○'}</span>
        <div>
          <div style={{ fontSize:'.82rem', fontWeight:600, color:ok?'#5DCAA5':bad?'#f87171':'var(--text)' }}>
            {ok?'Payment Confirmed':bad?'Order Cancelled':order.status}
          </div>
          {bad && order.cancellationReason && (
            <div style={{ fontSize:'.74rem', color:'#f87171', marginTop:'2px' }}>Reason: {order.cancellationReason}</div>
          )}
        </div>
      </div>
      <div className="details-grid">
        <DR label="Product"  value={order.productName} />
        <DR label="Quantity" value={order.quantity} />
        <DR label="Amount"   value={`₹${Number(order.amount).toLocaleString('en-IN')}`} mono />
        <DR label="Username" value={order.username} />
        <DR label="Status"   value={<OrderBadge status={order.status} />} />
        {order.paymentId && <DR label="Payment ID" value={order.paymentId} mono small />}
        {order.cancellationReason && <DR label="Cancel Reason" value={order.cancellationReason} red />}
        <DR label="Created"  value={fmtFull(order.createdAt)} />
        {order.updatedAt && <DR label="Updated" value={fmtFull(order.updatedAt)} />}
      </div>
    </div>
  )
}

function Drawer({ children, onClose }) {
  return (
    <>
      <div onClick={onClose} style={{ position:'fixed', inset:0, background:'rgba(0,0,0,.5)', zIndex:40, backdropFilter:'blur(2px)' }} />
      <div style={{ position:'fixed', top:0, right:0, bottom:0, width:'420px', maxWidth:'100vw',
        background:'var(--surface)', borderLeft:'1px solid var(--border)', zIndex:50, overflowY:'auto',
        animation:'slideIn .2s ease' }}>
        {children}
      </div>
      <style>{`@keyframes slideIn{from{transform:translateX(100%)}to{transform:translateX(0)}}`}</style>
    </>
  )
}

function StatCard({ label, value, color }) {
  const c = { teal:['#0a1f18','#0f6e56','#5DCAA5'], red:['#1f0a0a','#A32D2D','#f87171'],
    amber:['#1a1400','#854F0B','#EF9F27'], gray:['#141414','#2a2a2a','#888780'] }[color]||['#141414','#2a2a2a','#888780']
  return <div className="stat-card" style={{ background:c[0], borderColor:c[1] }}>
    <span className="stat-value" style={{ color:c[2] }}>{value}</span>
    <span className="stat-label">{label}</span>
  </div>
}
function OrderBadge({ status }) {
  const [c,l] = ({CONFIRMED:['teal','Confirmed'],CANCELLED:['red','Cancelled'],
    PROCESSING:['amber','Processing'],PENDING:['gray','Pending']})[status]||['gray',status]
  return <StatusBadge text={l} color={c} />
}
function DR({ label, value, mono, small, red }) {
  return <div className="detail-row">
    <span className="detail-key">{label}</span>
    <span style={{ fontFamily:mono?'var(--font-mono)':undefined, fontSize:small?'11px':'.8rem',
      color:red?'#f87171':'var(--text)', wordBreak:'break-all', textAlign:'right', maxWidth:'60%' }}>
      {value}
    </span>
  </div>
}
const fmt     = dt => dt ? new Date(dt).toLocaleString('en-IN',{day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit'}) : '—'
const fmtFull = dt => dt ? new Date(dt).toLocaleString('en-IN',{day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit',second:'2-digit'}) : '—'