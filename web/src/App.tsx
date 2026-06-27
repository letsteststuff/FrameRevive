import './App.css'

declare global {
  interface Window {
    FrameRevive?: {
      getVersion(): string
      getPlatform(): string
      getAndroidVersion(): string
      getDevice(): string
    }
  }
}

function App() {
  const version = window.FrameRevive?.getVersion() ?? "Development"
  const platform = window.FrameRevive?.getPlatform() ?? "Browser"
  const android = window.FrameRevive?.getAndroidVersion() ?? "-"
  const device = window.FrameRevive?.getDevice() ?? "Desktop"

  return (
    <main className="app">
      <div className="card">
        <h1>🖼️ FrameRevive</h1>

        <p className="status">Connected ✅</p>

        <table>
          <tbody>
            <tr>
              <td>Version</td>
              <td>{version}</td>
            </tr>

            <tr>
              <td>Platform</td>
              <td>{platform}</td>
            </tr>

            <tr>
              <td>Android</td>
              <td>{android}</td>
            </tr>

            <tr>
              <td>Device</td>
              <td>{device}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </main>
  )
}

export default App