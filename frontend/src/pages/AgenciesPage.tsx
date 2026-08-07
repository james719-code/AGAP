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
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-primary">Operations</p>
          <h2 className="mt-1 text-2xl font-extrabold tracking-tight text-foreground">Emergency Agency Directory</h2>
          <p className="mt-1 text-sm text-muted-foreground">Register and manage disaster response centers & law enforcement agencies</p>
        </div>

        <Button onClick={() => setIsCreateOpen(true)} className="bg-primary hover:bg-primary/90 text-white flex items-center gap-2 font-semibold">
          <Plus className="h-4 w-4" /> Register Agency
        </Button>
      </div>

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
            <div className="p-8 text-center text-muted-foreground text-sm">No agencies registered yet. Click above to add one.</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow className="border-b border-border bg-muted/60">
                  <TableHead>Code</TableHead>
                  <TableHead>Agency</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Contact</TableHead>
                  <TableHead>Coordinates</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
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
                    <TableCell className="text-xs text-muted-foreground">{agency.contact_number || 'N/A'}</TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {agency.latitude?.toFixed(4)}, {agency.longitude?.toFixed(4)}
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => deleteAgency(agency.id)}
                        className="h-8 w-8 p-0 text-red-500 hover:text-red-600 hover:bg-red-500/10"
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

      {/* Register Agency Dialog */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Building2 className="h-5 w-5 text-[#1F91A8]" />
            Register New Emergency Agency
          </DialogTitle>
          <DialogDescription>Add a disaster response center or law enforcement agency to the mesh network</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4 py-2 text-sm">
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
                <option value="POLICE">POLICE</option>
                <option value="FIRE">FIRE</option>
                <option value="MEDICAL">MEDICAL</option>
                <option value="COAST_GUARD">COAST GUARD</option>
                <option value="OTHER">OTHER</option>
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

          <div className="grid grid-cols-4 gap-2">
            <div>
              <label className="text-[11px] text-muted-foreground mb-1 block">Region Code</label>
              <Input
                value={formData.region_code}
                onChange={(e) => setFormData({ ...formData, region_code: e.target.value })}
              />
            </div>
            <div>
              <label className="text-[11px] text-muted-foreground mb-1 block">Province Code</label>
              <Input
                value={formData.province_code}
                onChange={(e) => setFormData({ ...formData, province_code: e.target.value })}
              />
            </div>
            <div>
              <label className="text-[11px] text-muted-foreground mb-1 block">Muni Code</label>
              <Input
                value={formData.muni_code}
                onChange={(e) => setFormData({ ...formData, muni_code: e.target.value })}
              />
            </div>
            <div>
              <label className="text-[11px] text-muted-foreground mb-1 block">Brgy Code</label>
              <Input
                value={formData.barangay_code}
                onChange={(e) => setFormData({ ...formData, barangay_code: e.target.value })}
              />
            </div>
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
              <label className="text-xs text-muted-foreground mb-1 block">Latitude</label>
              <Input
                type="number"
                step="any"
                value={formData.latitude}
                onChange={(e) => setFormData({ ...formData, latitude: parseFloat(e.target.value) })}
              />
            </div>
            <div>
              <label className="text-xs text-muted-foreground mb-1 block">Longitude</label>
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
            <Button type="submit" disabled={saving} className="bg-primary hover:bg-primary/90">
              {saving ? 'Saving...' : 'Save Agency'}
            </Button>
          </DialogFooter>
        </form>
      </Dialog>
    </div>
  );
};

