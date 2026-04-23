import { useState, useEffect, useCallback } from 'react'
import { MapContainer, TileLayer, Marker, Popup, useMapEvents } from 'react-leaflet'
import L from 'leaflet'
import { getAllStatuses, saveStatus, deleteStatus } from './apiClient'

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

function App() {
  const [username, setUsername] = useState('')
  const [statustext, setStatustext] = useState('')
  const [lat, setLat] = useState(String(CENTER_LAT))
  const [lon, setLon] = useState(String(CENTER_LON))
  const [statuses, setStatuses] = useState([])
  const [message, setMessage] = useState('')
  const [isError, setIsError] = useState(false)

  const showMessage = (msg, error = false) => {
    setMessage(msg)
    setIsError(error)
    setTimeout(() => setMessage(''), 5000)
  }

  const fetchAll = useCallback(async () => {
    try {
      const data = await getAllStatuses()
      setStatuses(data)
    } catch (e) {
      showMessage('Error fetching statuses: ' + e.message, true)
    }
  }, [])

  useEffect(() => {
    fetchAll()
    const interval = setInterval(fetchAll, 5000)
    return () => clearInterval(interval)
  }, [fetchAll])

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
    if (err) {
      showMessage(err, true)
      return
    }
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
    } catch (e) {
      showMessage('Error: ' + e.message, true)
    }
  }

  const del = async (name) => {
    if (!window.confirm(`Delete status for "${name}"?`)) return
    try {
      await deleteStatus(name)
      showMessage('Status deleted')
      fetchAll()
    } catch (e) {
      showMessage('Delete error: ' + e.message, true)
    }
  }

  const formatTime = (iso) => {
    if (!iso) return ''
    try {
      return new Date(iso).toLocaleString()
    } catch {
      return iso
    }
  }

  return (
    <div className="container">
      <h1>TEVS Command Center</h1>

      {message && (
        <div className={`alert ${isError ? 'alert-error' : 'alert-success'}`}>
          {isError ? '⚠️' : '✅'} {message}
        </div>
      )}

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
            <span>💾</span> Save Status
          </button>
        </div>
        <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '1rem', display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
          <span>💡</span> Tip: Click anywhere on the map to automatically set coordinates.
        </p>
      </div>

      <div className="card" style={{ padding: '0.5rem' }}>
        <div className="map-container">
          <MapContainer center={[CENTER_LAT, CENTER_LON]} zoom={13} style={{ height: '100%', width: '100%' }}>
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <LocationPicker onPick={(la, lo) => { setLat(String(la.toFixed(6))); setLon(String(lo.toFixed(6))) }} />
            {statuses.map((s) => (
              <Marker key={s.username} position={[s.latitude, s.longitude]}>
                <Popup>
                  <div style={{ minWidth: '180px', padding: '0.2rem' }}>
                    <div style={{ fontSize: '1rem', fontWeight: '700', color: 'var(--text-main)', marginBottom: '0.25rem' }}>{s.username}</div>
                    <div style={{ fontSize: '0.9rem', color: 'var(--text-main)', marginBottom: '0.75rem', lineHeight: '1.4' }}>{s.statustext}</div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '0.75rem', display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                      <span>🕒</span> {formatTime(s.time)}
                    </div>
                    <button className="btn-delete" style={{ width: '100%' }} onClick={() => del(s.username)}>
                      <span>🗑️</span> Delete Entry
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
          <button className="btn-secondary" onClick={fetchAll}>
            <span>🔄</span> Refresh Feed
          </button>
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
                  <td colSpan="4" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                    No status updates yet.
                  </td>
                </tr>
              ) : (
                statuses.map((s) => (
                  <tr key={s.username}>
                    <td>
                      <div style={{ fontWeight: '600', color: 'var(--text-main)' }}>{s.username}</div>
                    </td>
                    <td style={{ maxWidth: '300px', wordBreak: 'break-word' }}>{s.statustext}</td>
                    <td style={{ whiteSpace: 'nowrap', color: 'var(--text-muted)' }}>{formatTime(s.time)}</td>
                    <td style={{ textAlign: 'right' }}>
                      <button className="btn-delete" onClick={() => del(s.username)}>
                        <span>🗑️</span> Delete
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

export default App
