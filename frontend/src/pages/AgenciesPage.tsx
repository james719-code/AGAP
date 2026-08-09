import React, { useState, useEffect } from 'react';
import { useAgencies } from '@/hooks/useAgencies';
import { useAuth } from '@/context/AuthContext';
import type { Agency, AgencyType, Province, Municipality, Barangay } from '@/types/api';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Table, TableHeader, TableBody, TableHead, TableRow, TableCell } from '@/components/ui/table';
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

// Naga City / Bicol region is roughly 12-14N, 122-125E — loose sanity bounds,
// not a strict geofence, just enough to catch fat-finger entry.
const LAT_RANGE: [number, number] = [-90, 90];
const LON_RANGE: [number, number] = [-180, 180];

export const AgenciesPage: React.FC = () => {
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

  const [provinces, setProvinces] = useState<Province[]>([]);
  const [municipalities, setMunicipalities] = useState<Municipality[]>([]);
  const [barangays, setBarangays] = useState<Barangay[]>([]);
  const [geoLoading, setGeoLoading] = useState(false);

  // Separate selected geo IDs for dropdowns, independent from the text code
  // fields stored on the agency record for backward compatibility.
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
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-primary">Operations</p>
          <h2 className="mt-1 text-2xl font-extrabold tracking-tight text-foreground">Emergency Agency Directory</h2>
          <p className="mt-1 text-sm text-muted-foreground">Register and manage disaster response centers & law enforcement agencies</p>
        </div>

        {isAdmin && (
          <Button onClick={openCreateDialog} className="bg-primary hover:bg-primary/90 text-white flex items-center gap-2 font-semibold">
            <Plus className="h-4 w-4" /> Register Agency
          </Button>
        )}
      </div>

      {toggleError && (
        <div className="rounded-md border border-red-300 bg-red-50 px-4 py-2 text-sm text-red-700">{toggleError}</div>
      )}

      {/* Agencies Table */}
      <Card className="border-border bg-card shadow-sm">
        <CardHeader className="py-4 border-b border-border">
          <CardTitle className="text-sm font-semibold text-card-foreground">
            Registered Agencies ({agencies.length})
          </CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {loading ? (
            <div className="p-8 text-center text-muted-foreground text-sm">Loading agencies...</div>
          ) : error ? (
            <div className="p-8 text-center text-red-500 text-sm">{error}</div>
          ) : agencies.length === 0 ? (
            <div className="p-8 text-center text-muted-foreground text-sm">No agencies registered yet. {isAdmin ? 'Click above to add one.' : ''}</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow className="border-b border-border bg-muted/60">
                  <TableHead>Code</TableHead>
                  <TableHead>Agency</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Contact</TableHead>
                  <TableHead>Coordinates</TableHead>
                  {isAdmin && <TableHead className="text-right">Actions</TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {agencies.map((agency) => (
                  <TableRow key={agency.id} className="border-b border-border/70 hover:bg-muted/60">
                    <TableCell className="font-mono text-xs text-muted-foreground font-bold">{agency.code}</TableCell>
                    <TableCell className="text-sm font-medium text-foreground">
                      <span className="flex items-center gap-2">
                        <Building2 className="h-4 w-4 text-[#1F91A8]" />
                        {agency.name}
                      </span>
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary" className="text-[10px] font-semibold">{agency.type}</Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={agency.is_active ? 'success' : 'outline'} className="text-[10px] font-semibold">
                        {agency.is_active ? 'ACTIVE' : 'INACTIVE'}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">{agency.contact_number || 'N/A'}</TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {agency.latitude?.toFixed(4)}, {agency.longitude?.toFixed(4)}
                    </TableCell>
                    {isAdmin && (
                      <TableCell className="text-right space-x-1">
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => openEditDialog(agency)}
                          disabled={togglingId === agency.id}
                          className="h-8 w-8 p-0 text-muted-foreground hover:bg-muted hover:text-foreground"
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => toggleAgencyActive(agency)}
                          disabled={togglingId === agency.id}
                          className="h-8 w-8 p-0 text-muted-foreground hover:bg-muted hover:text-foreground disabled:opacity-50"
                        >
                          <Power className="h-4 w-4" />
                        </Button>
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

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
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Building2 className="h-5 w-5 text-[#1F91A8]" />
              {editingAgency ? 'Edit Emergency Agency' : 'Register New Emergency Agency'}
            </DialogTitle>
            <DialogDescription>
              {editingAgency ? 'Update agency routing, contact, status, and location details' : 'Add a disaster response center or law enforcement agency to the mesh network'}
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleSubmit} className="space-y-4 py-2 text-sm">
            {formError && (
              <div className="rounded-md border border-red-300 bg-red-50 px-3 py-2 text-xs text-red-700">{formError}</div>
            )}

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Agency Code *</label>
                <Input
                  required
                  placeholder="e.g. LGU-LEG-DRRMO-01"
                  value={formData.code}
                  onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                />
              </div>
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Agency Type *</label>
                <Select
                  value={formData.type}
                  onChange={(e) => setFormData({ ...formData, type: e.target.value as AgencyType })}
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
              <label className="text-xs text-muted-foreground mb-1 block">Full Agency Name *</label>
              <Input
                required
                placeholder="e.g. Legazpi City Disaster Risk Reduction & Management Office"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-[11px] text-muted-foreground mb-1 block">Province *</label>
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
                <label className="text-[11px] text-muted-foreground mb-1 block">City / Municipality *</label>
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
              <label className="text-[11px] text-muted-foreground mb-1 block">Barangay *</label>
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
              <label className="text-xs text-muted-foreground mb-1 block">Region Code *</label>
              <Input
                required
                placeholder="e.g. 05"
                value={formData.region_code}
                onChange={(e) => setFormData({ ...formData, region_code: e.target.value })}
              />
            </div>

            <div>
              <label className="text-xs text-muted-foreground mb-1 block">Contact Number</label>
              <Input
                placeholder="+63 917 123 4567"
                value={formData.contact_number}
                onChange={(e) => setFormData({ ...formData, contact_number: e.target.value })}
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Latitude *</label>
                <Input
                  required
                  type="number"
                  step="any"
                  min={LAT_RANGE[0]}
                  max={LAT_RANGE[1]}
                  value={Number.isNaN(formData.latitude) ? '' : formData.latitude}
                  onChange={(e) => setFormData({ ...formData, latitude: e.target.value === '' ? NaN : parseFloat(e.target.value) })}
                />
              </div>
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Longitude *</label>
                <Input
                  required
                  type="number"
                  step="any"
                  min={LON_RANGE[0]}
                  max={LON_RANGE[1]}
                  value={Number.isNaN(formData.longitude) ? '' : formData.longitude}
                  onChange={(e) => setFormData({ ...formData, longitude: e.target.value === '' ? NaN : parseFloat(e.target.value) })}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Operating Hours</label>
                <Input
                  placeholder="24/7"
                  value={formData.operating_hours || ''}
                  onChange={(e) => setFormData({ ...formData, operating_hours: e.target.value })}
                />
              </div>
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Operational Status</label>
                <Select
                  value={formData.is_active === false ? 'inactive' : 'active'}
                  onChange={(e) => setFormData({ ...formData, is_active: e.target.value === 'active' })}
                >
                  <option value="active">ACTIVE</option>
                  <option value="inactive">INACTIVE</option>
                </Select>
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setIsCreateOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={saving} className="bg-primary hover:bg-primary/90">
                {saving ? 'Saving...' : editingAgency ? 'Save Changes' : 'Save Agency'}
              </Button>
            </DialogFooter>
          </form>
        </Dialog>
      )}
    </div>
  );
};