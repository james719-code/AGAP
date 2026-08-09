import React, { useEffect, useState, useCallback } from 'react';
import { apiService } from '@/services/api';
import { useAgencies } from '@/hooks/useAgencies';
import type { AccountStatus, UserAccount, UserRole } from '@/types/api';
import { Card, CardHeader, CardContent } from '@/components/ui/card';
import { Table, TableHeader, TableBody, TableHead, TableRow, TableCell } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  Users,
  UserCheck,
  Search,
  ShieldCheck,
  AlertCircle,
  RefreshCw,
  Edit,
  Trash2,
  CheckCircle2,
  ShieldAlert,
  Phone,
  Mail,
  Building2,
} from 'lucide-react';

export const UsersPage: React.FC = () => {
  const { agencies } = useAgencies();

  const [users, setUsers] = useState<UserAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [roleTab, setRoleTab] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Modals state
  const [statusModalUser, setStatusModalUser] = useState<UserAccount | null>(null);
  const [newStatus, setNewStatus] = useState<AccountStatus>('APPROVED');
  const [statusRemarks, setStatusRemarks] = useState('');
  const [statusSaving, setStatusSaving] = useState(false);
  const [statusError, setStatusError] = useState<string | null>(null);

  const [editModalUser, setEditModalUser] = useState<UserAccount | null>(null);
  const [editForm, setEditForm] = useState<Partial<UserAccount>>({});
  const [editSaving, setEditSaving] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  const [deleteModalUser, setDeleteModalUser] = useState<UserAccount | null>(null);
  const [deleteSaving, setDeleteSaving] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiService.getUsers();
      setUsers(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch users');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const showNotification = (msg: string) => {
    setActionSuccess(msg);
    setTimeout(() => setActionSuccess(null), 4000);
  };

  // 1-Click Fast Verification
  const handleQuickVerify = async (user: UserAccount) => {
    try {
      await apiService.verifyUser(user.id);
      showNotification(`Verified user account ${user.username} successfully.`);
      fetchUsers();
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to verify user');
    }
  };

  // Status Change Submit
  const handleStatusSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!statusModalUser) return;
    setStatusSaving(true);
    setStatusError(null);
    try {
      await apiService.updateUserStatus(statusModalUser.id, newStatus, statusRemarks);
      showNotification(`Account status for ${statusModalUser.username} set to ${newStatus}.`);
      setStatusModalUser(null);
      fetchUsers();
    } catch (err) {
      setStatusError(err instanceof Error ? err.message : 'Failed to update user status');
    } finally {
      setStatusSaving(false);
    }
  };

  // User Edit Submit
  const handleEditSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editModalUser) return;
    setEditSaving(true);
    setEditError(null);
    try {
      await apiService.updateUser(editModalUser.id, editForm);
      showNotification(`Profile for ${editModalUser.username} updated.`);
      setEditModalUser(null);
      fetchUsers();
    } catch (err) {
      setEditError(err instanceof Error ? err.message : 'Failed to update user');
    } finally {
      setEditSaving(false);
    }
  };

  // Delete User Submit
  const handleDeleteSubmit = async () => {
    if (!deleteModalUser) return;
    setDeleteSaving(true);
    setDeleteError(null);
    try {
      await apiService.deleteUser(deleteModalUser.id);
      showNotification(`User account ${deleteModalUser.username} deleted.`);
      setDeleteModalUser(null);
      fetchUsers();
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : 'Failed to delete user');
    } finally {
      setDeleteSaving(false);
    }
  };

  // Filter users client-side
  const filteredUsers = users.filter((u) => {
    if (roleTab !== 'ALL' && u.role !== roleTab) return false;
    if (statusFilter !== 'ALL' && u.account_status !== statusFilter) return false;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      const matchName = u.name?.toLowerCase().includes(q);
      const matchUsername = u.username?.toLowerCase().includes(q);
      const matchPhone = u.phone_number?.toLowerCase().includes(q);
      const matchEmail = u.email?.toLowerCase().includes(q);
      const matchId = String(u.id).includes(q);
      if (!matchName && !matchUsername && !matchPhone && !matchEmail && !matchId) return false;
    }
    return true;
  });

  const pendingVerificationUsers = users.filter((u) => u.account_status === 'PENDING');

  return (
    <div className="space-y-6">
      {/* Top Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-foreground flex items-center gap-2.5">
            <Users className="h-6 w-6 text-[#1F91A8]" /> User Roster & Account Administration
          </h1>
          <p className="text-xs text-muted-foreground mt-1">
            Manage registered Citizens, Responders, and Dispatch Admins. Verify pending accounts and update roles.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={fetchUsers} disabled={loading} className="gap-2 shrink-0">
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} /> Refresh Roster
        </Button>
      </div>

      {actionSuccess && (
        <div className="p-4 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-600 dark:text-emerald-400 text-xs font-semibold flex items-center gap-2 animate-in fade-in">
          <CheckCircle2 className="h-4 w-4 shrink-0" />
          {actionSuccess}
        </div>
      )}

      {/* Pending Verification Notice Banner */}
      {pendingVerificationUsers.length > 0 && (
        <div className="p-4 rounded-xl bg-amber-500/10 border border-amber-500/30 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-amber-500/20 text-amber-600 dark:text-amber-400 shrink-0">
              <UserCheck className="h-5 w-5" />
            </div>
            <div>
              <div className="text-sm font-bold text-amber-700 dark:text-amber-300">
                {pendingVerificationUsers.length} Account(s) Pending Verification
              </div>
              <div className="text-xs text-amber-600/90 dark:text-amber-400/90">
                Newly registered citizens and responders require administrative approval to access distress services.
              </div>
            </div>
          </div>
          <Button
            size="sm"
            className="bg-amber-600 hover:bg-amber-700 text-white shrink-0 text-xs font-semibold"
            onClick={() => {
              setRoleTab('ALL');
              setStatusFilter('PENDING');
            }}
          >
            Review Pending Users
          </Button>
        </div>
      )}

      {/* Main Roster Card */}
      <Card className="border border-border">
        <CardHeader className="pb-4 border-b border-border">
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
            {/* Role Filter Tabs */}
            <Tabs value={roleTab} onValueChange={setRoleTab} className="w-full lg:w-auto">
              <TabsList className="grid grid-cols-4 w-full sm:w-auto">
                <TabsTrigger value="ALL" className="text-xs">All Users ({users.length})</TabsTrigger>
                <TabsTrigger value="CITIZEN" className="text-xs">Citizens</TabsTrigger>
                <TabsTrigger value="RESPONDER" className="text-xs">Responders</TabsTrigger>
                <TabsTrigger value="ADMIN" className="text-xs">Admins</TabsTrigger>
              </TabsList>
            </Tabs>

            {/* Controls: Search and Status Filter */}
            <div className="flex flex-wrap items-center gap-3">
              <div className="relative flex-1 sm:flex-initial min-w-[200px]">
                <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                <Input
                  type="text"
                  placeholder="Search name, phone, ID..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-8 h-9 text-xs"
                />
              </div>

              <Select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="h-9 text-xs min-w-[140px]"
              >
                <option value="ALL">All Statuses</option>
                <option value="PENDING">Pending Only</option>
                <option value="APPROVED">Approved Only</option>
                <option value="REJECTED">Rejected Only</option>
                <option value="SUSPENDED">Suspended Only</option>
              </Select>
            </div>
          </div>
        </CardHeader>

        <CardContent className="p-0">
          {error && (
            <div className="p-6 text-center text-red-500 text-xs flex items-center justify-center gap-2">
              <AlertCircle className="h-4 w-4" /> {error}
            </div>
          )}

          {loading ? (
            <div className="p-12 text-center text-muted-foreground text-xs">Loading user roster...</div>
          ) : filteredUsers.length === 0 ? (
            <div className="p-12 text-center text-muted-foreground text-xs">
              No user accounts match the current filter criteria.
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow className="bg-muted/30 text-[11px] uppercase tracking-wider font-bold">
                  <TableHead className="w-12 text-center">ID</TableHead>
                  <TableHead>User Identity</TableHead>
                  <TableHead>Role</TableHead>
                  <TableHead>Status & Verification</TableHead>
                  <TableHead>Contact & National ID</TableHead>
                  <TableHead>Organization</TableHead>
                  <TableHead className="text-right pr-6">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className="text-xs">
                {filteredUsers.map((u) => {
                  const agencyObj = agencies.find((a) => a.id === u.agency_id);
                  return (
                    <TableRow key={u.id} className="hover:bg-muted/40 transition-colors">
                      <TableCell className="text-center font-mono text-muted-foreground">{u.id}</TableCell>
                      <TableCell>
                        <div className="font-bold text-foreground">{u.name || u.username}</div>
                        <div className="text-[11px] text-muted-foreground font-mono">@{u.username}</div>
                      </TableCell>
                      <TableCell>
                        {u.role === 'ADMIN' ? (
                          <Badge className="bg-purple-500/10 text-purple-600 dark:text-purple-400 border-purple-500/30">
                            ADMIN
                          </Badge>
                        ) : u.role === 'RESPONDER' ? (
                          <Badge className="bg-[#1F91A8]/10 text-[#1F91A8] dark:text-[#4FB4C9] border-[#1F91A8]/30">
                            RESPONDER
                          </Badge>
                        ) : (
                          <Badge variant="outline" className="text-slate-600 dark:text-slate-400">
                            CITIZEN
                          </Badge>
                        )}
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-col gap-1 items-start">
                          {u.account_status === 'APPROVED' ? (
                            <Badge className="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/30 text-[10px]">
                              APPROVED
                            </Badge>
                          ) : u.account_status === 'PENDING' ? (
                            <Badge className="bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/30 text-[10px]">
                              PENDING
                            </Badge>
                          ) : u.account_status === 'SUSPENDED' ? (
                            <Badge className="bg-red-500/10 text-red-600 dark:text-red-400 border-red-500/30 text-[10px]">
                              SUSPENDED
                            </Badge>
                          ) : (
                            <Badge variant="destructive" className="text-[10px]">
                              {u.account_status}
                            </Badge>
                          )}

                          {u.is_verified ? (
                            <span className="text-[10px] text-emerald-600 dark:text-emerald-400 font-semibold flex items-center gap-1">
                              <ShieldCheck className="h-3 w-3" /> Verified
                            </span>
                          ) : (
                            <span className="text-[10px] text-amber-600 dark:text-amber-400 font-semibold flex items-center gap-1">
                              <AlertCircle className="h-3 w-3" /> Unverified
                            </span>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="space-y-0.5">
                          {u.phone_number && (
                            <div className="text-foreground flex items-center gap-1.5">
                              <Phone className="h-3 w-3 text-muted-foreground" /> {u.phone_number}
                            </div>
                          )}
                          {u.email && (
                            <div className="text-muted-foreground flex items-center gap-1.5">
                              <Mail className="h-3 w-3 text-muted-foreground" /> {u.email}
                            </div>
                          )}
                          {u.national_id && (
                            <div className="text-[10px] font-mono text-muted-foreground">
                              Nat ID: {u.national_id}
                            </div>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        {agencyObj ? (
                          <div className="text-foreground font-medium flex items-center gap-1.5">
                            <Building2 className="h-3.5 w-3.5 text-[#1F91A8]" /> {agencyObj.name}
                          </div>
                        ) : (
                          <span className="text-muted-foreground text-[11px]">—</span>
                        )}
                      </TableCell>
                      <TableCell className="text-right pr-6">
                        <div className="flex items-center justify-end gap-1.5">
                          {u.account_status === 'PENDING' && (
                            <Button
                              size="sm"
                              variant="outline"
                              className="h-8 px-2 bg-emerald-500/10 text-emerald-600 border-emerald-500/30 hover:bg-emerald-500/20 text-[11px] font-semibold"
                              onClick={() => handleQuickVerify(u)}
                            >
                              <ShieldCheck className="h-3.5 w-3.5 mr-1" /> Verify
                            </Button>
                          )}
                          <Button
                            size="sm"
                            variant="ghost"
                            className="h-8 px-2 text-muted-foreground hover:text-foreground text-[11px]"
                            onClick={() => {
                              setStatusModalUser(u);
                              setNewStatus(u.account_status);
                              setStatusRemarks('');
                            }}
                          >
                            Status
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            className="h-8 w-8 p-0 text-muted-foreground hover:text-foreground"
                            onClick={() => {
                              setEditModalUser(u);
                              setEditForm({
                                name: u.name,
                                email: u.email,
                                phone_number: u.phone_number,
                                role: u.role,
                                national_id: u.national_id,
                                agency_id: u.agency_id,
                              });
                            }}
                          >
                            <Edit className="h-3.5 w-3.5" />
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            className="h-8 w-8 p-0 text-red-500 hover:text-red-600 hover:bg-red-500/10"
                            onClick={() => setDeleteModalUser(u)}
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Account Status Modal */}
      {statusModalUser && (
        <Dialog open={!!statusModalUser} onOpenChange={() => setStatusModalUser(null)}>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <ShieldAlert className="h-5 w-5 text-[#F2A540]" /> Update Account Status — @{statusModalUser.username}
            </DialogTitle>
            <DialogDescription className="text-xs">
              Approve, reject, or suspend access for user {statusModalUser.name || statusModalUser.username}.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleStatusSubmit} className="space-y-4 py-3 text-xs">
            {statusError && <div className="text-red-500 text-xs font-semibold">{statusError}</div>}

            <div className="space-y-1.5">
              <span className="font-semibold text-foreground">Target Account Status</span>
              <Select
                value={newStatus}
                onChange={(e) => setNewStatus(e.target.value as AccountStatus)}
                className="h-9 text-xs"
              >
                <option value="APPROVED">APPROVED — Full System Access</option>
                <option value="REJECTED">REJECTED — Denied Access</option>
                <option value="SUSPENDED">SUSPENDED — Blocked from Logging In</option>
              </Select>
            </div>

            <div className="space-y-1.5">
              <span className="font-semibold text-foreground">Status Reason / Reviewer Remarks</span>
              <Input
                type="text"
                placeholder="Optional notes or rationale..."
                value={statusRemarks}
                onChange={(e) => setStatusRemarks(e.target.value)}
                className="h-9 text-xs"
              />
            </div>

            <DialogFooter className="pt-3 border-t border-border">
              <Button type="button" variant="outline" size="sm" onClick={() => setStatusModalUser(null)}>
                Cancel
              </Button>
              <Button type="submit" size="sm" disabled={statusSaving} className="bg-[#1F91A8] hover:bg-[#187588]">
                {statusSaving ? 'Updating...' : 'Save Status'}
              </Button>
            </DialogFooter>
          </form>
        </Dialog>
      )}

      {/* Edit User Modal */}
      {editModalUser && (
        <Dialog open={!!editModalUser} onOpenChange={() => setEditModalUser(null)}>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Edit className="h-5 w-5 text-[#1F91A8]" /> Edit User Profile — @{editModalUser.username}
            </DialogTitle>
            <DialogDescription className="text-xs">
              Update identity, role level, and agency association.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleEditSubmit} className="space-y-3 py-2 text-xs">
            {editError && <div className="text-red-500 text-xs font-semibold">{editError}</div>}

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <span className="font-semibold text-foreground">Full Name</span>
                <Input
                  type="text"
                  value={editForm.name || ''}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, name: e.target.value }))}
                  className="h-9 text-xs"
                />
              </div>
              <div className="space-y-1">
                <span className="font-semibold text-foreground">User Role</span>
                <Select
                  value={editForm.role || 'CITIZEN'}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, role: e.target.value as UserRole }))}
                  className="h-9 text-xs"
                >
                  <option value="CITIZEN">CITIZEN</option>
                  <option value="RESPONDER">RESPONDER</option>
                  <option value="ADMIN">ADMIN</option>
                </Select>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <span className="font-semibold text-foreground">Phone Number</span>
                <Input
                  type="text"
                  value={editForm.phone_number || ''}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, phone_number: e.target.value }))}
                  className="h-9 text-xs"
                />
              </div>
              <div className="space-y-1">
                <span className="font-semibold text-foreground">Email Address</span>
                <Input
                  type="email"
                  value={editForm.email || ''}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, email: e.target.value }))}
                  className="h-9 text-xs"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <span className="font-semibold text-foreground">National ID</span>
                <Input
                  type="text"
                  value={editForm.national_id || ''}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, national_id: e.target.value }))}
                  className="h-9 text-xs"
                />
              </div>
              <div className="space-y-1">
                <span className="font-semibold text-foreground">Assigned Agency</span>
                <Select
                  value={editForm.agency_id ? String(editForm.agency_id) : ''}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, agency_id: e.target.value ? Number(e.target.value) : 0 }))}
                  className="h-9 text-xs"
                >
                  <option value="">No Agency Assigned</option>
                  {agencies.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.name} ({a.code})
                    </option>
                  ))}
                </Select>
              </div>
            </div>

            <DialogFooter className="pt-3 border-t border-border">
              <Button type="button" variant="outline" size="sm" onClick={() => setEditModalUser(null)}>
                Cancel
              </Button>
              <Button type="submit" size="sm" disabled={editSaving} className="bg-[#1F91A8] hover:bg-[#187588]">
                {editSaving ? 'Saving...' : 'Save Changes'}
              </Button>
            </DialogFooter>
          </form>
        </Dialog>
      )}

      {/* Delete User Safety Modal */}
      {deleteModalUser && (
        <Dialog open={!!deleteModalUser} onOpenChange={() => setDeleteModalUser(null)}>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-red-600 dark:text-red-400">
              <Trash2 className="h-5 w-5" /> Confirm Account Deletion
            </DialogTitle>
            <DialogDescription className="text-xs">
              Are you sure you want to permanently delete account <strong>@{deleteModalUser.username}</strong> ({deleteModalUser.name})? This action cannot be undone.
            </DialogDescription>
          </DialogHeader>

          {deleteError && <div className="text-red-500 text-xs font-semibold py-2">{deleteError}</div>}

          <DialogFooter className="pt-3 border-t border-border">
            <Button type="button" variant="outline" size="sm" onClick={() => setDeleteModalUser(null)}>
              Cancel
            </Button>
            <Button
              type="button"
              variant="destructive"
              size="sm"
              disabled={deleteSaving}
              onClick={handleDeleteSubmit}
            >
              {deleteSaving ? 'Deleting...' : 'Delete Account'}
            </Button>
          </DialogFooter>
        </Dialog>
      )}
    </div>
  );
};
