import { useState, useEffect, useCallback, useRef } from 'react'
import { MapContainer, TileLayer, Marker, Popup, useMapEvents, useMap } from 'react-leaflet'
import L from 'leaflet'
import { getAllStatuses, getStatus, saveStatus, deleteStatus, AllNodesUnreachable } from './apiClient'

// Fix Leaflet default icon paths for Vite bundler
import icon from 'leaflet/dist/images/marker-icon.png'
import iconShadow from 'leaflet/dist/images/marker-shadow.png'

const DefaultIcon = L.icon({
  iconUrl: icon,
  shadowUrl: iconShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
})
L.Marker.prototype.options.icon = DefaultIcon

const CENTER_LAT = 48.215
const CENTER_LON = 16.385

function LocationPicker({ onPick }) {
  useMapEvents({
    click(e) {
      onPick(e.latlng.lat, e.latlng.lng)
    },
  })
  return null
}

function MapZoom({ center, zoom }) {
  const map = useMap()
  useEffect(() => {
    if (center) {
      map.setView(center, zoom || 13)
    }
  }, [map, center, zoom])
  return null
}

function formatTime(iso) {
  if (!iso) return ''
  try { return new Date(iso).toLocaleString() }
  catch { return iso }
}

const TABS = ['dashboard', 'new-status']
const TAB_LABELS = { dashboard: 'Dashboard', 'new-status': 'New Status' }

function App() {
  const [activeTab, setActiveTab] = useState('dashboard')
  const [username, setUsername] = useState('')
  const [statustext, setStatustext] = useState('')
  const [lat, setLat] = useState(String(CENTER_LAT))
  const [lon, setLon] = useState(String(CENTER_LON))
  const [statuses, setStatuses] = useState([])
  const [message, setMessage] = useState('')
  const [isError, setIsError] = useState(false)
  const [selectedUser, setSelectedUser] = useState(null)
  const [detailData, setDetailData] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const messageTimer = useRef(null)

  const showMessage = (msg, error = false, persistent = false) => {
    if (messageTimer.current) clearTimeout(messageTimer.current)
    setMessage(msg)
    setIsError(error)
    if (!persistent) {
      messageTimer.current = setTimeout(() => setMessage(''), 5000)
    }
  }

  const dismissMessage = () => {
    if (messageTimer.current) clearTimeout(messageTimer.current)
    setMessage('')
  }

  const fetchAll = useCallback(async () => {
    try {
      const data = await getAllStatuses()
      setStatuses(data)
    } catch (e) {
      if (e instanceof AllNodesUnreachable) {
        showMessage('All backend nodes unreachable. Check if servers are running.', true, true)
      } else {
        showMessage('Error fetching statuses: ' + e.message, true)
      }
    }
  }, [])

  useEffect(() => {
    fetchAll()
    const interval = setInterval(fetchAll, 5000)
    return () => clearInterval(interval)
  }, [fetchAll])

  const viewDetail = async (name) => {
    setSelectedUser(name)
    setDetailLoading(true)
    setDetailData(null)
    try {
      const data = await getStatus(name)
      setDetailData(data)
    } catch (e) {
      if (e instanceof AllNodesUnreachable) {
        showMessage('All backend nodes unreachable. Check if servers are running.', true, true)
      } else {
        showMessage('Error fetching detail: ' + e.message, true)
      }
    } finally {
      setDetailLoading(false)
    }
  }

  const closeDetail = () => {
    setSelectedUser(null)
    setDetailData(null)
  }

  const validate = () => {
    if (!username.trim()) return 'Username is required'
    if (!statustext.trim()) return 'Status text is required'
    const latNum = parseFloat(lat)
    const lonNum = parseFloat(lon)
    if (Number.isNaN(latNum) || latNum < -90 || latNum > 90) return 'Latitude must be between -90 and 90'
    if (Number.isNaN(lonNum) || lonNum < -180 || lonNum > 180) return 'Longitude must be between -180 and 180'
    return null
  }

  const submit = async () => {
    const err = validate()
    if (err) { showMessage(err, true); return }
    const payload = {
      username: username.trim(),
      statustext: statustext.trim(),
      time: new Date().toISOString(),
      latitude: parseFloat(lat),
      longitude: parseFloat(lon),
    }
    try {
      await saveStatus(payload)
      showMessage('Status saved!')
      fetchAll()
      setUsername('')
      setStatustext('')
      setActiveTab('dashboard')
    } catch (e) {
      if (e instanceof AllNodesUnreachable) {
        showMessage('All backend nodes unreachable. Check if servers are running.', true, true)
      } else {
        showMessage('Error: ' + e.message, true)
      }
    }
  }

  const del = async (name) => {
    if (!window.confirm(`Delete status for "${name}"?`)) return
    try {
      await deleteStatus(name)
      showMessage('Status deleted')
      fetchAll()
      if (selectedUser === name) closeDetail()
    } catch (e) {
      if (e instanceof AllNodesUnreachable) {
        showMessage('All backend nodes unreachable. Check if servers are running.', true, true)
      } else {
        showMessage('Delete error: ' + e.message, true)
      }
    }
  }

  return (
    <div className="container">
      <h1>TEVS Command Center</h1>

      {/* Nav tabs */}
      <div className="nav-tabs">
        {TABS.map(tab => (
          <button
            key={tab}
            className={`nav-tab ${activeTab === tab ? 'nav-tab-active' : ''}`}
            onClick={() => setActiveTab(tab)}
          >
            {tab === 'dashboard' ? '🗺️ ' : '📝 '}{TAB_LABELS[tab]}
          </button>
        ))}
      </div>

      {/* Persistent unreachable banner */}
      {message && isError && message.startsWith('All backend nodes') && (
        <div className="alert alert-error alert-persistent">
          <span>🚫</span> {message}
          <button className="btn-dismiss" onClick={dismissMessage}>✕</button>
        </div>
      )}

      {/* Normal transient messages */}
      {message && !message.startsWith('All backend nodes') && (
        <div className={`alert ${isError ? 'alert-error' : 'alert-success'}`}>
          {isError ? '⚠️' : '✅'} {message}
        </div>
      )}

      {/* Detail view */}
      {selectedUser && (
        <div className="card">
          <div className="detail-header">
            <h3>📍 Status Detail: {selectedUser}</h3>
            <button className="btn-secondary" onClick={closeDetail}>← Back to Dashboard</button>
          </div>
          {detailLoading ? (
            <p className="text-muted" style={{ padding: '1rem 0' }}>Loading...</p>
          ) : detailData ? (
            <div className="detail-grid">
              <div className="detail-field">
                <span className="detail-label">Username</span>
                <span className="detail-value">{detailData.username}</span>
              </div>
              <div className="detail-field">
                <span className="detail-label">Status</span>
                <span className="detail-value">{detailData.statustext}</span>
              </div>
              <div className="detail-field">
                <span className="detail-label">Time</span>
                <span className="detail-value">{formatTime(detailData.time)}</span>
              </div>
              <div className="detail-field">
                <span className="detail-label">Latitude</span>
                <span className="detail-value">{detailData.latitude}</span>
              </div>
              <div className="detail-field">
                <span className="detail-label">Longitude</span>
                <span className="detail-value">{detailData.longitude}</span>
              </div>
              <div className="detail-actions">
                <button className="btn-delete" onClick={() => del(detailData.username)}>
                  🗑️ Delete Entry
                </button>
              </div>
            </div>
          ) : (
            <p className="text-muted" style={{ padding: '1rem 0' }}>User not found.</p>
          )}
        </div>
      )}

      {activeTab === 'new-status' && (
        <div className="card">
          <h3>New / Update Status</h3>
          <div style={{ display: 'flex', gap: '1rem', marginBottom: '1rem' }}>
            <div className="input-group">
              <label>Username</label>
              <input placeholder="Enter username" value={username} onChange={(e) => setUsername(e.target.value)} />
            </div>
            <div className="input-group" style={{ flex: 2 }}>
              <label>Status message</label>
              <input placeholder="What's happening?" value={statustext} onChange={(e) => setStatustext(e.target.value)} />
            </div>
          </div>
          <div style={{ display: 'flex', gap: '1rem', alignItems: 'flex-end' }}>
            <div className="input-group">
              <label>Latitude</label>
              <input type="number" step="0.0001" value={lat} onChange={(e) => setLat(e.target.value)} />
            </div>
            <div className="input-group">
              <label>Longitude</label>
              <input type="number" step="0.0001" value={lon} onChange={(e) => setLon(e.target.value)} />
            </div>
            <button className="btn-success" style={{ minWidth: '140px' }} onClick={submit}>
              💾 Save Status
            </button>
          </div>
          <p className="tip">
            💡 Tip: Click anywhere on the map to automatically set coordinates.
          </p>
        </div>
      )}

      {activeTab === 'dashboard' && (
        <>
          <div className="card" style={{ padding: '0.5rem' }}>
            <div className="map-container">
              <MapContainer center={[CENTER_LAT, CENTER_LON]} zoom={13} style={{ height: '100%', width: '100%' }}>
                <TileLayer
                  attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                  url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                />
                {selectedUser === null && (
                  <LocationPicker onPick={(la, lo) => { setLat(String(la.toFixed(6))); setLon(String(lo.toFixed(6))) }} />
                )}
                {detailData && (
                  <MapZoom center={[detailData.latitude, detailData.longitude]} zoom={15} />
                )}
                {statuses.map((s) => (
                  <Marker key={s.username} position={[s.latitude, s.longitude]}>
                    <Popup>
                      <div style={{ minWidth: '180px', padding: '0.2rem' }}>
                        <div style={{ fontSize: '1rem', fontWeight: '700', color: 'var(--text-main)', marginBottom: '0.25rem' }}>{s.username}</div>
                        <div style={{ fontSize: '0.9rem', color: 'var(--text-main)', marginBottom: '0.75rem', lineHeight: '1.4' }}>{s.statustext}</div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '0.75rem', display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                          🕒 {formatTime(s.time)}
                        </div>
                        <button className="btn-detail" onClick={() => viewDetail(s.username)}>
                          👤 View Details
                        </button>
                        <button className="btn-delete" style={{ width: '100%', marginTop: '0.4rem' }} onClick={() => del(s.username)}>
                          🗑️ Delete Entry
                        </button>
                      </div>
                    </Popup>
                  </Marker>
                ))}
              </MapContainer>
            </div>
          </div>

          <div className="card">
            <div className="status-feed-header">
              <h3>Status Feed</h3>
              <button className="btn-secondary" onClick={fetchAll}>🔄 Refresh Feed</button>
            </div>
            <div style={{ overflowX: 'auto' }}>
              <table>
                <thead>
                  <tr>
                    <th>User</th>
                    <th>Text</th>
                    <th>Time</th>
                    <th style={{ textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {statuses.length === 0 ? (
                    <tr>
                      <td colSpan="4" className="empty-state">No status updates yet.</td>
                    </tr>
                  ) : (
                    statuses.map((s) => (
                      <tr key={s.username}>
                        <td>
                          <button className="link-btn" onClick={() => viewDetail(s.username)}>
                            {s.username}
                          </button>
                        </td>
                        <td style={{ maxWidth: '300px', wordBreak: 'break-word' }}>{s.statustext}</td>
                        <td style={{ whiteSpace: 'nowrap', color: 'var(--text-muted)' }}>{formatTime(s.time)}</td>
                        <td style={{ textAlign: 'right' }}>
                          <button className="btn-delete" onClick={() => del(s.username)}>🗑️ Delete</button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  )
}

export default App
