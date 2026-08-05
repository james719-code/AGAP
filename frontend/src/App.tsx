import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { AlertTriangle, Shield, Radio, Activity } from 'lucide-react'

function App() {
  const [sosActive, setSosActive] = useState(false)

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col items-center justify-center p-6 space-y-6">
      <div className="flex items-center gap-3">
        <Shield className="h-10 w-10 text-emerald-400" />
        <h1 className="text-3xl font-bold tracking-wider text-slate-100">AGAP Command Center</h1>
      </div>
      <p className="text-slate-400 max-w-md text-center text-sm">
        Accelerated Geolocation-based Assistance and Protection — Legazpi City Emergency Dispatch
      </p>

      <div className="flex flex-col sm:flex-row items-center gap-4 pt-4">
        <Button
          variant={sosActive ? "destructive" : "default"}
          size="lg"
          onClick={() => setSosActive(!sosActive)}
          className="flex items-center gap-2 font-semibold"
        >
          <AlertTriangle className="h-5 w-5" />
          {sosActive ? "CANCEL DISTRESS SOS" : "TRIGGER DISTRESS SOS"}
        </Button>

        <Button variant="outline" size="lg" className="flex items-center gap-2">
          <Radio className="h-5 w-5 text-sky-400" />
          BLE Mesh Scan Active
        </Button>
      </div>

      {sosActive && (
        <div className="p-4 bg-red-950/60 border border-red-800 text-red-200 rounded-lg max-w-md flex items-center gap-3 animate-pulse">
          <Activity className="h-6 w-6 text-red-400 flex-shrink-0" />
          <div className="text-xs">
            <p className="font-bold">Distress Telemetry Active</p>
            <p>Target ID: 05-05-12-01-04::4a8f9c12 (DRRMO Unit 1)</p>
          </div>
        </div>
      )}
    </div>
  )
}

export default App
