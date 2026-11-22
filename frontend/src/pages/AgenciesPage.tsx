import React, { useState, useEffect } from 'react';
import { useAgencies } from '@/hooks/useAgencies';
import { useAuth } from '@/context/AuthContext';
import type { Agency, AgencyType, Province, Municipality, Barangay } from '@/types/api';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { DataTableViewport } from '@/components/common/DataTableViewport';
import { RegistryPagination } from '@/components/common/RegistryPagination';
import { RegistrySearch } from '@/components/common/RegistrySearch';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { Building2, Pencil, Plus, Power, RefreshCw } from 'lucide-react';
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

  const { agencies, loading, error, refetch, createAgency, updateAgency } = useAgencies();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
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

  const query = search.trim().toLowerCase();
  const filteredRecords = agencies.filter((agency) =>
    (statusFilter === 'all' || agency.is_active === (statusFilter === 'active')) &&
    [agency.name, agency.code, agency.type, agency.contact_number].some((value) => value?.toLowerCase().includes(query))
  );
  const page = Math.min(currentPage, Math.max(1, Math.ceil(filteredRecords.length / PAGE_SIZE)));

  return (
    <div className="page-stack">
      {/* Page Header */}
      <div className="page-header">
        <div>
          <span className="page-eyebrow">Administration</span>
          <h1 className="page-title">Agencies</h1>
          <p className="page-description">
            Manage disaster response and law enforcement agencies.
          </p>
        </div>

        <div className="page-actions">
        <Button type="button" variant="outline" disabled={loading} onClick={() => void refetch()}><RefreshCw className={`size-4 ${loading ? 'motion-safe:animate-spin' : ''}`} /> Refresh</Button>
        {isAdmin && (
          <Button
            onClick={openCreateDialog}
            className="bg-primary hover:bg-primary/90 text-primary-foreground flex items-center gap-2 font-semibold rounded-md shrink-0"
          >
            <Plus className="h-4 w-4" /> Add Agency
          </Button>
        )}
        </div>
      </div>

      {toggleError && (
        <div className="rounded-md border border-red-300 bg-red-50 dark:bg-red-900/20 px-4 py-2 text-sm text-red-700 dark:text-red-300">
          {toggleError}
        </div>
      )}

      {/* Agencies Table */}
      <div className="surface-panel">
        <div className="surface-header flex-wrap">
          <div>
            <h2 className="font-bold">Agency directory</h2>
            <p className="text-xs text-muted-foreground tabular-nums">{loading ? 'Loading…' : `${filteredRecords.length} agencies`}</p>
          </div>
          <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto">
            <RegistrySearch label="Search agencies" placeholder="Search name, code, or contact" value={search} onChange={(value) => { setSearch(value); setCurrentPage(1); }} />
            <Select aria-label="Agency status" value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); setCurrentPage(1); }} className="w-full sm:w-40"><option value="all">All statuses</option><option value="active">Active</option><option value="inactive">Inactive</option></Select>
          </div>
        </div>

        <DataTableViewport label="Agencies table">
          {loading ? (
            <div className="p-8 text-center text-muted-foreground text-sm">Loading agencies...</div>
          ) : error ? (
            <div className="state-message state-message-error" role="alert">
              <p>{error}</p>
              <Button type="button" variant="link" onClick={refetch}>Retry</Button>
            </div>
          ) : filteredRecords.length === 0 ? (
            <div className="state-message">
              <p>{search.trim() || statusFilter !== 'all' ? 'No agencies match these filters.' : 'No agencies registered yet.'}</p>
              {search.trim() || statusFilter !== 'all' ? (
                <Button type="button" variant="link" onClick={() => { setSearch(''); setStatusFilter('all'); setCurrentPage(1); }}>Clear filters</Button>
              ) : isAdmin && (
                <Button type="button" variant="link" onClick={openCreateDialog}>Add agency</Button>
              )}
            </div>
          ) : (
            <table className="data-table">
              <thead className="bg-muted border-b border-border">
                <tr>
                  <th scope="col" className="text-left">Code</th>
                  <th scope="col" className="text-left">Agency</th>
                  <th scope="col" className="text-left">Type</th>
                  <th scope="col" className="text-left">Status</th>
                  <th scope="col" className="text-left">Contact</th>
                  <th scope="col" className="text-left">Coordinates</th>
                  {isAdmin && (
                    <th scope="col" className="text-right">Actions</th>
                  )}
                </tr>
              </thead>
              <tbody>
                {filteredRecords.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE).map((agency) => (
                  <tr key={agency.id} className="border-b border-[#E7E3DC]/70 dark:border-[#26292C]/70 hover:bg-accent transition-colors">
                    <td><span className="identity-chip">{agency.code}</span></td>
                    <td className="px-4 py-3 text-sm font-medium text-foreground">
                      {agency.name}
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant="secondary" className="rounded-md text-[10px] font-semibold">{agency.type}</Badge>
                    </td>
                    <td className="px-4 py-3">
                      <Badge
                        variant={agency.is_active ? 'success' : 'outline'}
                        className="rounded-md text-[10px] font-semibold"
                      >
                        {agency.is_active ? 'ACTIVE' : 'INACTIVE'}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">{agency.contact_number || 'N/A'}</td>
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
                            className="record-action disabled:opacity-50"
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
                            className={`h-9 w-9 p-0 rounded-md disabled:opacity-50 ${
                              agency.is_active
                                ? 'record-action record-action-danger'
                                : 'record-action record-action-success'
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
        </DataTableViewport>
        {!loading && !error && <RegistryPagination total={filteredRecords.length} page={page} pageSize={PAGE_SIZE} onPageChange={setCurrentPage} />}
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
          contentClassName="max-w-2xl"
        >
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-lg font-bold text-foreground">
              <Building2 className="h-5 w-5 text-[#1F91A8]" />
              {editingAgency ? 'Edit Emergency Agency' : 'Register New Emergency Agency'}
            </DialogTitle>
            <DialogDescription className="text-sm text-muted-foreground">
              {editingAgency ? 'Update agency routing, contact, status, and location details' : 'Add a disaster response center or law enforcement agency to the mesh network'}
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleSubmit} className="space-y-4 py-2 text-sm">
            {formError && (
              <div className="rounded-md border border-red-300 bg-red-50 dark:bg-red-900/20 px-3 py-2 text-xs text-red-700 dark:text-red-300">
                {formError}
              </div>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label htmlFor="agenciespage-agency-code" className="text-xs text-muted-foreground mb-1 block">Agency Code *</label>
                <Input id="agenciespage-agency-code"
                  required
                  placeholder="e.g. LGU-LEG-DRRMO-01"
                  value={formData.code}
                  onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                  className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                />
              </div>
              <div>
                <label htmlFor="agenciespage-agency-type" className="text-xs text-muted-foreground mb-1 block">Agency Type *</label>
                <Select id="agenciespage-agency-type"
                  value={formData.type}
                  onChange={(e) => setFormData({ ...formData, type: e.target.value as AgencyType })}
                  className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
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
              <label htmlFor="agenciespage-full-agency-name" className="text-xs text-muted-foreground mb-1 block">Full Agency Name *</label>
              <Input id="agenciespage-full-agency-name"
                required
                placeholder="e.g. Legazpi City Disaster Risk Reduction & Management Office"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label htmlFor="agenciespage-province" className="text-[11px] text-muted-foreground mb-1 block">Province *</label>
                <Select id="agenciespage-province"
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
                  className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
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
                <label htmlFor="agenciespage-city-municipality" className="text-[11px] text-muted-foreground mb-1 block">City / Municipality *</label>
                <Select id="agenciespage-city-municipality"
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
                  className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
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
              <label htmlFor="agenciespage-barangay" className="text-[11px] text-muted-foreground mb-1 block">Barangay *</label>
              <Select id="agenciespage-barangay"
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
                className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
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
              <label htmlFor="agenciespage-region-code" className="text-xs text-muted-foreground mb-1 block">Region Code *</label>
              <Input id="agenciespage-region-code"
                required
                placeholder="e.g. 05"
                value={formData.region_code}
                onChange={(e) => setFormData({ ...formData, region_code: e.target.value })}
                className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
              />
            </div>

            <div>
              <label htmlFor="agenciespage-contact-number" className="text-xs text-muted-foreground mb-1 block">Contact Number</label>
              <Input id="agenciespage-contact-number"
                placeholder="+63 917 123 4567"
                value={formData.contact_number}
                onChange={(e) => setFormData({ ...formData, contact_number: e.target.value })}
                className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label htmlFor="agenciespage-latitude" className="text-xs text-muted-foreground mb-1 block">Latitude *</label>
                <Input id="agenciespage-latitude"
                  required
                  type="number"
                  step="any"
                  min={LAT_RANGE[0]}
                  max={LAT_RANGE[1]}
                  value={Number.isNaN(formData.latitude) ? '' : formData.latitude}
                  onChange={(e) => setFormData({ ...formData, latitude: e.target.value === '' ? NaN : parseFloat(e.target.value) })}
                  className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                />
              </div>
              <div>
                <label htmlFor="agenciespage-longitude" className="text-xs text-muted-foreground mb-1 block">Longitude *</label>
                <Input id="agenciespage-longitude"
                  required
                  type="number"
                  step="any"
                  min={LON_RANGE[0]}
                  max={LON_RANGE[1]}
                  value={Number.isNaN(formData.longitude) ? '' : formData.longitude}
                  onChange={(e) => setFormData({ ...formData, longitude: e.target.value === '' ? NaN : parseFloat(e.target.value) })}
                  className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label htmlFor="agenciespage-operating-hours" className="text-xs text-muted-foreground mb-1 block">Operating Hours</label>
                <Input id="agenciespage-operating-hours"
                  placeholder="24/7"
                  value={formData.operating_hours || ''}
                  onChange={(e) => setFormData({ ...formData, operating_hours: e.target.value })}
                  className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
                />
              </div>
              <div>
                <label htmlFor="agenciespage-operational-status" className="text-xs text-muted-foreground mb-1 block">Operational Status</label>
                <Select id="agenciespage-operational-status"
                  value={formData.is_active === false ? 'inactive' : 'active'}
                  onChange={(e) => setFormData({ ...formData, is_active: e.target.value === 'active' })}
                  className="h-9 rounded-md border-border bg-muted text-xs focus-visible:ring-1 focus-visible:ring-[#D15033]"
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
                className="rounded-md border-border bg-card text-muted-foreground hover:bg-accent"
              >
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={saving}
                className="rounded-md bg-primary hover:bg-primary/90 text-primary-foreground"
              >
                {saving ? 'Saving...' : editingAgency ? 'Save Changes' : 'Save Agency'}
              </Button>
            </DialogFooter>
          </form>
        </Dialog>
      )}
    </div>
  );
};
