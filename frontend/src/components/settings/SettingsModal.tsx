import React, { useEffect, useState } from 'react';
import { apiService } from '@/services/api';
import { useAuth } from '@/context/AuthContext';
import type { DatabaseTable, EmergencyContact, SystemInfoResponse, UserDevice } from '@/types/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  X,
  Settings,
  Radio,
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
} from 'lucide-react';

interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  defaultTab?: 'protocol' | 'explorer' | 'profile';
}

const STORAGE_KEY = 'agap_dispatcher_settings';

interface DispatcherSettings {
  maxHops: string;
  packetTtl: string;
  channelMode: string;
  autoSiren: boolean;
  hubRegion: string;
}

const DEFAULT_SETTINGS: DispatcherSettings = {
  maxHops: '5',
  packetTtl: '10',
  channelMode: 'EXTENDED_ADV',
  autoSiren: true,
  hubRegion: 'Legazpi City (Region V)',
};

function loadSettings(): DispatcherSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_SETTINGS;
    const parsed = JSON.parse(raw);
    return { ...DEFAULT_SETTINGS, ...parsed };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

export const SettingsModal: React.FC<SettingsModalProps> = ({ isOpen, onClose, defaultTab = 'protocol' }) => {
  const { user, refreshUser } = useAuth();
  const [activeTab, setActiveTab] = useState<string>(defaultTab);

  // Protocol settings
  const [settings, setSettings] = useState<DispatcherSettings>(loadSettings);
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

  useEffect(() => {
    if (isOpen) {
      if (activeTab === 'explorer') loadSystemExplorerData();
      if (activeTab === 'profile') loadProfileData();
    }
  }, [isOpen, activeTab]);

  if (!isOpen) return null;

  const updateField = <K extends keyof DispatcherSettings>(field: K, value: DispatcherSettings[K]) => {
    setSettings((prev) => ({ ...prev, [field]: value }));
  };

  const handleSaveProtocol = (e: React.FormEvent) => {
    e.preventDefault();
    setSaveError(null);
    const hops = Number(settings.maxHops);
    const ttl = Number(settings.packetTtl);
    if (!Number.isInteger(hops) || hops < 1 || hops > 20) {
      setSaveError('Max hop count must be between 1 and 20.');
      return;
    }
    if (!Number.isInteger(ttl) || ttl < 1 || ttl > 60) {
      setSaveError('Packet TTL must be between 1 and 60.');
      return;
    }
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
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
      <div className="bg-card border border-border w-full max-w-2xl rounded-xl shadow-2xl overflow-hidden font-sans flex flex-col max-h-[90vh]">
        {/* Top Header */}
        <div className="px-6 py-4 border-b border-border flex items-center justify-between bg-muted/40 shrink-0">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-[#1F91A8]/10 text-[#1F91A8]">
              <Settings className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-card-foreground">System & Dispatcher Preferences</h2>
              <p className="text-xs text-muted-foreground">BLE Protocol, Database Health, & User Profile</p>
            </div>
          </div>
          <button onClick={onClose} className="text-muted-foreground hover:text-foreground p-1.5 rounded-md hover:bg-muted">
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Tab Selection */}
        <div className="px-6 pt-3 border-b border-border bg-card shrink-0">
          <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
            <TabsList className="grid grid-cols-3 w-full">
              <TabsTrigger value="protocol" className="text-xs flex items-center gap-1.5">
                <Radio className="h-3.5 w-3.5" /> BLE Protocol
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

        {/* Tab 1: Protocol & Siren */}
        {activeTab === 'protocol' && (
          <form onSubmit={handleSaveProtocol} className="p-6 space-y-5 overflow-y-auto">
            <div className="space-y-3">
              <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                <Radio className="h-3.5 w-3.5 text-[#1F91A8]" /> BLE MESH PROTOCOL CONFIG
              </label>
              <div className="grid grid-cols-2 gap-3 text-xs">
                <div className="space-y-1">
                  <span className="text-foreground font-semibold">Max Hop Count</span>
                  <Input
                    type="number"
                    min={1}
                    max={20}
                    value={settings.maxHops}
                    onChange={(e) => updateField('maxHops', e.target.value)}
                    className="h-9 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <span className="text-foreground font-semibold">Time To Live (TTL)</span>
                  <Input
                    type="number"
                    min={1}
                    max={60}
                    value={settings.packetTtl}
                    onChange={(e) => updateField('packetTtl', e.target.value)}
                    className="h-9 text-xs"
                  />
                </div>
              </div>

              <div className="space-y-1">
                <span className="text-xs text-foreground font-semibold">Primary Radio Advertising Layer</span>
                <Select
                  value={settings.channelMode}
                  onChange={(e) => updateField('channelMode', e.target.value)}
                  className="h-9 text-xs"
                >
                  <option value="EXTENDED_ADV">BLE 5.0 Extended Advertising (255 Bytes Payload)</option>
                  <option value="LEGACY_GATT">Legacy GATT Primary Sockets (31 Bytes Fallback)</option>
                </Select>
              </div>
            </div>

            <div className="space-y-3 pt-3 border-t border-border">
              <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                <BellRing className="h-3.5 w-3.5 text-[#F2A540]" /> DISPATCH ALERTS & REGION
              </label>

              <div className="space-y-1">
                <span className="text-xs text-foreground font-semibold">Assigned Region Station</span>
                <Select
                  value={settings.hubRegion}
                  onChange={(e) => updateField('hubRegion', e.target.value)}
                  className="h-9 text-xs"
                >
                  <option value="Legazpi City (Region V)">Legazpi City DRRMO HQ (Region V)</option>
                  <option value="Naga City (Region V)">Naga City DRRMO Sub-Station</option>
                  <option value="Sorsogon (Region V)">Sorsogon Coastal Patrol Sector</option>
                </Select>
              </div>

              <div className="p-3 bg-muted rounded-lg border border-border flex items-center justify-between text-xs">
                <div>
                  <div className="font-bold text-card-foreground">Auto Emergency Siren</div>
                  <div className="text-[11px] text-muted-foreground">Trigger audio siren on high priority SOS</div>
                </div>
                <input
                  type="checkbox"
                  checked={settings.autoSiren}
                  onChange={(e) => updateField('autoSiren', e.target.checked)}
                  className="h-4 w-4 rounded border-border text-[#1F91A8] focus:ring-[#1F91A8] cursor-pointer"
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
                  <Check className="h-3.5 w-3.5" /> Preferences Saved
                </Badge>
              ) : (
                <span className="text-[11px] text-muted-foreground font-mono">v1.0 BLE-MESH SPEC</span>
              )}
              <div className="flex gap-2">
                <Button type="button" variant="outline" size="sm" onClick={onClose} className="h-9 text-xs">
                  Cancel
                </Button>
                <Button type="submit" size="sm" className="h-9 text-xs bg-[#1F91A8] hover:bg-[#187588] text-white font-bold px-4">
                  Save Preferences
                </Button>
              </div>
            </div>
          </form>
        )}

        {/* Tab 2: System & DB Info Explorer */}
        {activeTab === 'explorer' && (
          <div className="p-6 space-y-4 overflow-y-auto">
            <div className="flex items-center justify-between">
              <div className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                <Database className="h-4 w-4 text-[#1F91A8]" /> ENGINE HEALTH & DATABASE TABLES
              </div>
              <Button size="sm" variant="outline" onClick={loadSystemExplorerData} disabled={infoLoading} className="h-8 gap-1.5 text-xs">
                <RefreshCw className={`h-3.5 w-3.5 ${infoLoading ? 'animate-spin' : ''}`} /> Refresh Stats
              </Button>
            </div>

            {infoError && (
              <div className="text-xs text-red-500 font-semibold bg-red-500/10 p-2 rounded border border-red-500/20">
                {infoError}
              </div>
            )}

            {infoLoading ? (
              <div className="py-8 text-center text-xs text-muted-foreground">Fetching backend diagnostics...</div>
            ) : (
              <>
                {/* System Info Stats Grid */}
                {sysInfo && (
                  <div className="grid grid-cols-3 gap-3 text-xs">
                    <div className="p-3 rounded-lg bg-muted border border-border">
                      <div className="text-[10px] text-muted-foreground font-semibold uppercase">Engine Status</div>
                      <div className="font-bold text-emerald-600 dark:text-emerald-400 mt-1 uppercase">{sysInfo.status}</div>
                    </div>
                    <div className="p-3 rounded-lg bg-muted border border-border">
                      <div className="text-[10px] text-muted-foreground font-semibold uppercase">DB Engine</div>
                      <div className="font-bold text-foreground mt-1 uppercase">{sysInfo.database?.type || 'SQLite3'}</div>
                    </div>
                    <div className="p-3 rounded-lg bg-muted border border-border">
                      <div className="text-[10px] text-muted-foreground font-semibold uppercase">Engine Uptime</div>
                      <div className="font-bold text-foreground font-mono mt-1">{sysInfo.api?.uptime || 'N/A'}</div>
                    </div>
                  </div>
                )}

                {/* DB Table Row Counts */}
                <div className="space-y-2">
                  <div className="text-xs font-semibold text-foreground flex items-center justify-between">
                    <span>Database Tables &amp; Record Counts</span>
                    <span className="text-[11px] text-muted-foreground">{dbTables.length} tables total</span>
                  </div>

                  <div className="border border-border rounded-lg overflow-hidden max-h-48 overflow-y-auto">
                    <table className="w-full text-xs text-left">
                      <thead className="bg-muted/50 text-[10px] uppercase font-bold text-muted-foreground border-b border-border">
                        <tr>
                          <th className="p-2 pl-3">Table Name</th>
                          <th className="p-2 text-right pr-3">Rows</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border font-mono">
                        {dbTables.map((t) => (
                          <tr key={t.name} className="hover:bg-muted/30">
                            <td className="p-2 pl-3 font-semibold text-foreground">{t.name}</td>
                            <td className="p-2 text-right pr-3 text-muted-foreground">{t.count}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>

                {/* HTML Explorer Direct Link */}
                <div className="p-3.5 rounded-lg bg-[#1F91A8]/10 border border-[#1F91A8]/30 flex items-center justify-between text-xs">
                  <div>
                    <div className="font-bold text-foreground">Embedded HTML Database Explorer</div>
                    <div className="text-[11px] text-muted-foreground">Explore interactive tables, columns, and rows directly in backend endpoint.</div>
                  </div>
                  <a
                    href="http://localhost:8080/info"
                    target="_blank"
                    rel="noreferrer"
                    className="px-3 py-1.5 rounded-md bg-[#1F91A8] hover:bg-[#187588] text-white font-semibold text-xs flex items-center gap-1 shrink-0"
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
              <div className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                <User className="h-4 w-4 text-[#1F91A8]" /> DISPATCHER IDENTITY PROFILE
              </div>

              {profileSuccess && (
                <div className="p-2.5 rounded bg-emerald-500/10 border border-emerald-500/30 text-emerald-600 dark:text-emerald-400 text-xs font-semibold">
                  {profileSuccess}
                </div>
              )}

              <div className="grid grid-cols-2 gap-3 text-xs">
                <div className="space-y-1">
                  <span className="font-semibold text-foreground">Full Name</span>
                  <Input type="text" value={profileName} onChange={(e) => setProfileName(e.target.value)} className="h-9 text-xs" />
                </div>
                <div className="space-y-1">
                  <span className="font-semibold text-foreground">Email Address</span>
                  <Input type="email" value={profileEmail} onChange={(e) => setProfileEmail(e.target.value)} className="h-9 text-xs" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3 text-xs">
                <div className="space-y-1">
                  <span className="font-semibold text-foreground">Phone Number</span>
                  <Input type="text" value={profilePhone} onChange={(e) => setProfilePhone(e.target.value)} className="h-9 text-xs" />
                </div>
                <div className="space-y-1">
                  <span className="font-semibold text-foreground">National PhilSys ID</span>
                  <Input type="text" value={profileNationalId} onChange={(e) => setProfileNationalId(e.target.value)} className="h-9 text-xs" />
                </div>
              </div>

              <div className="flex justify-end pt-1">
                <Button type="submit" size="sm" disabled={profileSaving} className="h-8 text-xs bg-[#1F91A8] hover:bg-[#187588]">
                  {profileSaving ? 'Saving...' : 'Update My Profile'}
                </Button>
              </div>
            </form>

            {/* Linked Devices */}
            <div className="space-y-2 pt-3 border-t border-border">
              <div className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center justify-between">
                <span className="flex items-center gap-1.5">
                  <Smartphone className="h-4 w-4 text-[#F2A540]" /> REGISTERED MOBILE DEVICES
                </span>
                <span className="text-[11px] font-normal">{devices.length} linked</span>
              </div>

              {devicesLoading ? (
                <div className="text-[11px] text-muted-foreground py-2">Loading devices...</div>
              ) : devices.length === 0 ? (
                <div className="text-[11px] text-muted-foreground py-2">No mobile devices linked to this user account.</div>
              ) : (
                <div className="space-y-1.5">
                  {devices.map((d) => (
                    <div key={d.id} className="p-2.5 rounded-lg bg-muted border border-border flex items-center justify-between text-xs">
                      <div>
                        <div className="font-bold text-foreground">{d.device_name || 'Mobile Handset'}</div>
                        <div className="text-[10px] text-muted-foreground font-mono">HW ID: {d.device_hardware_id}</div>
                      </div>
                      <Button
                        size="sm"
                        variant="ghost"
                        className="h-7 w-7 p-0 text-red-500 hover:bg-red-500/10"
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
            <div className="space-y-2 pt-3 border-t border-border">
              <div className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center justify-between">
                <span className="flex items-center gap-1.5">
                  <PhoneCall className="h-4 w-4 text-emerald-500" /> PERSONAL EMERGENCY CONTACTS (ICE)
                </span>
                <span className="text-[11px] font-normal">{contacts.length} contacts</span>
              </div>

              {contacts.map((c) => (
                <div key={c.id} className="p-2.5 rounded-lg bg-muted border border-border flex items-center justify-between text-xs">
                  <div>
                    <div className="font-bold text-foreground flex items-center gap-2">
                      {c.contact_name} {c.is_primary && <Badge className="bg-emerald-500 text-white text-[9px] px-1.5 py-0">PRIMARY ICE</Badge>}
                    </div>
                    <div className="text-[11px] text-muted-foreground">
                      {c.phone_number} {c.relationship && `• ${c.relationship}`}
                    </div>
                  </div>
                  <Button size="sm" variant="ghost" className="h-7 w-7 p-0 text-red-500 hover:bg-red-500/10" onClick={() => handleDeleteContact(c.id)}>
                    <Trash2 className="h-3.5 w-3.5" />
                  </Button>
                </div>
              ))}

              {/* Add New Contact Row */}
              <form onSubmit={handleAddContact} className="grid grid-cols-3 gap-2 pt-2 text-xs">
                <Input type="text" placeholder="Name" value={newContactName} onChange={(e) => setNewContactName(e.target.value)} className="h-8 text-xs" />
                <Input type="text" placeholder="Phone" value={newContactPhone} onChange={(e) => setNewContactPhone(e.target.value)} className="h-8 text-xs" />
                <div className="flex gap-1">
                  <Input type="text" placeholder="Relation" value={newContactRel} onChange={(e) => setNewContactRel(e.target.value)} className="h-8 text-xs flex-1" />
                  <Button type="submit" size="sm" disabled={contactAdding} className="h-8 px-2.5 bg-emerald-600 hover:bg-emerald-700 text-white">
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
