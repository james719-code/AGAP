import React, { useState } from 'react';
import { useAgencies } from '@/hooks/useAgencies';
import type { Agency, AgencyType } from '@/types/api';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Table, TableHeader, TableBody, TableHead, TableRow, TableCell } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { Plus, Trash2, Building2 } from 'lucide-react';

export const AgenciesPage: React.FC = () => {
  const { agencies, loading, error, createAgency, deleteAgency } = useAgencies();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [formData, setFormData] = useState<Partial<Agency>>({
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
  });
  const [saving, setSaving] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await createAgency(formData);
      setIsCreateOpen(false);
      setFormData({
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
      });
    } catch (err) {
      console.error(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-100">Emergency Agency Directory</h2>
          <p className="text-sm text-slate-400">Register and manage disaster response centers & law enforcement agencies</p>
        </div>

        <Button onClick={() => setIsCreateOpen(true)} className="bg-emerald-600 hover:bg-emerald-700 flex items-center gap-2 font-semibold">
          <Plus className="h-4 w-4" /> Register Agency
        </Button>
      </div>

      {/* Agencies Table */}
      <Card className="border-slate-800 bg-slate-900/90">
        <CardHeader className="py-4 border-b border-slate-800">
          <CardTitle className="text-sm font-semibold text-slate-300">
            Registered Agencies ({agencies.length})
          </CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {loading ? (
            <div className="p-8 text-center text-slate-500 text-sm">Loading agencies...</div>
          ) : error ? (
            <div className="p-8 text-center text-red-400 text-sm">{error}</div>
          ) : agencies.length === 0 ? (
            <div className="p-8 text-center text-slate-500 text-sm">No agencies registered yet. Click above to add one.</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead>
                  <TableHead>Agency Name</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Location Codes</TableHead>
                  <TableHead>Contact</TableHead>
                  <TableHead>Coordinates</TableHead>
                  <TableHead className="text-right">Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {agencies.map((agency) => (
                  <TableRow key={agency.id}>
                    <TableCell className="font-mono text-xs text-emerald-400 font-bold">{agency.code}</TableCell>
                    <TableCell className="font-semibold text-slate-200">{agency.name}</TableCell>
                    <TableCell>
                      <Badge variant="outline">{agency.type}</Badge>
                    </TableCell>
                    <TableCell className="font-mono text-xs text-slate-400">
                      R:{agency.region_code} P:{agency.province_code} M:{agency.muni_code} B:{agency.barangay_code}
                    </TableCell>
                    <TableCell className="text-xs text-slate-300">{agency.contact_number || 'N/A'}</TableCell>
                    <TableCell className="font-mono text-xs text-slate-400">
                      {agency.latitude?.toFixed(4)}, {agency.longitude?.toFixed(4)}
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => deleteAgency(agency.id)}
                        className="h-8 w-8 p-0 text-red-400 hover:text-red-300 hover:bg-red-950/50"
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Create Agency Dialog Form */}
      {isCreateOpen && (
        <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Building2 className="h-5 w-5 text-emerald-400" />
              Register New Dispatch Agency
            </DialogTitle>
            <DialogDescription>Add a new emergency response agency to the AGAP spatial registry</DialogDescription>
          </DialogHeader>

          <form onSubmit={handleSubmit} className="space-y-4 py-2 text-sm">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-slate-400 mb-1 block">Agency Code *</label>
                <Input
                  required
                  placeholder="e.g. DRRMO-LEGAZPI"
                  value={formData.code}
                  onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                />
              </div>

              <div>
                <label className="text-xs text-slate-400 mb-1 block">Agency Type *</label>
                <Select
                  value={formData.type}
                  onChange={(e) => setFormData({ ...formData, type: e.target.value as AgencyType })}
                >
                  <option value="DRRMO">DRRMO</option>
                  <option value="POLICE">POLICE</option>
                  <option value="FIRE">FIRE</option>
                  <option value="MEDICAL">MEDICAL</option>
                  <option value="VOLUNTEER">VOLUNTEER</option>
                </Select>
              </div>
            </div>

            <div>
              <label className="text-xs text-slate-400 mb-1 block">Full Agency Name *</label>
              <Input
                required
                placeholder="e.g. Legazpi City Disaster Risk Reduction & Management Office"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              />
            </div>

            <div className="grid grid-cols-4 gap-2">
              <div>
                <label className="text-[11px] text-slate-400 mb-1 block">Region Code</label>
                <Input
                  value={formData.region_code}
                  onChange={(e) => setFormData({ ...formData, region_code: e.target.value })}
                />
              </div>
              <div>
                <label className="text-[11px] text-slate-400 mb-1 block">Province Code</label>
                <Input
                  value={formData.province_code}
                  onChange={(e) => setFormData({ ...formData, province_code: e.target.value })}
                />
              </div>
              <div>
                <label className="text-[11px] text-slate-400 mb-1 block">Muni Code</label>
                <Input
                  value={formData.muni_code}
                  onChange={(e) => setFormData({ ...formData, muni_code: e.target.value })}
                />
              </div>
              <div>
                <label className="text-[11px] text-slate-400 mb-1 block">Brgy Code</label>
                <Input
                  value={formData.barangay_code}
                  onChange={(e) => setFormData({ ...formData, barangay_code: e.target.value })}
                />
              </div>
            </div>

            <div>
              <label className="text-xs text-slate-400 mb-1 block">Contact Number</label>
              <Input
                placeholder="+63 917 123 4567"
                value={formData.contact_number}
                onChange={(e) => setFormData({ ...formData, contact_number: e.target.value })}
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-slate-400 mb-1 block">Latitude</label>
                <Input
                  type="number"
                  step="any"
                  value={formData.latitude}
                  onChange={(e) => setFormData({ ...formData, latitude: parseFloat(e.target.value) })}
                />
              </div>
              <div>
                <label className="text-xs text-slate-400 mb-1 block">Longitude</label>
                <Input
                  type="number"
                  step="any"
                  value={formData.longitude}
                  onChange={(e) => setFormData({ ...formData, longitude: parseFloat(e.target.value) })}
                />
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setIsCreateOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={saving} className="bg-emerald-600 hover:bg-emerald-700">
                {saving ? 'Saving...' : 'Save Agency'}
              </Button>
            </DialogFooter>
          </form>
        </Dialog>
      )}
    </div>
  );
};
