import React, { useState, useEffect } from 'react';
import { useAgencies } from '@/hooks/useAgencies';
import { useAuth } from '@/context/AuthContext';
import type { Agency, AgencyType, Province, Municipality, Barangay } from '@/types/api';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { Building2, Pencil, Plus, Power } from 'lucide-react';
import { apiService } from '@/services/api';

const blankAgencyForm: Partial<Agency> = {
  code: '',
  name: '',
  type: 'DRRMO',
  region_code: '05',
  province_code: '05',
  muni_code: '12',
  barangay_code: '01',
  contact_number: '',
  latitude: 13.1391,
  longitude: 123.7438,
  is_active: true,
  operating_hours: '24/7',
};

const LAT_RANGE: [number, number] = [-90, 90];
const LON_RANGE: [number, number] = [-180, 180];

export const AgenciesPage: React.FC = () => {
  const PAGE_SIZE = 25;
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const { agencies, loading, error, createAgency, updateAgency } = useAgencies();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [editingAgency, setEditingAgency] = useState<Agency | null>(null);
  const [formData, setFormData] = useState<Partial<Agency>>(blankAgencyForm);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [togglingId, setTogglingId] = useState<number | null>(null);
  const [toggleError, setToggleError] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState(1);

  const [provinces, setProvinces] = useState<Province[]>([]);
  const [municipalities, setMunicipalities] = useState<Municipality[]>([]);
  const [barangays, setBarangays] = useState<Barangay[]>([]);
  const [geoLoading, setGeoLoading] = useState(false);

  const [selectedProvinceId, setSelectedProvinceId] = useState<string>('');
  const [selectedMuniId, setSelectedMuniId] = useState<string>('');
  const [selectedBrgyId, setSelectedBrgyId] = useState<string>('');

  useEffect(() => {
    let cancelled = false;

    const run = async () => {
      setGeoLoading(true);
      apiService
        .getProvinces()
        .then((list) => {
          if (cancelled) return;
          setProvinces(list || []);
        })
        .catch(() => {
          if (!cancelled) setProvinces([]);
        })
        .finally(() => {
          if (!cancelled) setGeoLoading(false);
        });
    };

    run();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;

    const run = async () => {
      if (!selectedProvinceId) {
        setMunicipalities([]);
        setSelectedMuniId('');
        setBarangays([]);
        setSelectedBrgyId('');
        return;
      }
      setGeoLoading(true);
      apiService
        .getMunicipalities(selectedProvinceId)
        .then((list) => {
          if (cancelled) return;
          setMunicipalities(list || []);
        })
        .catch(() => {
          if (!cancelled) setMunicipalities([]);
        })
        .finally(() => {
          if (!cancelled) setGeoLoading(false);
        });
    };

    run();

    return () => {
      cancelled = true;
    };
  }, [selectedProvinceId]);

  useEffect(() => {
    let cancelled = false;

    const run = async () => {
      if (!selectedMuniId) {
        setBarangays([]);
        setSelectedBrgyId('');
        return;
      }
      setGeoLoading(true);
      apiService
        .getBarangays(selectedMuniId)
        .then((list) => {
          if (cancelled) return;
          setBarangays(list || []);
        })
        .catch(() => {
          if (!cancelled) setBarangays([]);
        })
        .finally(() => {
          if (!cancelled) setGeoLoading(false);
        });
    };

    run();

    return () => {
      cancelled = true;
    };
  }, [selectedMuniId]);

  const validate = (data: Partial<Agency>): string | null => {
    if (!data.code?.trim()) return 'Agency code is required.';
    if (!data.name?.trim()) return 'Agency name is required.';
    if (!data.region_code?.trim() || !data.province_code?.trim() || !data.muni_code?.trim() || !data.barangay_code?.trim()) {
      return 'Region, province, municipality, and barangay codes are all required.';
    }
    if (data.latitude === undefined || Number.isNaN(data.latitude) || data.latitude < LAT_RANGE[0] || data.latitude > LAT_RANGE[1]) {
      return 'Latitude must be a valid number between -90 and 90.';
    }
    if (data.longitude === undefined || Number.isNaN(data.longitude) || data.longitude < LON_RANGE[0] || data.longitude > LON_RANGE[1]) {
      return 'Longitude must be a valid number between -180 and 180.';
    }
    return null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    const validationError = validate(formData);
    if (validationError) {
      setFormError(validationError);
      return;
    }

    setSaving(true);
    try {
      if (editingAgency) {
        await updateAgency(editingAgency.id, formData);
      } else {
        await createAgency(formData);
      }
      setIsCreateOpen(false);
      setEditingAgency(null);
      setFormData(blankAgencyForm);
      setSelectedProvinceId('');
      setSelectedMuniId('');
      setSelectedBrgyId('');
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to save agency. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  const openCreateDialog = () => {
    setEditingAgency(null);
    setFormData(blankAgencyForm);
    setSelectedProvinceId('');
    setSelectedMuniId('');
    setSelectedBrgyId('');
    setFormError(null);
    setIsCreateOpen(true);
  };

  const openEditDialog = (agency: Agency) => {
    setEditingAgency(agency);
    setFormError(null);
    setFormData({
      code: agency.code,
      name: agency.name,
      type: agency.type,
      region_code: agency.region_code,
      province_code: agency.province_code,
      muni_code: agency.muni_code,
      barangay_code: agency.barangay_code,
      contact_number: agency.contact_number,
      latitude: agency.latitude,
      longitude: agency.longitude,
      is_active: agency.is_active,
      operating_hours: agency.operating_hours,
    });
    setSelectedProvinceId('');
    setSelectedMuniId('');
    setSelectedBrgyId('');
    setIsCreateOpen(true);
  };

  const toggleAgencyActive = async (agency: Agency) => {
    setToggleError(null);
    setTogglingId(agency.id);
    try {
      await updateAgency(agency.id, { ...agency, is_active: !agency.is_active });
    } catch (err) {
      setToggleError(err instanceof Error ? err.message : `Failed to update status for ${agency.name}.`);
    } finally {
      setTogglingId(null);
    }
  };

  return (
    <div className="page-stack">
      {/* Page Header */}
      <div className="page-header">
        <div>
          <span className="page-eyebrow">Operations</span>
          <h2 className="page-title">Agencies</h2>
          <p className="page-description">
            Manage disaster response and law enforcement agencies.
          </p>
        </div>

        {isAdmin && (
          <Button
            onClick={openCreateDialog}
            className="bg-[#D15033] hover:bg-[#B5432A] text-white flex items-center gap-2 font-semibold rounded-[2px] shrink-0"
          >
            <Plus className="h-4 w-4" /> Add Agency
          </Button>
        )}
      </div>

      {toggleError && (
        <div className="rounded-[2px] border border-red-300 bg-red-50 dark:bg-red-900/20 px-4 py-2 text-sm text-red-700 dark:text-red-300">
          {toggleError}
        </div>
      )}

      {/* Agencies Table */}
      <div className="rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-[0_1px_2px_rgba(10,10,10,0.04)] overflow-hidden">
        <div className="py-2.5 px-4 border-b border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] flex items-center justify-between">
          <h3 className="text-sm font-semibold text-[#0A0A0A] dark:text-white flex items-center gap-2">
            Registered Agencies <span className="text-[#5C5A56] dark:text-[#B9B6B0] font-normal">({agencies.length})</span>
          </h3>
          <div className="flex items-center gap-2 text-[10px] text-[#5C5A56] dark:text-[#B9B6B0] font-medium">
            <span className="h-2 w-2 rounded-full bg-[#1F91A8] animate-pulse"></span> AUTO-SYNCING
          </div>
        </div>

        <div className="overflow-x-auto">
          {loading ? (
            <div className="p-8 text-center text-[#5C5A56] dark:text-[#B9B6B0] text-sm">Loading agencies...</div>
          ) : error ? (
            <div className="p-8 text-center text-red-600 dark:text-red-400 text-sm">{error}</div>
          ) : agencies.length === 0 ? (
            <div className="p-8 text-center text-[#5C5A56] dark:text-[#B9B6B0] text-sm">
              No agencies registered yet. {isAdmin ? 'Click above to add one.' : ''}
            </div>
          ) : (
            <table className="data-table">
              <thead className="bg-[#F7F5F2] dark:bg-[#181C1D] border-b border-[#E7E3DC] dark:border-[#26292C]">
                <tr>
                  <th className="text-left px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Code</th>
                  <th className="text-left px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Agency</th>
                  <th className="text-left px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Type</th>
                  <th className="text-left px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Status</th>
                  <th className="text-left px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Contact</th>
                  <th className="text-left px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Coordinates</th>
                  {isAdmin && (
                    <th className="text-right px-4 py-3 font-semibold text-[#6B6864] dark:text-[#9C9994] uppercase tracking-wider text-[10px]">Actions</th>
                  )}
                </tr>
              </thead>
              <tbody>
                {agencies.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE).map((agency) => (
                  <tr key={agency.id} className="border-b border-[#E7E3DC]/70 dark:border-[#26292C]/70 hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D] transition-colors">
                    <td><span className="identity-chip">{agency.code}</span></td>
                    <td className="px-4 py-3 text-sm font-medium text-[#0A0A0A] dark:text-white">
                      {agency.name}
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant="secondary" className="rounded-[2px] text-[10px] font-semibold">{agency.type}</Badge>
                    </td>
                    <td className="px-4 py-3">
                      <Badge
                        variant={agency.is_active ? 'success' : 'outline'}
                        className="rounded-[2px] text-[10px] font-semibold"
                      >
                        {agency.is_active ? 'ACTIVE' : 'INACTIVE'}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 text-xs text-[#5C5A56] dark:text-[#B9B6B0]">{agency.contact_number || 'N/A'}</td>
                    <td><span className="technical-chip">{agency.latitude?.toFixed(4)}, {agency.longitude?.toFixed(4)}</span></td>
                    {isAdmin && (
                      <td className="px-4 py-3 text-right whitespace-nowrap min-w-fit">
                        <div className="flex items-center justify-end gap-1.5">
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => openEditDialog(agency)}
                            disabled={togglingId === agency.id}
                            title="Edit agency"
                            aria-label={`Edit ${agency.name}`}
                            className="h-9 w-9 p-0 rounded-[2px] text-[#3B7DD1] hover:bg-[#3B7DD1]/10 hover:text-[#3B7DD1] dark:hover:bg-[#3B7DD1]/15 disabled:opacity-50"
                          >
                            <Pencil className="h-[17px] w-[17px]" />
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => toggleAgencyActive(agency)}
                            disabled={togglingId === agency.id}
                            title={agency.is_active ? 'Disable agency' : 'Enable agency'}
                            aria-label={`${agency.is_active ? 'Disable' : 'Enable'} ${agency.name}`}
                            className={`h-9 w-9 p-0 rounded-[2px] disabled:opacity-50 ${
                              agency.is_active
                                ? 'text-red-500 hover:bg-red-500/10 hover:text-red-600 dark:hover:bg-red-500/15'
                                : 'text-emerald-600 hover:bg-emerald-500/10 hover:text-emerald-600 dark:text-emerald-400 dark:hover:bg-emerald-400/15'
                            }`}
                          >
                            <Power className="h-[17px] w-[17px]" />
                          </Button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        {agencies.length > PAGE_SIZE && (
          <div className="flex items-center justify-between border-t border-[#E7E3DC] px-4 py-3 text-xs text-[#5C5A56] dark:border-[#26292C] dark:text-[#B9B6B0]">
            <span>Page {currentPage} of {Math.ceil(agencies.length / PAGE_SIZE)}</span>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" disabled={currentPage === 1} onClick={() => setCurrentPage((page) => Math.max(1, page - 1))}>Previous</Button>
              <Button variant="outline" size="sm" disabled={currentPage * PAGE_SIZE >= agencies.length} onClick={() => setCurrentPage((page) => page + 1)}>Next</Button>
            </div>
          </div>
        )}
      </div>

      {/* Register Agency Dialog */}
      {isAdmin && (
        <Dialog
          open={isCreateOpen}
          onOpenChange={(open) => {
            setIsCreateOpen(open);
            if (!open) {
              setEditingAgency(null);
              setFormData(blankAgencyForm);
              setSelectedProvinceId('');
              setSelectedMuniId('');
              setSelectedBrgyId('');
              setFormError(null);
            }
          }}
        >
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="max-w-2xl w-full mx-4 rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] shadow-lg p-6 max-h-[90vh] overflow-y-auto">
              <DialogHeader>
                <DialogTitle className="flex items-center gap-2 text-lg font-bold text-[#0A0A0A] dark:text-white">
                  <Building2 className="h-5 w-5 text-[#1F91A8]" />
                  {editingAgency ? 'Edit Emergency Agency' : 'Register New Emergency Agency'}
                </DialogTitle>
                <DialogDescription className="text-sm text-[#5C5A56] dark:text-[#B9B6B0]">
                  {editingAgency ? 'Update agency routing, contact, status, and location details' : 'Add a disaster response center or law enforcement agency to the mesh network'}
                </DialogDescription>
              </DialogHeader>

              <form onSubmit={handleSubmit} className="space-y-4 py-2 text-sm">
                {formError && (
                  <div className="rounded-[2px] border border-red-300 bg-red-50 dark:bg-red-900/20 px-3 py-2 text-xs text-red-700 dark:text-red-300">
                    {formError}
                  </div>
                )}

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Agency Code *</label>
                    <Input
                      required
                      placeholder="e.g. LGU-LEG-DRRMO-01"
                      value={formData.code}
                      onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    />
                  </div>
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Agency Type *</label>
                    <Select
                      value={formData.type}
                      onChange={(e) => setFormData({ ...formData, type: e.target.value as AgencyType })}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    >
                      <option value="DRRMO">DRRMO</option>
                      <option value="RESCUE">RESCUE</option>
                      <option value="POLICE">POLICE</option>
                      <option value="FIRE">FIRE</option>
                      <option value="MEDICAL">MEDICAL</option>
                      <option value="VOLUNTEER">VOLUNTEER</option>
                    </Select>
                  </div>
                </div>

                <div>
                  <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Full Agency Name *</label>
                  <Input
                    required
                    placeholder="e.g. Legazpi City Disaster Risk Reduction & Management Office"
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-[11px] text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Province *</label>
                    <Select
                      required
                      value={selectedProvinceId}
                      onChange={(e) => {
                        const selected = provinces.find((p) => String(p.id) === e.target.value);
                        setSelectedProvinceId(e.target.value);
                        setFormData({
                          ...formData,
                          province_code: selected ? selected.name : '',
                          muni_code: '',
                          barangay_code: '',
                        });
                      }}
                      disabled={geoLoading || provinces.length === 0}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    >
                      <option value="">
                        {geoLoading ? 'Loading provinces...' : 'Select province'}
                      </option>
                      {provinces.map((p) => (
                        <option key={p.id} value={String(p.id)}>
                          {p.name}
                        </option>
                      ))}
                    </Select>
                  </div>
                  <div>
                    <label className="text-[11px] text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">City / Municipality *</label>
                    <Select
                      required
                      value={selectedMuniId}
                      onChange={(e) => {
                        const selected = municipalities.find((m) => String(m.id) === e.target.value);
                        setSelectedMuniId(e.target.value);
                        setFormData({
                          ...formData,
                          muni_code: selected ? selected.name : '',
                          barangay_code: '',
                        });
                      }}
                      disabled={!selectedProvinceId || geoLoading || municipalities.length === 0}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    >
                      <option value="">
                        {geoLoading ? 'Loading municipalities...' : 'Select municipality'}
                      </option>
                      {municipalities.map((m) => (
                        <option key={m.id} value={String(m.id)}>
                          {m.name}
                        </option>
                      ))}
                    </Select>
                  </div>
                </div>

                <div>
                  <label className="text-[11px] text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Barangay *</label>
                  <Select
                    required
                    value={selectedBrgyId}
                    onChange={(e) => {
                      const selected = barangays.find((b) => String(b.id) === e.target.value);
                      setSelectedBrgyId(e.target.value);
                      setFormData({
                        ...formData,
                        barangay_code: selected ? selected.name : '',
                      });
                    }}
                    disabled={!selectedMuniId || geoLoading || barangays.length === 0}
                    className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                  >
                    <option value="">
                      {geoLoading ? 'Loading barangays...' : 'Select barangay'}
                    </option>
                    {barangays.map((b) => (
                      <option key={b.id} value={String(b.id)}>
                        {b.name}
                      </option>
                    ))}
                  </Select>
                </div>

                <div>
                  <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Region Code *</label>
                  <Input
                    required
                    placeholder="e.g. 05"
                    value={formData.region_code}
                    onChange={(e) => setFormData({ ...formData, region_code: e.target.value })}
                    className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                  />
                </div>

                <div>
                  <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Contact Number</label>
                  <Input
                    placeholder="+63 917 123 4567"
                    value={formData.contact_number}
                    onChange={(e) => setFormData({ ...formData, contact_number: e.target.value })}
                    className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Latitude *</label>
                    <Input
                      required
                      type="number"
                      step="any"
                      min={LAT_RANGE[0]}
                      max={LAT_RANGE[1]}
                      value={Number.isNaN(formData.latitude) ? '' : formData.latitude}
                      onChange={(e) => setFormData({ ...formData, latitude: e.target.value === '' ? NaN : parseFloat(e.target.value) })}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    />
                  </div>
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Longitude *</label>
                    <Input
                      required
                      type="number"
                      step="any"
                      min={LON_RANGE[0]}
                      max={LON_RANGE[1]}
                      value={Number.isNaN(formData.longitude) ? '' : formData.longitude}
                      onChange={(e) => setFormData({ ...formData, longitude: e.target.value === '' ? NaN : parseFloat(e.target.value) })}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Operating Hours</label>
                    <Input
                      placeholder="24/7"
                      value={formData.operating_hours || ''}
                      onChange={(e) => setFormData({ ...formData, operating_hours: e.target.value })}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    />
                  </div>
                  <div>
                    <label className="text-xs text-[#5C5A56] dark:text-[#B9B6B0] mb-1 block">Operational Status</label>
                    <Select
                      value={formData.is_active === false ? 'inactive' : 'active'}
                      onChange={(e) => setFormData({ ...formData, is_active: e.target.value === 'active' })}
                      className="h-9 rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                    >
                      <option value="active">ACTIVE</option>
                      <option value="inactive">INACTIVE</option>
                    </Select>
                  </div>
                </div>

                <DialogFooter className="mt-6 flex justify-end gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setIsCreateOpen(false)}
                    className="rounded-[2px] border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#0F1214] text-[#6B6864] dark:text-[#9C9994] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]"
                  >
                    Cancel
                  </Button>
                  <Button
                    type="submit"
                    disabled={saving}
                    className="rounded-[2px] bg-[#D15033] hover:bg-[#B5432A] text-white"
                  >
                    {saving ? 'Saving...' : editingAgency ? 'Save Changes' : 'Save Agency'}
                  </Button>
                </DialogFooter>
              </form>
            </div>
          </div>
        </Dialog>
      )}
    </div>
  );
};
