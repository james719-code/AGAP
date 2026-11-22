import React, { useCallback, useEffect, useRef, useState } from 'react';
import { API_BASE_URL, apiService } from '@/services/api';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
import { useWebSocket } from '@/context/WebSocketContext';
import { usesHostedApi } from '@/services/backend';
import {
  loadDispatcherPreferences,
  saveDispatcherPreferences,
  type DispatcherPreferences,
} from '@/lib/dispatcherPreferences';
import type { DatabaseTable, EmergencyContact, SystemInfoResponse, UserDevice } from '@/types/api';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
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

const SystemClock: React.FC = () => {
  const [time, setTime] = useState(() => new Date().toLocaleTimeString('en-US', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }));

  useEffect(() => {
    const timer = setInterval(() => {
      setTime(new Date().toLocaleTimeString('en-US', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      }));
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  return <div className="mt-1 font-mono text-sm font-bold text-foreground">{time}</div>;
};

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

  // System status indicators
  const [apiOnline, setApiOnline] = useState<boolean | null>(null);
  const systemDataLoadedRef = useRef(false);
  const profileDataLoadedRef = useRef(false);
  const closeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (user) {
      queueMicrotask(() => {
        setProfileName(user.name || '');
        setProfileEmail(user.email || '');
        setProfilePhone(user.phone_number || '');
        setProfileNationalId(user.national_id || '');
      });
    }
  }, [user]);

  const loadSystemExplorerData = useCallback(async () => {
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
  }, []);

  const loadProfileData = useCallback(async () => {
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
  }, []);

  const checkApiHealth = useCallback(async () => {
    try {
      await apiService.getHealth();
      setApiOnline(true);
    } catch {
      setApiOnline(false);
    }
  }, []);

  useEffect(() => {
    if (!isOpen || activeTab !== 'explorer') return;
    queueMicrotask(() => void checkApiHealth());
    const interval = setInterval(() => void checkApiHealth(), 30000);
    return () => clearInterval(interval);
  }, [activeTab, checkApiHealth, isOpen]);

  useEffect(() => {
    if (isOpen) {
      queueMicrotask(() => {
        setPreferences(loadDispatcherPreferences());
        setSelectedTheme(theme);
        if (activeTab === 'explorer' && !systemDataLoadedRef.current) {
          systemDataLoadedRef.current = true;
          void loadSystemExplorerData();
        }
        if (activeTab === 'profile' && !profileDataLoadedRef.current) {
          profileDataLoadedRef.current = true;
          void loadProfileData();
        }
      });
    }
  }, [activeTab, isOpen, loadProfileData, loadSystemExplorerData, theme]);

  useEffect(() => () => {
    if (closeTimerRef.current) clearTimeout(closeTimerRef.current);
  }, []);

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
    closeTimerRef.current = setTimeout(() => {
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
      void loadProfileData();
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
      void loadProfileData();
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to add contact');
    } finally {
      setContactAdding(false);
    }
  };

  const handleDeleteContact = async (contactId: number) => {
    try {
      await apiService.deleteEmergencyContact(contactId);
      void loadProfileData();
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to delete contact');
    }
  };

  return (
    <Dialog
      open={isOpen}
      onOpenChange={(open) => { if (!open) onClose(); }}
      contentClassName="inset-y-0 right-0 left-auto m-0 flex h-dvh max-h-dvh w-full max-w-md flex-col overflow-hidden rounded-none border-y-0 border-r-0 p-0 sm:rounded-l-lg sm:p-0"
    >
      <div className="flex shrink-0 items-center gap-3 border-b border-border bg-muted py-4 pr-14 pl-4 sm:pl-6">
        <div className="rounded-md bg-secondary p-2 text-secondary-foreground">
          <Settings className="size-5" aria-hidden="true" />
        </div>
        <div className="min-w-0">
          <DialogTitle>Settings</DialogTitle>
          <DialogDescription>Preferences, system status, and account</DialogDescription>
        </div>
      </div>

      {/* Tab Selection */}
      <div className="shrink-0 border-b border-border bg-card px-4 py-3 sm:px-6">
        <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
          <TabsList className="grid grid-cols-3 w-full">
            <TabsTrigger value="protocol" className="text-xs flex items-center gap-1.5">
              <Settings className="h-3.5 w-3.5" /> Preferences
            </TabsTrigger>
            <TabsTrigger value="explorer" className="text-xs flex items-center gap-1.5">
              <Database className="h-3.5 w-3.5" /> System
            </TabsTrigger>
            <TabsTrigger value="profile" className="text-xs flex items-center gap-1.5">
              <User className="h-3.5 w-3.5" /> Profile
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      {/* Tab 1: Preferences */}
      {activeTab === 'protocol' && (
        <form onSubmit={handleSavePreferences} className="p-6 space-y-5 overflow-y-auto">
          <div className="space-y-3">
            <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
              {selectedTheme === 'dark' ? <Moon className="h-3.5 w-3.5 text-[#1F91A8]" /> : <Sun className="h-3.5 w-3.5 text-[#F2A540]" />} Appearance
            </label>
            <div className="grid grid-cols-2 gap-3">
              {(['dark', 'light'] as const).map((value) => (
                <button key={value} type="button" aria-pressed={selectedTheme === value} onClick={() => setSelectedTheme(value)} className={`rounded-md border p-3 text-left text-xs transition-colors ${selectedTheme === value ? 'border-primary/40 bg-primary/10' : 'border-border bg-muted hover:bg-accent'}`}>
                  <div className="flex items-center gap-2 font-bold text-foreground">{value === 'dark' ? <Moon className="h-4 w-4" /> : <Sun className="h-4 w-4" />}{value === 'dark' ? 'Dark' : 'Light'}</div>
                  <div className="mt-1 text-[11px] text-muted-foreground">Use the {value} theme</div>
                </button>
              ))}
            </div>
          </div>

          <div className="space-y-3 pt-3 border-t border-border">
            <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
              <BellRing className="h-3.5 w-3.5 text-[#F2A540]" /> Alerts
            </label>
            <div className="p-3 bg-muted rounded-md border border-border flex items-center justify-between text-xs">
              <div>
                <div className="font-bold text-foreground">Emergency Siren</div>
                <div className="text-[11px] text-muted-foreground">Play an audio alert on high priority SOS</div>
              </div>
              <input
                aria-label="Emergency siren"
                type="checkbox"
                checked={preferences.soundEnabled}
                onChange={(e) => updatePreference('soundEnabled', e.target.checked)}
                className="h-4 w-4 shrink-0 rounded border-border text-[#1F91A8] focus:ring-[#1F91A8] cursor-pointer"
              />
            </div>
          </div>

          {saveError && (
            <div className="text-xs text-red-500 font-semibold bg-red-500/10 p-2 rounded border border-red-500/20">
              {saveError}
            </div>
          )}

          <div className="pt-3 border-t border-border flex items-center justify-between">
            {savedSuccess ? (
              <Badge className="bg-emerald-500 text-white border-0 text-xs px-3 py-1 flex items-center gap-1">
                <Check className="h-3.5 w-3.5" /> Saved
              </Badge>
            ) : (
              <span className="text-[11px] text-muted-foreground">Saved in this browser</span>
            )}
            <div className="flex gap-2">
              <Button type="button" variant="outline" size="sm" onClick={onClose} className="h-9 text-xs rounded-md border-border">
                Cancel
              </Button>
              <Button type="submit" size="sm" className="h-9 px-4 text-xs font-bold">
                Save
              </Button>
            </div>
          </div>
        </form>
      )}

      {/* Tab 2: System & DB Info */}
      {activeTab === 'explorer' && (
        <div className="p-6 space-y-5 overflow-y-auto">
          {/* Status row — one box, four aligned columns */}
          <div className="grid grid-cols-2 rounded-md border border-[#E7E3DC] bg-[#F7F5F2] dark:border-[#26292C] dark:bg-[#181C1D] sm:grid-cols-4 sm:divide-x sm:divide-[#E7E3DC] sm:dark:divide-[#26292C]">
            <div className="p-3">
              <div className="text-[10px] text-muted-foreground font-semibold uppercase flex items-center gap-1">
                <Clock className="h-3 w-3" /> Time
              </div>
              <SystemClock />
            </div>

            <div className="p-3">
              <div className="text-[10px] text-muted-foreground font-semibold uppercase flex items-center gap-1">
                {apiOnline === true ? <Server className="h-3 w-3 text-emerald-500" /> : <ServerOff className="h-3 w-3 text-red-500" />} API
              </div>
              <div className={`font-bold text-sm mt-1 ${apiOnline === true ? 'text-emerald-600 dark:text-emerald-400' : apiOnline === false ? 'text-red-600 dark:text-red-400' : 'text-muted-foreground'}`}>
                {apiOnline === true ? 'Online' : apiOnline === false ? 'Offline' : 'Checking…'}
              </div>
            </div>

            <div className="p-3">
              <div className="text-[10px] text-muted-foreground font-semibold uppercase flex items-center gap-1">
                {isConnected || usesHostedApi ? <Wifi className="h-3 w-3 text-[#1F91A8]" /> : <WifiOff className="h-3 w-3 text-muted-foreground" />}
                {usesHostedApi ? 'Updates' : 'Socket'}
              </div>
              <div className={`font-bold text-sm mt-1 ${isConnected ? 'text-[#1F91A8]' : 'text-muted-foreground'}`}>
                {usesHostedApi ? 'Auto-refresh' : isConnected ? 'Connected' : 'Disconnected'}
              </div>
            </div>

            <div className="p-3">
              <div className="text-[10px] text-muted-foreground font-semibold uppercase">Engine</div>
              <div className="font-bold text-sm mt-1 text-emerald-600 dark:text-emerald-400 capitalize">
                {sysInfo?.status || 'Loading…'}
              </div>
            </div>
          </div>

          <div className="flex items-center justify-between gap-2">
            <div className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5 min-w-0">
              <Database className="h-4 w-4 text-[#1F91A8] shrink-0" /> Database Tables
            </div>
            <Button size="sm" variant="outline" onClick={() => void loadSystemExplorerData()} disabled={infoLoading} className="h-8 gap-1.5 text-xs rounded-md border-border shrink-0">
              <RefreshCw className={`h-3.5 w-3.5 ${infoLoading ? 'animate-spin' : ''}`} /> Refresh
            </Button>
          </div>

          {infoError && (
            <div className="text-xs text-red-500 font-semibold bg-red-500/10 p-2 rounded border border-red-500/20">
              {infoError}
            </div>
          )}

          {infoLoading ? (
            <div className="py-8 text-center text-xs text-muted-foreground">Loading…</div>
          ) : (
            <div className="space-y-2">
              <div className="border border-border rounded-md overflow-hidden">
                <table className="w-full text-xs text-left">
                  <thead className="bg-muted text-[10px] uppercase font-bold text-muted-foreground border-b border-border">
                    <tr>
                      <th className="p-2 pl-3">Table</th>
                      <th className="p-2 text-right pr-3">Rows</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#E7E3DC] dark:divide-[#26292C] font-mono max-h-48 overflow-y-auto">
                    {dbTables.map((t) => (
                      <tr key={t.name} className="hover:bg-accent">
                        <td className="p-2 pl-3 font-semibold text-foreground">{t.name}</td>
                        <td className="p-2 text-right pr-3 text-muted-foreground">{t.count}</td>
                      </tr>
                    ))}
                    <tr>
                      <td className="p-2 pl-3 text-[11px] text-muted-foreground" colSpan={2}>
                        {dbTables.length} tables · API: <span className="font-mono break-all">{API_BASE_URL}</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <a
                href={API_BASE_URL ? `${API_BASE_URL.replace(/\/api\/v1\/?$/, '')}/info` : 'http://localhost:8088/info'}
                target="_blank"
                rel="noreferrer"
                className="w-full flex items-center justify-center gap-1.5 px-3 py-2 rounded-md border border-border hover:bg-accent text-foreground font-semibold text-xs"
              >
                Open Database Explorer <ExternalLink className="h-3.5 w-3.5" />
              </a>
            </div>
          )}
        </div>
      )}

      {/* Tab 3: Profile, Devices & Contacts */}
      {activeTab === 'profile' && (
        <div className="p-6 space-y-5 overflow-y-auto">
          {/* User Info Form */}
          <form onSubmit={handleUpdateProfile} className="space-y-3">
            <div className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
              <User className="h-4 w-4 text-[#1F91A8]" /> Profile
            </div>

            {profileSuccess && (
              <div className="p-2.5 rounded bg-emerald-500/10 border border-emerald-500/30 text-emerald-600 dark:text-emerald-400 text-xs font-semibold">
                {profileSuccess}
              </div>
            )}

            <div className="grid grid-cols-1 gap-3 text-xs sm:grid-cols-2">
              <div className="space-y-1">
                <label htmlFor="settingsmodal-full-name" className="font-semibold text-foreground">Full Name</label>
                <Input id="settingsmodal-full-name" type="text" value={profileName} onChange={(e) => setProfileName(e.target.value)} className="h-9 text-xs rounded-md border-border bg-card" />
              </div>
              <div className="space-y-1">
                <label htmlFor="settingsmodal-email" className="font-semibold text-foreground">Email</label>
                <Input id="settingsmodal-email" type="email" value={profileEmail} onChange={(e) => setProfileEmail(e.target.value)} className="h-9 text-xs rounded-md border-border bg-card" />
              </div>
              <div className="space-y-1">
                <label htmlFor="settingsmodal-phone-number" className="font-semibold text-foreground">Phone Number</label>
                <Input id="settingsmodal-phone-number" type="text" value={profilePhone} onChange={(e) => setProfilePhone(e.target.value)} className="h-9 text-xs rounded-md border-border bg-card" />
              </div>
              <div className="space-y-1">
                <label htmlFor="settingsmodal-national-id" className="font-semibold text-foreground">National ID</label>
                <Input id="settingsmodal-national-id" type="text" value={profileNationalId} onChange={(e) => setProfileNationalId(e.target.value)} className="h-9 text-xs rounded-md border-border bg-card" />
              </div>
            </div>

            <div className="flex justify-end pt-1">
              <Button type="submit" size="sm" disabled={profileSaving} className="h-8 text-xs rounded-md bg-[#1F91A8] hover:bg-[#187588] text-white">
                {profileSaving ? 'Saving...' : 'Update Profile'}
              </Button>
            </div>
          </form>

          {/* Linked Devices */}
          <div className="space-y-2 pt-3 border-t border-border">
            <div className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center justify-between">
              <span className="flex items-center gap-1.5">
                <Smartphone className="h-4 w-4 text-[#F2A540]" /> Devices
              </span>
              <span className="text-[11px] font-normal">{devices.length} linked</span>
            </div>

            {devicesLoading ? (
              <div className="text-[11px] text-muted-foreground py-2">Loading…</div>
            ) : devices.length === 0 ? (
              <div className="text-[11px] text-muted-foreground py-2">No devices linked to this account.</div>
            ) : (
              <div className="space-y-1.5">
                {devices.map((d) => (
                  <div key={d.id} className="p-2.5 rounded-md bg-muted border border-border flex items-center justify-between text-xs">
                    <div className="min-w-0">
                      <div className="font-bold text-foreground truncate">{d.device_name || 'Mobile Handset'}</div>
                      <div className="text-[10px] text-muted-foreground font-mono truncate">{d.device_hardware_id}</div>
                    </div>
                    <Button
                      size="sm"
                      variant="ghost"
                      className="h-7 w-7 p-0 shrink-0 text-red-500 hover:bg-red-500/10 rounded-md"
                      onClick={() => handleUnlinkDevice(d.id)}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Emergency Contacts */}
          <div className="space-y-2 pt-3 border-t border-border">
            <div className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center justify-between">
              <span className="flex items-center gap-1.5">
                <PhoneCall className="h-4 w-4 text-emerald-500" /> Emergency Contacts
              </span>
              <span className="text-[11px] font-normal">{contacts.length}</span>
            </div>

            {contacts.map((c) => (
              <div key={c.id} className="p-2.5 rounded-md bg-muted border border-border flex items-center justify-between text-xs">
                <div className="min-w-0">
                  <div className="font-bold text-foreground flex items-center gap-2 truncate">
                    {c.contact_name} {c.is_primary && <Badge className="bg-emerald-500 text-white text-[9px] px-1.5 py-0 rounded-md shrink-0">Primary</Badge>}
                  </div>
                  <div className="text-[11px] text-muted-foreground truncate">
                    {c.phone_number} {c.relationship && `• ${c.relationship}`}
                  </div>
                </div>
                <Button size="sm" variant="ghost" className="h-7 w-7 p-0 shrink-0 text-red-500 hover:bg-red-500/10 rounded-md" onClick={() => handleDeleteContact(c.id)} aria-label={`Remove contact ${c.contact_name}`}>
                  <Trash2 className="h-3.5 w-3.5" />
                </Button>
              </div>
            ))}

            <form onSubmit={handleAddContact} className="grid grid-cols-1 gap-2 pt-2 text-xs sm:grid-cols-3">
              <Input type="text" aria-label="Contact name" placeholder="Name" value={newContactName} onChange={(e) => setNewContactName(e.target.value)} className="h-8 text-xs rounded-md border-border bg-card" />
              <Input type="text" aria-label="Contact phone" placeholder="Phone" value={newContactPhone} onChange={(e) => setNewContactPhone(e.target.value)} className="h-8 text-xs rounded-md border-border bg-card" />
              <div className="flex gap-1">
                <Input type="text" aria-label="Contact relationship" placeholder="Relation" value={newContactRel} onChange={(e) => setNewContactRel(e.target.value)} className="h-8 text-xs rounded-md border-border bg-card flex-1 min-w-0" />
                <Button type="submit" aria-label="Add emergency contact" size="sm" disabled={contactAdding} className="h-8 px-2.5 rounded-md bg-emerald-600 hover:bg-emerald-700 text-white shrink-0">
                  <Plus className="h-3.5 w-3.5" />
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </Dialog>
  );
};
