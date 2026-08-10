import React, { useEffect, useState } from 'react';
import { API_BASE_URL, apiService } from '@/services/api';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
import { useWebSocket } from '@/context/WebSocketContext';
import {
  loadDispatcherPreferences,
  saveDispatcherPreferences,
  type DispatcherPreferences,
} from '@/lib/dispatcherPreferences';
import type { DatabaseTable, EmergencyContact, SystemInfoResponse, UserDevice } from '@/types/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  X,
  Settings,
  BellRing,
  Check,
  Database,
  User,
  Smartphone,
  PhoneCall,
  ExternalLink,
  RefreshCw,
  Trash2,
  Plus,
  Sun,
  Moon,
  PanelLeftClose,
  Clock,
  Wifi,
  WifiOff,
  Server,
  ServerOff,
} from 'lucide-react';

interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  defaultTab?: 'protocol' | 'explorer' | 'profile';
}

export const SettingsModal: React.FC<SettingsModalProps> = ({ isOpen, onClose, defaultTab = 'protocol' }) => {
  const { user, refreshUser } = useAuth();
  const { theme, setTheme } = useTheme();
  const { isConnected } = useWebSocket();
  const [activeTab, setActiveTab] = useState<string>(defaultTab);

  // Dashboard preferences
  const [preferences, setPreferences] = useState<DispatcherPreferences>(loadDispatcherPreferences);
  const [selectedTheme, setSelectedTheme] = useState<'light' | 'dark'>(theme);
  const [savedSuccess, setSavedSuccess] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  // System & DB Explorer state
  const [sysInfo, setSysInfo] = useState<SystemInfoResponse | null>(null);
  const [dbTables, setDbTables] = useState<DatabaseTable[]>([]);
  const [infoLoading, setInfoLoading] = useState(false);
  const [infoError, setInfoError] = useState<string | null>(null);

  // Profile & Devices & Contacts state
  const [profileName, setProfileName] = useState(user?.name || '');
  const [profileEmail, setProfileEmail] = useState(user?.email || '');
  const [profilePhone, setProfilePhone] = useState(user?.phone_number || '');
  const [profileNationalId, setProfileNationalId] = useState(user?.national_id || '');
  const [profileSaving, setProfileSaving] = useState(false);
  const [profileSuccess, setProfileSuccess] = useState<string | null>(null);

  const [devices, setDevices] = useState<UserDevice[]>([]);
  const [contacts, setContacts] = useState<EmergencyContact[]>([]);
  const [devicesLoading, setDevicesLoading] = useState(false);

  // New Contact dialog inputs
  const [newContactName, setNewContactName] = useState('');
  const [newContactPhone, setNewContactPhone] = useState('');
  const [newContactRel, setNewContactRel] = useState('');
  const [contactAdding, setContactAdding] = useState(false);

  // System status indicators (moved from AppShell)
  const [currentTime, setCurrentTime] = useState('');
  const [apiOnline, setApiOnline] = useState<boolean | null>(null);

  useEffect(() => {
    if (user) {
      setProfileName(user.name || '');
      setProfileEmail(user.email || '');
      setProfilePhone(user.phone_number || '');
      setProfileNationalId(user.national_id || '');
    }
  }, [user]);

  // Load system info & tables when explorer tab is activated
  const loadSystemExplorerData = async () => {
    setInfoLoading(true);
    setInfoError(null);
    try {
      const [infoRes, tablesRes] = await Promise.all([
        apiService.getSystemInfo().catch(() => null),
        apiService.getDatabaseTables().catch(() => []),
      ]);
      setSysInfo(infoRes);
      setDbTables(tablesRes);
    } catch (err) {
      setInfoError(err instanceof Error ? err.message : 'Failed to load system info');
    } finally {
      setInfoLoading(false);
    }
  };

  // Load devices and contacts when profile tab is activated
  const loadProfileData = async () => {
    setDevicesLoading(true);
    try {
      const [devs, conts] = await Promise.all([
        apiService.getUserDevices().catch(() => []),
        apiService.getEmergencyContacts().catch(() => []),
      ]);
      setDevices(devs);
      setContacts(conts);
    } finally {
      setDevicesLoading(false);
    }
  };

  // Fetch API health status
  const checkApiHealth = async () => {
    try {
      await apiService.getHealth();
      setApiOnline(true);
    } catch {
      setApiOnline(false);
    }
  };

  // Time updater
  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setCurrentTime(now.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' }));
    };
    updateTime();
    const timer = setInterval(updateTime, 1000);
    return () => clearInterval(timer);
  }, []);

  // API health polling
  useEffect(() => {
    checkApiHealth();
    const interval = setInterval(checkApiHealth, 10000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (isOpen) {
      setPreferences(loadDispatcherPreferences());
      setSelectedTheme(theme);
      if (activeTab === 'explorer') loadSystemExplorerData();
      if (activeTab === 'profile') loadProfileData();
    }
  }, [isOpen, activeTab]);

  if (!isOpen) return null;

  const updatePreference = <K extends keyof DispatcherPreferences>(field: K, value: DispatcherPreferences[K]) => {
    setPreferences((prev) => ({ ...prev, [field]: value }));
  };

  const handleSavePreferences = (e: React.FormEvent) => {
    e.preventDefault();
    setSaveError(null);
    try {
      saveDispatcherPreferences(preferences);
      setTheme(selectedTheme);
    } catch {
      setSaveError('Could not save settings.');
      return;
    }
    setSavedSuccess(true);
    setTimeout(() => {
      setSavedSuccess(false);
      onClose();
    }, 800);
  };

  const handleUpdateProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setProfileSaving(true);
    setProfileSuccess(null);
    try {
      await apiService.updateMyProfile({
        name: profileName,
        email: profileEmail,
        phone_number: profilePhone,
        national_id: profileNationalId,
      });
      await refreshUser();
      setProfileSuccess('Profile details updated successfully.');
      setTimeout(() => setProfileSuccess(null), 3000);
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to update profile');
    } finally {
      setProfileSaving(false);
    }
  };

  const handleUnlinkDevice = async (deviceId: number) => {
    try {
      await apiService.deleteUserDevice(deviceId);
      loadProfileData();
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to unlink device');
    }
  };

  const handleAddContact = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newContactName || !newContactPhone) return;
    setContactAdding(true);
    try {
      await apiService.addEmergencyContact({
        contact_name: newContactName,
        phone_number: newContactPhone,
        relationship: newContactRel,
      });
      setNewContactName('');
      setNewContactPhone('');
      setNewContactRel('');
      loadProfileData();
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to add contact');
    } finally {
      setContactAdding(false);
    }
  };

  const handleDeleteContact = async (contactId: number) => {
    try {
      await apiService.deleteEmergencyContact(contactId);
      loadProfileData();
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to delete contact');
    }
  };

  return (
    <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-in fade-in">
      <div className="bg-white dark:bg-[#0F1214] border border-[#E7E3DC] dark:border-[#26292C] w-full max-w-2xl rounded-[2px] shadow-2xl overflow-hidden font-sans flex flex-col max-h-[90vh]">
        {/* Top Header */}
        <div className="px-6 py-4 border-b border-[#E7E3DC] dark:border-[#26292C] flex items-center justify-between bg-[#F7F5F2] dark:bg-[#181C1D] shrink-0">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-[2px] bg-[#1F91A8]/10 text-[#1F91A8]">
              <Settings className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-[#0A0A0A] dark:text-[#F2F0EC]">System & Dispatcher Preferences</h2>
              <p className="text-xs text-[#6B6864] dark:text-[#9C9994]">Dashboard behavior, backend health, and account data</p>
            </div>
          </div>
          <button onClick={onClose} className="text-[#6B6864] dark:text-[#9C9994] hover:text-[#0A0A0A] dark:hover:text-white p-1.5 rounded-[2px] hover:bg-[#E7E3DC] dark:hover:bg-[#26292C]">
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Tab Selection */}
        <div className="px-6 pt-3 border-b border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] shrink-0">
          <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
            <TabsList className="grid grid-cols-3 w-full">
              <TabsTrigger value="protocol" className="text-xs flex items-center gap-1.5">
                <Settings className="h-3.5 w-3.5" /> Preferences
              </TabsTrigger>
              <TabsTrigger value="explorer" className="text-xs flex items-center gap-1.5">
                <Database className="h-3.5 w-3.5" /> System & DB Info
              </TabsTrigger>
              <TabsTrigger value="profile" className="text-xs flex items-center gap-1.5">
                <User className="h-3.5 w-3.5" /> My Profile
              </TabsTrigger>
            </TabsList>
          </Tabs>
        </div>

        {/* Tab 1: Functional dashboard preferences */}
        {activeTab === 'protocol' && (
          <form onSubmit={handleSavePreferences} className="p-6 space-y-5 overflow-y-auto">
            <div className="space-y-3">
              <label className="text-[11px] font-bold uppercase tracking-wider text-[#6B6864] dark:text-[#9C9994] flex items-center gap-1.5">
                {selectedTheme === 'dark' ? <Moon className="h-3.5 w-3.5 text-[#1F91A8]" /> : <Sun className="h-3.5 w-3.5 text-[#F2A540]" />} APPEARANCE
              </label>
              <div className="grid grid-cols-2 gap-3">
                {(['dark', 'light'] as const).map((value) => (
                  <button key={value} type="button" onClick={() => setSelectedTheme(value)} className={`rounded-[2px] border p-3 text-left text-xs transition-colors ${selectedTheme === value ? 'border-[#1F91A8] bg-[#1F91A8]/10' : 'border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] hover:bg-[#E7E3DC] dark:hover:bg-[#26292C]'}`}>
                    <div className="flex items-center gap-2 font-bold text-[#0A0A0A] dark:text-[#F2F0EC]">{value === 'dark' ? <Moon className="h-4 w-4" /> : <Sun className="h-4 w-4" />}{value === 'dark' ? 'Dark' : 'Light'}</div>
                    <div className="mt-1 text-[11px] text-[#6B6864] dark:text-[#9C9994]">Apply the {value} dashboard theme</div>
                  </button>
                ))}
              </div>
            </div>

            <div className="space-y-3 pt-3 border-t border-[#E7E3DC] dark:border-[#26292C]">
              <label className="text-[11px] font-bold uppercase tracking-wider text-[#6B6864] dark:text-[#9C9994] flex items-center gap-1.5">
                <BellRing className="h-3.5 w-3.5 text-[#F2A540]" /> ALERTS & LAYOUT
              </label>
              <div className="p-3 bg-[#F7F5F2] dark:bg-[#181C1D] rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] flex items-center justify-between text-xs">
                <div>
                  <div className="font-bold text-[#0A0A0A] dark:text-[#F2F0EC]">Auto Emergency Siren</div>
                  <div className="text-[11px] text-[#6B6864] dark:text-[#9C9994]">Trigger audio siren on high priority SOS</div>
                </div>
                <input
                  type="checkbox"
                  checked={preferences.soundEnabled}
                  onChange={(e) => updatePreference('soundEnabled', e.target.checked)}
                  className="h-4 w-4 rounded border-[#E7E3DC] dark:border-[#26292C] text-[#1F91A8] focus:ring-[#1F91A8] cursor-pointer"
                />
              </div>
              <div className="p-3 bg-[#F7F5F2] dark:bg-[#181C1D] rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] flex items-center justify-between text-xs">
                <div className="flex items-start gap-2">
                  <PanelLeftClose className="mt-0.5 h-4 w-4 text-[#1F91A8]" />
                  <div><div className="font-bold text-[#0A0A0A] dark:text-[#F2F0EC]">Start With Collapsed Sidebar</div><div className="text-[11px] text-[#6B6864] dark:text-[#9C9994]">Use the compact navigation layout after reload and login</div></div>
                </div>
                <input type="checkbox" checked={preferences.sidebarCollapsed} onChange={(e) => updatePreference('sidebarCollapsed', e.target.checked)} className="h-4 w-4 rounded border-[#E7E3DC] dark:border-[#26292C] text-[#1F91A8] focus:ring-[#1F91A8] cursor-pointer" />
              </div>
            </div>

            {saveError && (
              <div className="text-xs text-red-500 font-semibold bg-red-500/10 p-2 rounded border border-red-500/20">
                {saveError}
              </div>
            )}

            <div className="pt-3 border-t border-[#E7E3DC] dark:border-[#26292C] flex items-center justify-between">
              {savedSuccess ? (
                <Badge className="bg-emerald-500 text-white border-0 text-xs px-3 py-1 flex items-center gap-1">
                  <Check className="h-3.5 w-3.5" /> Preferences Saved
                </Badge>
              ) : (
                <span className="text-[11px] text-[#6B6864] dark:text-[#9C9994]">Saved in this browser</span>
              )}
              <div className="flex gap-2">
                <Button type="button" variant="outline" size="sm" onClick={onClose} className="h-9 text-xs rounded-[2px] border-[#E7E3DC] dark:border-[#26292C]">
                  Cancel
                </Button>
                <Button type="submit" size="sm" className="h-9 text-xs rounded-[2px] bg-[#1F91A8] hover:bg-[#187588] text-white font-bold px-4">
                  Save Preferences
                </Button>
              </div>
            </div>
          </form>
        )}

        {/* Tab 2: System & DB Info Explorer */}
        {activeTab === 'explorer' && (
          <div className="p-6 space-y-5 overflow-y-auto">
            {/* System Status Cards (moved from AppShell header) */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {/* Current Time */}
              <div className="p-3 rounded-[2px] bg-[#F7F5F2] dark:bg-[#181C1D] border border-[#E7E3DC] dark:border-[#26292C]">
                <div className="text-[10px] text-[#6B6864] dark:text-[#9C9994] font-semibold uppercase flex items-center gap-1">
                  <Clock className="h-3 w-3" /> Server Time
                </div>
                <div className="font-mono text-sm font-bold text-[#0A0A0A] dark:text-[#F2F0EC] mt-1">{currentTime || '--:--:--'}</div>
              </div>

              {/* API Status */}
              <div className="p-3 rounded-[2px] bg-[#F7F5F2] dark:bg-[#181C1D] border border-[#E7E3DC] dark:border-[#26292C]">
                <div className="text-[10px] text-[#6B6864] dark:text-[#9C9994] font-semibold uppercase flex items-center gap-1">
                  {apiOnline === true ? <Server className="h-3 w-3 text-emerald-500" /> : <ServerOff className="h-3 w-3 text-red-500" />} API
                </div>
                <div className={`font-bold text-sm mt-1 ${apiOnline === true ? 'text-emerald-600 dark:text-emerald-400' : apiOnline === false ? 'text-red-600 dark:text-red-400' : 'text-[#6B6864] dark:text-[#9C9994]'}`}>
                  {apiOnline === true ? 'ONLINE' : apiOnline === false ? 'OFFLINE' : 'Checking…'}
                </div>
              </div>

              {/* WebSocket Status */}
              <div className="p-3 rounded-[2px] bg-[#F7F5F2] dark:bg-[#181C1D] border border-[#E7E3DC] dark:border-[#26292C]">
                <div className="text-[10px] text-[#6B6864] dark:text-[#9C9994] font-semibold uppercase flex items-center gap-1">
                  {isConnected ? <Wifi className="h-3 w-3 text-[#1F91A8]" /> : <WifiOff className="h-3 w-3 text-[#6B6864] dark:text-[#9C9994]" />} WebSocket
                </div>
                <div className={`font-bold text-sm mt-1 ${isConnected ? 'text-[#1F91A8]' : 'text-[#6B6864] dark:text-[#9C9994]'}`}>
                  {isConnected ? 'CONNECTED' : 'DISCONNECTED'}
                </div>
              </div>

              {/* Engine Status (from sysInfo) */}
              <div className="p-3 rounded-[2px] bg-[#F7F5F2] dark:bg-[#181C1D] border border-[#E7E3DC] dark:border-[#26292C]">
                <div className="text-[10px] text-[#6B6864] dark:text-[#9C9994] font-semibold uppercase">Engine</div>
                <div className="font-bold text-sm mt-1 text-emerald-600 dark:text-emerald-400 uppercase">
                  {sysInfo?.status || 'Loading…'}
                </div>
              </div>
            </div>

            {/* API Base URL */}
            <div className="p-3 rounded-[2px] bg-[#F7F5F2] dark:bg-[#181C1D] border border-[#E7E3DC] dark:border-[#26292C] text-xs flex flex-wrap items-center justify-between gap-2">
              <span className="font-medium text-[#6B6864] dark:text-[#9C9994]">API Endpoint</span>
              <code className="font-mono text-[11px] text-[#0A0A0A] dark:text-[#F2F0EC] break-all">{API_BASE_URL}</code>
            </div>

            <div className="flex items-center justify-between">
              <div className="text-xs font-bold uppercase tracking-wider text-[#6B6864] dark:text-[#9C9994] flex items-center gap-1.5">
                <Database className="h-4 w-4 text-[#1F91A8]" /> DATABASE TABLES
              </div>
              <Button size="sm" variant="outline" onClick={loadSystemExplorerData} disabled={infoLoading} className="h-8 gap-1.5 text-xs rounded-[2px] border-[#E7E3DC] dark:border-[#26292C]">
                <RefreshCw className={`h-3.5 w-3.5 ${infoLoading ? 'animate-spin' : ''}`} /> Refresh Stats
              </Button>
            </div>

            {infoError && (
              <div className="text-xs text-red-500 font-semibold bg-red-500/10 p-2 rounded border border-red-500/20">
                {infoError}
              </div>
            )}

            {infoLoading ? (
              <div className="py-8 text-center text-xs text-[#6B6864] dark:text-[#9C9994]">Fetching backend diagnostics...</div>
            ) : (
              <>
                {/* DB Table Row Counts */}
                <div className="space-y-2">
                  <div className="text-xs font-semibold text-[#0A0A0A] dark:text-[#F2F0EC] flex items-center justify-between">
                    <span>Tables &amp; Record Counts</span>
                    <span className="text-[11px] text-[#6B6864] dark:text-[#9C9994]">{dbTables.length} tables total</span>
                  </div>

                  <div className="border border-[#E7E3DC] dark:border-[#26292C] rounded-[2px] overflow-hidden max-h-48 overflow-y-auto">
                    <table className="w-full text-xs text-left">
                      <thead className="bg-[#F7F5F2] dark:bg-[#181C1D] text-[10px] uppercase font-bold text-[#6B6864] dark:text-[#9C9994] border-b border-[#E7E3DC] dark:border-[#26292C]">
                        <tr>
                          <th className="p-2 pl-3">Table Name</th>
                          <th className="p-2 text-right pr-3">Rows</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-[#E7E3DC] dark:divide-[#26292C] font-mono">
                        {dbTables.map((t) => (
                          <tr key={t.name} className="hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]">
                            <td className="p-2 pl-3 font-semibold text-[#0A0A0A] dark:text-[#F2F0EC]">{t.name}</td>
                            <td className="p-2 text-right pr-3 text-[#6B6864] dark:text-[#9C9994]">{t.count}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>

                {/* HTML Explorer Direct Link */}
                <div className="p-3.5 rounded-[2px] bg-[#1F91A8]/10 border border-[#1F91A8]/30 flex items-center justify-between text-xs">
                  <div>
                    <div className="font-bold text-[#0A0A0A] dark:text-[#F2F0EC]">Embedded HTML Database Explorer</div>
                    <div className="text-[11px] text-[#6B6864] dark:text-[#9C9994]">Explore interactive tables, columns, and rows directly in backend endpoint.</div>
                  </div>
                  <a
                    href="https://homeserver.tail8010bd.ts.net/info"
                    target="_blank"
                    rel="noreferrer"
                    className="px-3 py-1.5 rounded-[2px] bg-[#1F91A8] hover:bg-[#187588] text-white font-semibold text-xs flex items-center gap-1 shrink-0"
                  >
                    Open /info <ExternalLink className="h-3.5 w-3.5" />
                  </a>
                </div>
              </>
            )}
          </div>
        )}

        {/* Tab 3: My Profile, Devices & ICE Contacts */}
        {activeTab === 'profile' && (
          <div className="p-6 space-y-5 overflow-y-auto">
            {/* User Info Form */}
            <form onSubmit={handleUpdateProfile} className="space-y-3">
              <div className="text-xs font-bold uppercase tracking-wider text-[#6B6864] dark:text-[#9C9994] flex items-center gap-1.5">
                <User className="h-4 w-4 text-[#1F91A8]" /> DISPATCHER IDENTITY PROFILE
              </div>

              {profileSuccess && (
                <div className="p-2.5 rounded bg-emerald-500/10 border border-emerald-500/30 text-emerald-600 dark:text-emerald-400 text-xs font-semibold">
                  {profileSuccess}
                </div>
              )}

              <div className="grid grid-cols-2 gap-3 text-xs">
                <div className="space-y-1">
                  <span className="font-semibold text-[#0A0A0A] dark:text-[#F2F0EC]">Full Name</span>
                  <Input type="text" value={profileName} onChange={(e) => setProfileName(e.target.value)} className="h-9 text-xs rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214]" />
                </div>
                <div className="space-y-1">
                  <span className="font-semibold text-[#0A0A0A] dark:text-[#F2F0EC]">Email Address</span>
                  <Input type="email" value={profileEmail} onChange={(e) => setProfileEmail(e.target.value)} className="h-9 text-xs rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214]" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3 text-xs">
                <div className="space-y-1">
                  <span className="font-semibold text-[#0A0A0A] dark:text-[#F2F0EC]">Phone Number</span>
                  <Input type="text" value={profilePhone} onChange={(e) => setProfilePhone(e.target.value)} className="h-9 text-xs rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214]" />
                </div>
                <div className="space-y-1">
                  <span className="font-semibold text-[#0A0A0A] dark:text-[#F2F0EC]">National PhilSys ID</span>
                  <Input type="text" value={profileNationalId} onChange={(e) => setProfileNationalId(e.target.value)} className="h-9 text-xs rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214]" />
                </div>
              </div>

              <div className="flex justify-end pt-1">
                <Button type="submit" size="sm" disabled={profileSaving} className="h-8 text-xs rounded-[2px] bg-[#1F91A8] hover:bg-[#187588] text-white">
                  {profileSaving ? 'Saving...' : 'Update My Profile'}
                </Button>
              </div>
            </form>

            {/* Linked Devices */}
            <div className="space-y-2 pt-3 border-t border-[#E7E3DC] dark:border-[#26292C]">
              <div className="text-xs font-bold uppercase tracking-wider text-[#6B6864] dark:text-[#9C9994] flex items-center justify-between">
                <span className="flex items-center gap-1.5">
                  <Smartphone className="h-4 w-4 text-[#F2A540]" /> REGISTERED MOBILE DEVICES
                </span>
                <span className="text-[11px] font-normal">{devices.length} linked</span>
              </div>

              {devicesLoading ? (
                <div className="text-[11px] text-[#6B6864] dark:text-[#9C9994] py-2">Loading devices...</div>
              ) : devices.length === 0 ? (
                <div className="text-[11px] text-[#6B6864] dark:text-[#9C9994] py-2">No mobile devices linked to this user account.</div>
              ) : (
                <div className="space-y-1.5">
                  {devices.map((d) => (
                    <div key={d.id} className="p-2.5 rounded-[2px] bg-[#F7F5F2] dark:bg-[#181C1D] border border-[#E7E3DC] dark:border-[#26292C] flex items-center justify-between text-xs">
                      <div>
                        <div className="font-bold text-[#0A0A0A] dark:text-[#F2F0EC]">{d.device_name || 'Mobile Handset'}</div>
                        <div className="text-[10px] text-[#6B6864] dark:text-[#9C9994] font-mono">HW ID: {d.device_hardware_id}</div>
                      </div>
                      <Button
                        size="sm"
                        variant="ghost"
                        className="h-7 w-7 p-0 text-red-500 hover:bg-red-500/10 rounded-[2px]"
                        onClick={() => handleUnlinkDevice(d.id)}
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* ICE Emergency Contacts */}
            <div className="space-y-2 pt-3 border-t border-[#E7E3DC] dark:border-[#26292C]">
              <div className="text-xs font-bold uppercase tracking-wider text-[#6B6864] dark:text-[#9C9994] flex items-center justify-between">
                <span className="flex items-center gap-1.5">
                  <PhoneCall className="h-4 w-4 text-emerald-500" /> PERSONAL EMERGENCY CONTACTS (ICE)
                </span>
                <span className="text-[11px] font-normal">{contacts.length} contacts</span>
              </div>

              {contacts.map((c) => (
                <div key={c.id} className="p-2.5 rounded-[2px] bg-[#F7F5F2] dark:bg-[#181C1D] border border-[#E7E3DC] dark:border-[#26292C] flex items-center justify-between text-xs">
                  <div>
                    <div className="font-bold text-[#0A0A0A] dark:text-[#F2F0EC] flex items-center gap-2">
                      {c.contact_name} {c.is_primary && <Badge className="bg-emerald-500 text-white text-[9px] px-1.5 py-0 rounded-[2px]">PRIMARY ICE</Badge>}
                    </div>
                    <div className="text-[11px] text-[#6B6864] dark:text-[#9C9994]">
                      {c.phone_number} {c.relationship && `• ${c.relationship}`}
                    </div>
                  </div>
                  <Button size="sm" variant="ghost" className="h-7 w-7 p-0 text-red-500 hover:bg-red-500/10 rounded-[2px]" onClick={() => handleDeleteContact(c.id)}>
                    <Trash2 className="h-3.5 w-3.5" />
                  </Button>
                </div>
              ))}

              {/* Add New Contact Row */}
              <form onSubmit={handleAddContact} className="grid grid-cols-3 gap-2 pt-2 text-xs">
                <Input type="text" placeholder="Name" value={newContactName} onChange={(e) => setNewContactName(e.target.value)} className="h-8 text-xs rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214]" />
                <Input type="text" placeholder="Phone" value={newContactPhone} onChange={(e) => setNewContactPhone(e.target.value)} className="h-8 text-xs rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214]" />
                <div className="flex gap-1">
                  <Input type="text" placeholder="Relation" value={newContactRel} onChange={(e) => setNewContactRel(e.target.value)} className="h-8 text-xs rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] flex-1" />
                  <Button type="submit" size="sm" disabled={contactAdding} className="h-8 px-2.5 rounded-[2px] bg-emerald-600 hover:bg-emerald-700 text-white">
                    <Plus className="h-3.5 w-3.5" />
                  </Button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};