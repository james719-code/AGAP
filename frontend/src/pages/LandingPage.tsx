import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Shield,
  Radio,
  MapPin,
  Smartphone,
  Lock,
  Share2,
  Zap,
  ArrowRight,
  Download,
  Activity,
  Layers,
  ChevronRight,
  Users,
  Sun,
  Moon
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { Tooltip } from '@/components/ui/tooltip';
import { useTheme } from '@/context/ThemeContext';

export const LandingPage: React.FC = () => {
  const [downloadModalOpen, setDownloadModalOpen] = useState(false);
  const { theme, toggleTheme } = useTheme();

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 font-sans selection:bg-red-500 selection:text-white relative overflow-hidden transition-colors duration-200">
      {/* Background Glow Orbs */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[1000px] h-[500px] bg-gradient-to-b from-red-600/15 via-sky-600/10 to-transparent blur-3xl pointer-events-none -z-10" />
      <div className="absolute top-[800px] right-0 w-[600px] h-[600px] bg-emerald-500/5 blur-3xl pointer-events-none -z-10" />

      {/* Top Header / Navigation */}
      <header className="sticky top-0 z-40 w-full border-b border-slate-200 dark:border-slate-800/80 bg-white/80 dark:bg-slate-950/80 backdrop-blur-md px-6 lg:px-12 h-20 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="bg-red-500/10 p-2.5 rounded-xl border border-red-500/30 shadow-lg shadow-red-500/10">
            <Shield className="h-6 w-6 text-red-500" />
          </div>
          <div>
            <span className="text-xl font-extrabold tracking-tight text-slate-900 dark:text-white">
              AGAP
            </span>
            <p className="text-[11px] text-slate-500 dark:text-slate-400 hidden sm:block">Accelerated Geolocation Assistance & Protection</p>
          </div>
        </div>

        <nav className="hidden md:flex items-center gap-8 text-sm font-medium text-slate-600 dark:text-slate-300">
          <a href="#features" className="hover:text-red-500 dark:hover:text-red-400 transition-colors">Features</a>
          <a href="#how-it-works" className="hover:text-red-500 dark:hover:text-red-400 transition-colors">How It Works</a>
          <a href="#team" className="hover:text-red-500 dark:hover:text-red-400 transition-colors">Fellowship</a>
        </nav>

        <div className="flex items-center gap-3">
          {/* Dark/Light Mode Theme Toggle Button */}
          <Tooltip content={theme === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'}>
            <Button
              onClick={toggleTheme}
              variant="outline"
              size="sm"
              className="h-9 w-9 p-0 rounded-lg border-slate-300 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-300 hover:text-slate-900 dark:hover:text-slate-100"
            >
              {theme === 'dark' ? <Sun className="h-4 w-4 text-amber-400" /> : <Moon className="h-4 w-4 text-sky-500" />}
            </Button>
          </Tooltip>

          <Button
            variant="outline"
            onClick={() => setDownloadModalOpen(true)}
            className="hidden sm:flex items-center gap-2 border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900/60 hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-800 dark:text-slate-200"
          >
            <Download className="h-4 w-4 text-emerald-500 dark:text-emerald-400" />
            <span>Download APK</span>
          </Button>

          <Link to="/dashboard">
            <Button className="bg-gradient-to-r from-red-600 to-red-700 hover:from-red-500 hover:to-red-600 text-white font-semibold flex items-center gap-2 shadow-lg shadow-red-900/20">
              <span>Command Center</span>
              <ArrowRight className="h-4 w-4" />
            </Button>
          </Link>
        </div>
      </header>

      {/* Hero Section */}
      <section className="relative pt-16 pb-24 px-6 lg:px-12 max-w-7xl mx-auto flex flex-col lg:flex-row items-center justify-between gap-12">
        <div className="flex-1 space-y-6 text-center lg:text-left">
          <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight leading-[1.1] text-slate-900 dark:text-slate-100">
            When Signals Fail — <br />
            <span className="gradient-text-red">Heroes Still Hear You.</span>
          </h1>

          <p className="text-lg text-slate-600 dark:text-slate-300 max-w-2xl font-normal leading-relaxed">
            AGAP delivers instant emergency geolocation assistance via standard mobile networks and <strong className="text-slate-900 dark:text-slate-100 font-semibold">automatically falls back to peer-to-peer Bluetooth Low Energy (BLE) store-and-forward mesh telemetry</strong> when cellular load or internet is unavailable.
          </p>

          <div className="flex flex-col sm:flex-row items-center justify-center lg:justify-start gap-4 pt-2">
            <Button
              size="lg"
              onClick={() => setDownloadModalOpen(true)}
              className="w-full sm:w-auto h-12 px-7 text-base bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-xl flex items-center justify-center gap-2.5 shadow-lg shadow-emerald-950/30 transition-all hover:scale-[1.02]"
            >
              <Download className="h-5 w-5" />
              Download Android App
              <Badge className="bg-emerald-950 text-emerald-300 text-[10px] ml-1">APK</Badge>
            </Button>

            <Link to="/dashboard" className="w-full sm:w-auto">
              <Button
                size="lg"
                variant="outline"
                className="w-full sm:w-auto h-12 px-7 text-base border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900/80 hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-900 dark:text-slate-100 rounded-xl flex items-center justify-center gap-2 font-semibold"
              >
                Launch GIS Command Center
                <ChevronRight className="h-5 w-5 text-slate-400" />
              </Button>
            </Link>
          </div>

          {/* Quick Metrics Bar */}
          <div className="pt-8 border-t border-slate-200 dark:border-slate-800/80 grid grid-cols-3 gap-4 text-left max-w-lg mx-auto lg:mx-0">
            <div>
              <div className="text-2xl font-bold text-slate-900 dark:text-slate-100 font-mono">0.4s</div>
              <div className="text-xs text-slate-500 dark:text-slate-400 font-medium">SOS Dispatch Latency</div>
            </div>
            <div>
              <div className="text-2xl font-bold text-sky-500 dark:text-sky-400 font-mono">255B</div>
              <div className="text-xs text-slate-500 dark:text-slate-400 font-medium">BLE 5.0 Payload</div>
            </div>
            <div>
              <div className="text-2xl font-bold text-emerald-500 dark:text-emerald-400 font-mono">100%</div>
              <div className="text-xs text-slate-500 dark:text-slate-400 font-medium">Offline Resiliency</div>
            </div>
          </div>
        </div>

        {/* Hero Interactive Visual Graphic */}
        <div className="flex-1 w-full max-w-lg relative">
          <div className="relative mx-auto rounded-3xl p-1 bg-gradient-to-b from-slate-200 via-slate-300 to-slate-400 dark:from-slate-700 dark:via-slate-800 dark:to-slate-900 shadow-2xl">
            <div className="bg-white dark:bg-slate-950 rounded-[22px] p-6 space-y-6 relative overflow-hidden border border-slate-200 dark:border-slate-800">
              {/* Animated Mesh Signal Simulation */}
              <div className="flex items-center justify-between pb-4 border-b border-slate-200 dark:border-slate-800">
                <div className="flex items-center gap-2">
                  <div className="h-3 w-3 rounded-full bg-red-500 animate-pulse" />
                  <span className="text-xs font-mono font-bold text-red-500 dark:text-red-400 uppercase tracking-widest">LIVE MESH DISPATCH</span>
                </div>
                <Badge variant="outline" className="border-emerald-500/40 text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-950/40 font-mono text-[10px]">
                  P2P ACTIVE
                </Badge>
              </div>

              {/* Simulated SOS Node Card */}
              <div className="bg-slate-50 dark:bg-slate-900/90 rounded-xl p-4 border border-red-500/30 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-red-500 dark:text-red-400 flex items-center gap-1.5">
                    <Radio className="h-4 w-4 animate-spin text-red-500" /> DISTRESS BROADCAST
                  </span>
                  <span className="text-[11px] font-mono text-slate-500 dark:text-slate-400">Target: DRRMO Zone 4</span>
                </div>
                <div className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                  Medical SOS — Barangay Central, Legazpi City
                </div>
                <div className="flex items-center justify-between text-xs font-mono text-slate-500 dark:text-slate-400 pt-1 border-t border-slate-200 dark:border-slate-800">
                  <span>Target ID: 05-05-12-01-04</span>
                  <span className="text-amber-500 dark:text-amber-400 flex items-center gap-1"><Layers className="h-3.5 w-3.5" /> Hop: 2</span>
                </div>
              </div>

              {/* Hop Chain Graphic */}
              <div className="space-y-2.5">
                <div className="text-[11px] font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">Telemetry Mesh Pathway</div>
                <div className="flex items-center justify-between bg-slate-100 dark:bg-slate-900/60 p-3 rounded-lg border border-slate-200 dark:border-slate-800/80 text-xs">
                  <div className="flex items-center gap-2 text-slate-800 dark:text-slate-300">
                    <Smartphone className="h-4 w-4 text-red-500 dark:text-red-400" />
                    <span>Victim Device</span>
                  </div>
                  <span className="text-slate-500 font-mono">BLE Broadcast</span>
                </div>
                <div className="flex items-center justify-center">
                  <div className="h-4 w-[2px] bg-gradient-to-b from-red-500 to-sky-500" />
                </div>
                <div className="flex items-center justify-between bg-slate-100 dark:bg-slate-900/60 p-3 rounded-lg border border-slate-200 dark:border-slate-800/80 text-xs">
                  <div className="flex items-center gap-2 text-slate-800 dark:text-slate-300">
                    <Users className="h-4 w-4 text-sky-500 dark:text-sky-400" />
                    <span>Citizen Relay Phone B</span>
                  </div>
                  <span className="text-sky-500 dark:text-sky-400 font-mono text-[11px]">Cellular Reconnect</span>
                </div>
                <div className="flex items-center justify-center">
                  <div className="h-4 w-[2px] bg-gradient-to-b from-sky-500 to-emerald-500" />
                </div>
                <div className="flex items-center justify-between bg-slate-100 dark:bg-slate-900/60 p-3 rounded-lg border border-emerald-500/30 text-xs">
                  <div className="flex items-center gap-2 text-emerald-600 dark:text-emerald-400 font-medium">
                    <Activity className="h-4 w-4 text-emerald-500 dark:text-emerald-400" />
                    <span>Legazpi Dispatch Command Center</span>
                  </div>
                  <Badge className="bg-emerald-500/20 text-emerald-700 dark:text-emerald-300 border-0 text-[10px]">RECOVERY COMPLETE</Badge>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Core Features Grid Section */}
      <section id="features" className="py-20 px-6 lg:px-12 max-w-7xl mx-auto border-t border-slate-200 dark:border-slate-800/80">
        <div className="text-center space-y-3 mb-16">
          <h2 className="text-3xl sm:text-4xl font-extrabold text-slate-900 dark:text-slate-100">
            Resilient Emergency Infrastructure
          </h2>
          <p className="text-slate-600 dark:text-slate-400 text-base max-w-2xl mx-auto">
            Designed specifically for Legazpi City’s complex topography, severe typhoon blackouts, and zero-prepaid load scenarios.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {/* Feature 1 */}
          <Card className="glass-card border-slate-200 dark:border-slate-800 hover:border-red-500/40 transition-all duration-300 hover:-translate-y-1">
            <CardContent className="p-6 space-y-4">
              <div className="w-12 h-12 rounded-xl bg-red-500/10 border border-red-500/20 flex items-center justify-center text-red-500 dark:text-red-400">
                <Zap className="h-6 w-6" />
              </div>
              <h3 className="text-lg font-bold text-slate-900 dark:text-slate-100">One-Tap SOS Dispatch</h3>
              <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
                Instant high-priority trigger UI for Crime, Medical Emergency, Traffic Collision, or Natural Disaster with automatic background GPS resolution.
              </p>
            </CardContent>
          </Card>

          {/* Feature 2 */}
          <Card className="glass-card border-slate-200 dark:border-slate-800 hover:border-sky-500/40 transition-all duration-300 hover:-translate-y-1">
            <CardContent className="p-6 space-y-4">
              <div className="w-12 h-12 rounded-xl bg-sky-500/10 border border-sky-500/20 flex items-center justify-center text-sky-500 dark:text-sky-400">
                <Radio className="h-6 w-6" />
              </div>
              <h3 className="text-lg font-bold text-slate-900 dark:text-slate-100">BLE 5.0 Extended Mesh</h3>
              <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
                Utilizes up to 255-byte connectionless extended advertising with fallback GATT fragmentation for phone-to-phone store-and-forward transmission.
              </p>
            </CardContent>
          </Card>

          {/* Feature 3 */}
          <Card className="glass-card border-slate-200 dark:border-slate-800 hover:border-amber-500/40 transition-all duration-300 hover:-translate-y-1">
            <CardContent className="p-6 space-y-4">
              <div className="w-12 h-12 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-500 dark:text-amber-400">
                <MapPin className="h-6 w-6" />
              </div>
              <h3 className="text-lg font-bold text-slate-900 dark:text-slate-100">Spatial Hierarchical Target IDs</h3>
              <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
                Tags packets with structured Region-Province-Barangay-Zone spatial codes allowing intermediate mesh nodes to perform coarse geographic routing.
              </p>
            </CardContent>
          </Card>

          {/* Feature 4 */}
          <Card className="glass-card border-slate-200 dark:border-slate-800 hover:border-emerald-500/40 transition-all duration-300 hover:-translate-y-1">
            <CardContent className="p-6 space-y-4">
              <div className="w-12 h-12 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-500 dark:text-emerald-400">
                <Share2 className="h-6 w-6" />
              </div>
              <h3 className="text-lg font-bold text-slate-900 dark:text-slate-100">Cloud Uplink Shortcut</h3>
              <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
                Any intermediate citizen phone in the mesh chain that hits cellular data or Wi-Fi automatically uploads accumulated distress packets to command backend.
              </p>
            </CardContent>
          </Card>

          {/* Feature 5 */}
          <Card className="glass-card border-slate-200 dark:border-slate-800 hover:border-purple-500/40 transition-all duration-300 hover:-translate-y-1">
            <CardContent className="p-6 space-y-4">
              <div className="w-12 h-12 rounded-xl bg-purple-500/10 border border-purple-500/20 flex items-center justify-center text-purple-500 dark:text-purple-400">
                <Lock className="h-6 w-6" />
              </div>
              <h3 className="text-lg font-bold text-slate-900 dark:text-slate-100">AES-256 Encrypted Telemetry</h3>
              <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
                Protects citizen privacy during relay hops while allowing authorized emergency dispatch units to inspect location, victim contact, and emergency status.
              </p>
            </CardContent>
          </Card>

          {/* Feature 6 */}
          <Card className="glass-card border-slate-200 dark:border-slate-800 hover:border-blue-500/40 transition-all duration-300 hover:-translate-y-1">
            <CardContent className="p-6 space-y-4">
              <div className="w-12 h-12 rounded-xl bg-blue-500/10 border border-blue-500/20 flex items-center justify-center text-blue-500 dark:text-blue-400">
                <Smartphone className="h-6 w-6" />
              </div>
              <h3 className="text-lg font-bold text-slate-900 dark:text-slate-100">Offline P2P App Sharing</h3>
              <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
                Integrated Wi-Fi Direct and local Bluetooth file transfer enabling citizens to install the AGAP APK phone-to-phone in dead zones without internet.
              </p>
            </CardContent>
          </Card>
        </div>
      </section>

      {/* How It Works Flow Section */}
      <section id="how-it-works" className="py-20 px-6 lg:px-12 max-w-7xl mx-auto border-t border-slate-200 dark:border-slate-800/80">
        <div className="text-center space-y-3 mb-16">
          <Badge variant="outline" className="border-sky-500/30 text-sky-600 dark:text-sky-400 bg-sky-50 dark:bg-sky-950/30 px-3 py-1 text-xs">
            DUAL-CHANNEL AUTO-HANDOFF
          </Badge>
          <h2 className="text-3xl sm:text-4xl font-extrabold text-slate-900 dark:text-slate-100">
            How AGAP Guarantees Delivery
          </h2>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 relative">
          <div className="bg-white dark:bg-slate-900/60 p-8 rounded-2xl border border-slate-200 dark:border-slate-800 space-y-4 relative shadow-sm">
            <div className="w-10 h-10 rounded-full bg-red-500/20 text-red-500 dark:text-red-400 border border-red-500/30 flex items-center justify-center font-bold font-mono">
              01
            </div>
            <h3 className="text-xl font-bold text-slate-900 dark:text-slate-100">SOS Trigger & Fallback Detection</h3>
            <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
              User taps SOS. AGAP attempts direct HTTP cellular payload send. If cellular network is down or mobile load is 0, the app seamlessly activates local BLE 5.0 Extended Mesh advertising.
            </p>
          </div>

          <div className="bg-white dark:bg-slate-900/60 p-8 rounded-2xl border border-slate-200 dark:border-slate-800 space-y-4 relative shadow-sm">
            <div className="w-10 h-10 rounded-full bg-sky-500/20 text-sky-500 dark:text-sky-400 border border-sky-500/30 flex items-center justify-center font-bold font-mono">
              02
            </div>
            <h3 className="text-xl font-bold text-slate-900 dark:text-slate-100">Store-and-Forward Mesh Hopping</h3>
            <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
              Nearby phones with AGAP passively detect the distress packet, increment the hop count, store it in local Room SQLite, and re-broadcast it to surrounding nodes.
            </p>
          </div>

          <div className="bg-white dark:bg-slate-900/60 p-8 rounded-2xl border border-slate-200 dark:border-slate-800 space-y-4 relative shadow-sm">
            <div className="w-10 h-10 rounded-full bg-emerald-500/20 text-emerald-500 dark:text-emerald-400 border border-emerald-500/30 flex items-center justify-center font-bold font-mono">
              03
            </div>
            <h3 className="text-xl font-bold text-slate-900 dark:text-slate-100">Responder Encounter / Cloud Uplink</h3>
            <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
              Packet either encounters a physical responder unit matching the Target ID (triggering immediate local siren) or hits any phone entering cell service (initiating Cloud Uplink to Go backend).
            </p>
          </div>
        </div>
      </section>

      {/* The Fellowship Team Section */}
      <section id="team" className="py-20 px-6 lg:px-12 max-w-7xl mx-auto border-t border-slate-200 dark:border-slate-800/80">
        <div className="text-center space-y-3 mb-16">
          <Badge variant="outline" className="border-amber-500/30 text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-950/30 px-3 py-1 text-xs">
            THE FELLOWSHIP (TRAVELING SALESMAN)
          </Badge>
          <h2 className="text-3xl sm:text-4xl font-extrabold text-slate-900 dark:text-slate-100">
            Meet the Builders
          </h2>
          <p className="text-slate-600 dark:text-slate-400 text-sm max-w-xl mx-auto">
            Returned to the People of Ibalong — Heroes of Innovation Challenge 2026, Legazpi City.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {[
            {
              name: 'JAMES RYAN SENTELICES GALLEGO',
              role: 'TEAM LEADER',
              skills: [
                'SOFTWARE DEVELOPMENT',
                'AI/ML',
                'UI/UX',
                'DISASTER RISK REDUCTION',
                'EDUCATION',
                'COMMUNITY DEVELOPMENT'
              ]
            },
            {
              name: 'DIOMAR BALASTA MOLINA',
              role: 'TEAM MEMBER',
              skills: [
                'SOFTWARE DEVELOPMENT',
                'RESEARCH'
              ]
            },
            {
              name: 'JAY VIRGIL ALTO ROMERO',
              role: 'TEAM MEMBER',
              skills: [
                'SOFTWARE DEVELOPMENT',
                'UI/UX',
                'CREATIVE INDUSTRIES'
              ]
            },
            {
              name: 'GIL IV MIGUEL SALVADOR I. CEA',
              role: 'TEAM MEMBER',
              skills: [
                'SOFTWARE DEVELOPMENT',
                'AI/ML',
                'DATA ANALYTICS',
                'COMMUNITY DEVELOPMENT',
                'UI/UX'
              ]
            }
          ].map((member, i) => (
            <Card key={i} className="bg-white dark:bg-slate-900/90 border-slate-200 dark:border-slate-800 hover:border-sky-500/40 transition-all p-6 shadow-sm">
              <div className="flex flex-col sm:flex-row items-start gap-4">
                <div className="w-14 h-14 rounded-xl bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 flex items-center justify-center text-lg font-extrabold text-red-500 dark:text-red-400 shrink-0">
                  {member.name.split(' ')[0][0]}{member.name.split(' ')[1][0]}
                </div>
                <div className="space-y-3 flex-1">
                  <div>
                    <h3 className="font-extrabold text-slate-900 dark:text-slate-100 text-base tracking-tight">{member.name}</h3>
                    <p className="text-xs text-slate-500 dark:text-slate-400 font-mono font-semibold uppercase tracking-wider mt-0.5">{member.role}</p>
                  </div>
                  <div className="flex flex-wrap gap-1.5 pt-1">
                    {member.skills.map((skill, sIdx) => (
                      <span
                        key={sIdx}
                        className="text-[10px] font-mono px-2 py-0.5 rounded border border-cyan-500/30 text-cyan-600 dark:text-cyan-400 bg-cyan-50 dark:bg-cyan-950/40 font-semibold"
                      >
                        {skill}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            </Card>
          ))}
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-slate-200 dark:border-slate-800/80 bg-white dark:bg-slate-950 px-6 lg:px-12 py-10 text-xs text-slate-500 flex flex-col sm:flex-row items-center justify-between gap-4 max-w-7xl mx-auto">
        <div className="flex items-center gap-2">
          <Shield className="h-4 w-4 text-red-500" />
          <span className="font-semibold text-slate-800 dark:text-slate-300">AGAP Project 2026</span>
          <span>— Built for Legazpi City DRRMO</span>
        </div>
        <div className="text-slate-500 dark:text-slate-400">
          Ibalong Festival 2026 · Heroes of Innovation Challenge
        </div>
      </footer>

      {/* Download Modal - Coming Soon */}
      <Dialog open={downloadModalOpen} onOpenChange={setDownloadModalOpen}>
        <div className="space-y-4 py-2">
          <div className="flex items-center gap-3">
            <div className="p-3 bg-emerald-500/10 rounded-xl border border-emerald-500/30">
              <Smartphone className="h-7 w-7 text-emerald-500 dark:text-emerald-400" />
            </div>
            <div>
              <h3 className="text-lg font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2">
                AGAP Android Release
                <Badge className="bg-amber-500/20 text-amber-600 dark:text-amber-400 border-amber-500/30 text-[10px]">COMING SOON</Badge>
              </h3>
              <p className="text-xs text-slate-500 dark:text-slate-400">BLE 5.0 Mesh & Wi-Fi Direct APK Package</p>
            </div>
          </div>

          <div className="p-4 bg-slate-50 dark:bg-slate-950 rounded-xl border border-slate-200 dark:border-slate-800 text-xs text-slate-700 dark:text-slate-300 space-y-2 font-mono">
            <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 border-b border-slate-200 dark:border-slate-800 pb-2">
              <span>Target OS:</span>
              <span className="text-slate-900 dark:text-slate-100">Android 9.0+ (API 28)</span>
            </div>
            <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 border-b border-slate-200 dark:border-slate-800 pb-2">
              <span>Required Hardware:</span>
              <span className="text-slate-900 dark:text-slate-100">Bluetooth 5.0 (Extended Adv)</span>
            </div>
            <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 border-b border-slate-200 dark:border-slate-800 pb-2">
              <span>P2P Transfer:</span>
              <span className="text-slate-900 dark:text-slate-100">Wi-Fi Direct + Local BLE</span>
            </div>
            <div className="flex items-center justify-between text-slate-500 dark:text-slate-400">
              <span>Status:</span>
              <span className="text-emerald-600 dark:text-emerald-400 font-semibold">Final Staging Build</span>
            </div>
          </div>

          <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed">
            The AGAP Android application is undergoing local mesh latency benchmarks for Legazpi City responders. In the meantime, you can explore the live dispatch interface.
          </p>

          <div className="flex items-center justify-end gap-3 pt-2">
            <Button variant="outline" onClick={() => setDownloadModalOpen(false)}>
              Close
            </Button>
            <Link to="/dashboard" onClick={() => setDownloadModalOpen(false)}>
              <Button className="bg-emerald-600 hover:bg-emerald-500 text-white font-semibold">
                Explore Command Center
              </Button>
            </Link>
          </div>
        </div>
      </Dialog>
    </div>
  );
};
