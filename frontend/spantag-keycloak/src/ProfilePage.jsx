import { useEffect, useState, useRef } from 'react'
import { PageHeader, DetailRow, StatusBadge } from './Components.jsx'

export default function ProfilePage({ profile, setProfile, authFetch }) {
  const [userProfile, setUserProfile] = useState(null)
  const [editing,     setEditing]     = useState(false)
  const [editForm,    setEditForm]    = useState({ email:'', displayName:'' })
  const [msg,         setMsg]         = useState({ text:'', type:'' })
  const [loading,     setLoading]     = useState(true)
  const fetchRef = useRef(false)       // React 19 double-fire guard

  useEffect(() => {
    if (fetchRef.current) return       // second invocation → exit immediately
    fetchRef.current = true            // set SYNCHRONOUSLY before any await

    authFetch('/api/user/me')
      .then(async r => {
        if (!r.ok) {
          fetchRef.current = false     // allow retry
          throw new Error(`HTTP ${r.status}`)
        }
        return r.json()
      })
      .then(data => {
        setUserProfile(data)
        setEditForm({ email: data.email||'', displayName: data.displayName||'' })
        if (setProfile && data.roles) setProfile(p => ({ ...p, roles: data.roles }))
        // No error message set — success is silent
      })
      .catch(err => {
        // Only show error if we have no profile data at all to display
        // (basic profile info comes from login, so user still sees their info)
        setMsg({ text:`Could not load extended profile (${err.message}).`, type:'error' })
      })
      .finally(() => setLoading(false))
  }, [authFetch]) // eslint-disable-line

  const handleSave = async () => {
    setMsg({ text:'', type:'' })
    try {
      const res  = await authFetch('/api/user/me', { method:'PUT', body: JSON.stringify(editForm) })
      const data = await res.json()
      if (res.ok) {
        setUserProfile(data); setEditing(false)
        setMsg({ text:'Profile updated successfully.', type:'success' })
        setTimeout(() => setMsg({ text:'', type:'' }), 3000)
      } else {
        setMsg({ text: data.message||'Update failed.', type:'error' })
      }
    } catch (_) { setMsg({ text:'Network error.', type:'error' }) }
  }

  const displayName = userProfile?.displayName || profile.displayName || profile.username || 'User'
  const email       = userProfile?.email       || profile.email       || ''
  const avatarUrl   = userProfile?.avatarUrl   || profile.avatar      || ''
  const provider    = userProfile?.provider    || profile.provider    || 'keycloak'
  const roles       = userProfile?.roles       || (profile.role ? [profile.role] : [])

  return (
    <div className="content-page">
      <PageHeader title="My Profile" subtitle="View and edit your account details" />
      {loading && <div className="skeleton-block" />}
      {!loading && (
        <div className="content-card">
          <div className="profile-hero">
            {avatarUrl
              ? <img src={avatarUrl} alt={displayName} className="profile-avatar" />
              : <div className="profile-avatar-placeholder">{(displayName[0]||'?').toUpperCase()}</div>
            }
            <div>
              <h2 className="profile-name">{displayName}</h2>
              <p className="profile-sub">@{userProfile?.username || profile.username}</p>
              <div style={{ display:'flex', gap:'8px', marginTop:'8px', flexWrap:'wrap' }}>
                {roles.filter(Boolean).map(r => (
                  <StatusBadge key={r} text={r.replace('ROLE_','')} color={r.includes('ADMIN')?'purple':'teal'} />
                ))}
                <StatusBadge text={provider} color="gray" />
              </div>
            </div>
          </div>

          {msg.text && (
            <div className={msg.type==='error'?'error-box':'success-box'} style={{ marginBottom:'16px' }}>
              {msg.text}
            </div>
          )}

          {!editing ? (
            <>
              <div className="details-grid">
                <DetailRow label="Display Name"  value={displayName} />
                <DetailRow label="Username"      value={userProfile?.username || profile.username} />
                {email && <DetailRow label="Email" value={email} />}
                <DetailRow label="Login Method"  value={profile.methodLabel || 'Password Login'} />
                <DetailRow label="Provider"      value={provider} />
                <DetailRow label="Access Token"  value="● 15 min · in-memory"       highlight />
                <DetailRow label="Refresh Token" value="● 7 days · HttpOnly cookie" highlight />
              </div>
              <button className="btn-secondary" style={{ marginTop:'20px' }} onClick={() => setEditing(true)}>
                EDIT PROFILE
              </button>
            </>
          ) : (
            <div className="form" style={{ marginTop:'8px' }}>
              <div className="field">
                <label className="label">DISPLAY NAME</label>
                <input className="input" value={editForm.displayName}
                  onChange={e => setEditForm(f => ({ ...f, displayName: e.target.value }))}
                  placeholder="Your display name" />
              </div>
              <div className="field">
                <label className="label">EMAIL</label>
                <input className="input" type="email" value={editForm.email}
                  onChange={e => setEditForm(f => ({ ...f, email: e.target.value }))}
                  placeholder="your@email.com" />
              </div>
              <div style={{ display:'flex', gap:'10px', marginTop:'4px' }}>
                <button className="btn-primary"   style={{ flex:1 }} onClick={handleSave}>SAVE</button>
                <button className="btn-secondary" style={{ flex:1 }}
                  onClick={() => { setEditing(false); setMsg({ text:'', type:'' }) }}>CANCEL</button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}