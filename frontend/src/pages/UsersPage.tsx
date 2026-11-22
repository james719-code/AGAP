import React, { useEffect, useMemo, useState, useCallback } from 'react';
import { StatStrip } from '@/components/common/StatStrip';
import { apiService } from '@/services/api';
import { useAgencies } from '@/hooks/useAgencies';
import type { AccountStatus, UserAccount, UserRole } from '@/types/api';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { DataTableViewport } from '@/components/common/DataTableViewport';
import { RegistryPagination } from '@/components/common/RegistryPagination';
import { RegistrySearch } from '@/components/common/RegistrySearch';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Separator } from '@/components/ui/separator';
import {
  UserCheck,
  ShieldCheck,
  RefreshCw,
  Edit,
  Trash2,
  CheckCircle2,
  ShieldAlert,
} from 'lucide-react';

export const UsersPage: React.FC = () => {
  const PAGE_SIZE = 25;
  const { agencies } = useAgencies();

  const [users, setUsers] = useState<UserAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [roleTab, setRoleTab] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [currentPage, setCurrentPage] = useState(1);

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
  const [verifyTarget, setVerifyTarget] = useState<UserAccount | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [verifyError, setVerifyError] = useState<string | null>(null);

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
    queueMicrotask(() => void fetchUsers());
  }, [fetchUsers]);

  const showNotification = (msg: string) => {
    setActionSuccess(msg);
    setTimeout(() => setActionSuccess(null), 4000);
  };

  // Confirm before granting an account access.
  const handleQuickVerify = async (user: UserAccount) => {
    setVerifying(true);
    setVerifyError(null);
    try {
      await apiService.verifyUser(user.id);
      showNotification(`Verified user account ${user.username} successfully.`);
      setVerifyTarget(null);
      fetchUsers();
    } catch (err) {
      setVerifyError(err instanceof Error ? err.message : 'Failed to verify user');
    } finally {
      setVerifying(false);
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
  const filteredUsers = useMemo(() => users.filter((u) => {
    if (roleTab !== 'ALL' && u.role !== roleTab) return false;
    if (statusFilter !== 'ALL' && u.account_status !== statusFilter) return false;
    if (searchQuery.trim()) {
      const q = searchQuery.trim().toLowerCase();
      const matchName = u.name?.toLowerCase().includes(q);
      const matchUsername = u.username?.toLowerCase().includes(q);
      const matchPhone = u.phone_number?.toLowerCase().includes(q);
      const matchEmail = u.email?.toLowerCase().includes(q);
      const matchId = String(u.id).includes(q);
      if (!matchName && !matchUsername && !matchPhone && !matchEmail && !matchId) return false;
    }
    return true;
  }), [roleTab, searchQuery, statusFilter, users]);

  const totalPages = Math.max(1, Math.ceil(filteredUsers.length / PAGE_SIZE));
  const page = Math.min(currentPage, totalPages);
  const visibleUsers = filteredUsers.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  function clearFilters() {
    setRoleTab('ALL');
    setStatusFilter('ALL');
    setSearchQuery('');
    setCurrentPage(1);
  }

  const pendingVerificationUsers = users.filter((u) => u.account_status === 'PENDING');

  return (
    <div className="page-stack">
      {/* Page Header */}
      <div className="page-header">
        <div>
          <span className="page-eyebrow">Administration</span>
          <h1 className="page-title">Users</h1>
          <p className="page-description">
            Verify accounts and manage user roles and access.
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={fetchUsers}
          disabled={loading}
          className="gap-2 shrink-0 rounded-md border-border bg-card text-foreground hover:bg-accent"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} /> Refresh
        </Button>
      </div>

      {actionSuccess && (
        <div role="status" className="tone-success flex items-center gap-2 rounded-xl border border-border p-4 text-sm font-semibold">
          <CheckCircle2 className="h-4 w-4 shrink-0" />
          {actionSuccess}
        </div>
      )}

      <StatStrip
        label="Account status"
        activeKey={statusFilter}
        onSelect={(key) => { setStatusFilter((current: string) => (current === key && key !== 'ALL' ? 'ALL' : key)); setCurrentPage(1); }}
        items={[
          { key: 'ALL', label: 'All accounts', value: loading ? null : users.length, tone: 'neutral' },
          { key: 'PENDING', label: 'Awaiting verification', value: loading ? null : users.filter((u) => u.account_status === 'PENDING').length, tone: 'moderate' },
          { key: 'APPROVED', label: 'Approved', value: loading ? null : users.filter((u) => u.account_status === 'APPROVED').length, tone: 'resolved' },
          { key: 'SUSPENDED', label: 'Suspended', value: loading ? null : users.filter((u) => u.account_status === 'SUSPENDED').length, tone: 'critical' },
        ]}
      />

      {/* Pending Verification Notice Banner */}
      {pendingVerificationUsers.length > 0 && (
        <div className="p-4 rounded-md bg-amber-500/10 border border-amber-500/30 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-md bg-amber-500/20 text-amber-600 dark:text-amber-400 shrink-0">
              <UserCheck className="h-5 w-5" />
            </div>
            <div>
              <div className="text-sm font-bold text-amber-700 dark:text-amber-300">
                {pendingVerificationUsers.length} {pendingVerificationUsers.length === 1 ? 'account' : 'accounts'} awaiting verification
              </div>
              <div className="text-xs text-amber-600/90 dark:text-amber-400/90">
                Newly registered citizens and responders require administrative approval to access distress services.
              </div>
            </div>
          </div>
          <Button
            size="sm"
            className="bg-primary hover:bg-primary/90 text-primary-foreground shrink-0 text-xs font-semibold rounded-md"
            onClick={() => {
              setRoleTab('ALL');
              setStatusFilter('PENDING');
              setSearchQuery('');
              setCurrentPage(1);
            }}
          >
            Review Pending Users
          </Button>
        </div>
      )}

      {/* Main Roster Card */}
      <div className="surface-panel">
        {/* Toolbar: role tabs + search + status filter in one row, on a shaded
            strip that's visually welded to the table beneath it. */}
        <div className="flex flex-col xl:flex-row xl:items-center gap-3 px-4 py-3 bg-muted border-b border-border">
          {/* Role Filter Tabs */}
          <Tabs value={roleTab} onValueChange={(value) => { setRoleTab(value); setCurrentPage(1); }} className="flex-shrink-0">
            <TabsList className="h-auto min-h-10 flex-wrap justify-start bg-card p-0.5">
              <TabsTrigger
                value="ALL"
                className="text-xs font-medium data-[state=active]:bg-muted data-[state=active]:text-primary data-[state=active]:shadow-[0_1px_2px_rgba(10,10,10,0.04)] rounded-md px-2 py-1.5 sm:px-3"
              >
                All Users ({users.length})
              </TabsTrigger>
              <TabsTrigger
                value="CITIZEN"
                className="text-xs font-medium data-[state=active]:bg-muted data-[state=active]:text-primary data-[state=active]:shadow-[0_1px_2px_rgba(10,10,10,0.04)] rounded-md px-2 py-1.5 sm:px-3"
              >
                Citizens
              </TabsTrigger>
              <TabsTrigger
                value="RESPONDER"
                className="text-xs font-medium data-[state=active]:bg-muted data-[state=active]:text-primary data-[state=active]:shadow-[0_1px_2px_rgba(10,10,10,0.04)] rounded-md px-2 py-1.5 sm:px-3"
              >
                Responders
              </TabsTrigger>
              <TabsTrigger
                value="ADMIN"
                className="text-xs font-medium data-[state=active]:bg-muted data-[state=active]:text-primary data-[state=active]:shadow-[0_1px_2px_rgba(10,10,10,0.04)] rounded-md px-2 py-1.5 sm:px-3"
              >
                Admins
              </TabsTrigger>
            </TabsList>
          </Tabs>

          <Separator orientation="vertical" className="hidden xl:block h-6 bg-[#E7E3DC] dark:bg-[#26292C]" />

          {/* Controls: Search and Status Filter */}
          <div className="flex flex-col sm:flex-row sm:items-center gap-2 flex-1 w-full">
            <RegistrySearch label="Search user accounts" placeholder="Search name, email, phone, or ID" value={searchQuery} onChange={(value) => { setSearchQuery(value); setCurrentPage(1); }} />

            <Select
              aria-label="Account status"
              value={statusFilter}
              onChange={(e) => { setStatusFilter(e.target.value); setCurrentPage(1); }}
              className="h-control sm:w-[170px] rounded-lg border-border bg-card"
            >
              <option value="ALL">All Statuses</option>
              <option value="PENDING">Pending Only</option>
              <option value="APPROVED">Approved Only</option>
              <option value="REJECTED">Rejected Only</option>
              <option value="SUSPENDED">Suspended Only</option>
            </Select>
            {(roleTab !== 'ALL' || statusFilter !== 'ALL' || searchQuery) && <Button type="button" variant="ghost" onClick={clearFilters}>Clear filters</Button>}
          </div>
        </div>

        {/* Results header: continuous with the toolbar above and table below. */}
        <div className="surface-header flex-wrap">
          <h2 className="font-bold">Account directory</h2>
          {!loading && <span className="text-sm text-muted-foreground tabular-nums">{filteredUsers.length} of {users.length} accounts</span>}
        </div>

        <DataTableViewport label="User accounts table">
          {loading ? (
            <div className="state-message" role="status">Loading user roster…</div>
          ) : error ? (
            <div className="state-message state-message-error" role="alert"><p>{error}</p><Button type="button" variant="link" onClick={() => void fetchUsers()}>Retry</Button></div>
          ) : filteredUsers.length === 0 ? (
            <div className="state-message">
              <p>{users.length ? 'No accounts match these filters.' : 'No accounts to display.'}</p>
              {users.length > 0 && <Button type="button" variant="link" onClick={clearFilters}>Clear filters</Button>}
            </div>
          ) : (
            <table className="data-table">
              <thead className="bg-muted border-b border-border">
                <tr>
                  <th scope="col" className="text-left">ID</th>
                  <th scope="col" className="text-left">User Identity</th>
                  <th scope="col" className="text-left">Role</th>
                  <th scope="col" className="text-left">Status & Verification</th>
                  <th scope="col" className="text-left">Contact & National ID</th>
                  <th scope="col" className="text-left">Organization</th>
                  <th scope="col" className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {visibleUsers.map((u) => {
                  const agencyObj = agencies.find((a) => a.id === u.agency_id);
                  return (
                    <tr key={u.id} className="border-b border-[#E7E3DC]/70 dark:border-[#26292C]/70 hover:bg-accent transition-colors">
                      <td><span className="identity-chip">USR-{u.id}</span></td>
                      <td className="px-4 py-3">
                        <span className="table-primary">{u.name || u.username}</span>
                        <span className="table-secondary-inline">@{u.username}</span>
                      </td>
                      <td className="px-4 py-3">
                        {u.role === 'ADMIN' ? (
                          <Badge className="rounded-md bg-purple-500/10 text-purple-600 dark:text-purple-400 border-purple-500/30 text-[10px] font-semibold">
                            ADMIN
                          </Badge>
                        ) : u.role === 'RESPONDER' ? (
                          <Badge className="rounded-md bg-[#1F91A8]/10 text-[#1F91A8] dark:text-[#4FB4C9] border-[#1F91A8]/30 text-[10px] font-semibold">
                            RESPONDER
                          </Badge>
                        ) : (
                          <Badge variant="outline" className="rounded-md text-[10px] font-semibold text-slate-600 dark:text-slate-400">
                            CITIZEN
                          </Badge>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-1.5">
                          {u.account_status === 'APPROVED' ? (
                          <Badge variant="success" className="rounded-md text-xs font-semibold">
                              APPROVED
                            </Badge>
                          ) : u.account_status === 'PENDING' ? (
                            <Badge className="rounded-md bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/30 text-[10px] font-semibold">
                              PENDING
                            </Badge>
                          ) : u.account_status === 'SUSPENDED' ? (
                            <Badge className="rounded-md bg-red-500/10 text-red-600 dark:text-red-400 border-red-500/30 text-[10px] font-semibold">
                              SUSPENDED
                            </Badge>
                          ) : (
                            <Badge variant="destructive" className="rounded-md text-[10px] font-semibold">
                              {u.account_status}
                            </Badge>
                          )}
                          {u.is_verified ? (
                            <Badge variant="success" className="text-[10px]">VERIFIED</Badge>
                          ) : (
                            <Badge variant="warning" className="text-[10px]">UNVERIFIED</Badge>
                          )}
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex flex-col items-start gap-1">
                          {u.phone_number && (
                            <span className="text-foreground">{u.phone_number}</span>
                          )}
                          {u.email && (
                            <span className="text-muted-foreground">{u.email}</span>
                          )}
                          {u.national_id && (
                            <span className="text-[10px] font-mono text-muted-foreground">
                              Nat ID: {u.national_id}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        {agencyObj ? (
                          <div className="text-foreground font-medium">{agencyObj.name}</div>
                        ) : (
                          <span className="text-muted-foreground text-[11px]">—</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right whitespace-nowrap min-w-fit">
                        <div className="flex items-center justify-end gap-1.5">
                          {u.account_status === 'PENDING' && (
                            <Button
                              size="sm"
                              variant="ghost"
                              className="record-action record-action-success"
                              onClick={() => { setVerifyTarget(u); setVerifyError(null); }}
                              title="Verify user"
                              aria-label={`Verify ${u.name || u.username}`}
                            >
                              <ShieldCheck className="h-[17px] w-[17px]" />
                            </Button>
                          )}
                          <Button
                            size="sm"
                            variant="ghost"
                            className="record-action record-action-warning"
                            title="Change account status"
                            aria-label={`Change status for ${u.name || u.username}`}
                            onClick={() => {
                              setStatusModalUser(u);
                              setNewStatus(u.account_status);
                              setStatusRemarks('');
                            }}
                          >
                            <ShieldAlert className="h-[17px] w-[17px]" />
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            className="record-action disabled:opacity-50"
                            title="Edit user"
                            aria-label={`Edit ${u.name || u.username}`}
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
                            <Edit className="h-[17px] w-[17px]" />
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            className="record-action record-action-danger"
                            title="Delete user"
                            aria-label={`Delete ${u.name || u.username}`}
                            onClick={() => setDeleteModalUser(u)}
                          >
                            <Trash2 className="h-[17px] w-[17px]" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </DataTableViewport>
        {!loading && !error && <RegistryPagination total={filteredUsers.length} page={page} pageSize={PAGE_SIZE} onPageChange={setCurrentPage} />}
      </div>

      {verifyTarget && (
        <Dialog open onOpenChange={(open) => { if (!open && !verifying) setVerifyTarget(null); }} role="alertdialog" contentClassName="max-w-md">
          <DialogHeader>
            <DialogTitle>Verify account?</DialogTitle>
            <DialogDescription>Confirm access approval for this account.</DialogDescription>
          </DialogHeader>
          <div className="rounded-lg border border-border bg-muted p-4">
            <p className="font-semibold">{verifyTarget.name || verifyTarget.username}</p>
            <p className="mt-1 text-sm text-muted-foreground">@{verifyTarget.username} · {verifyTarget.role}</p>
          </div>
          {verifyError && <p role="alert" className="mt-3 text-sm text-destructive">{verifyError}</p>}
          <DialogFooter><Button type="button" variant="outline" disabled={verifying} onClick={() => setVerifyTarget(null)}>Cancel</Button><Button type="button" disabled={verifying} onClick={() => void handleQuickVerify(verifyTarget)}>{verifying ? 'Verifying…' : 'Confirm verification'}</Button></DialogFooter>
        </Dialog>
      )}

      {/* Account Status Modal */}
      {statusModalUser && (
        <Dialog open={!!statusModalUser} onOpenChange={() => setStatusModalUser(null)} contentClassName="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-lg font-bold text-foreground">
              <ShieldAlert className="h-5 w-5 text-[#F2A540]" /> Update Account Status — @{statusModalUser.username}
            </DialogTitle>
            <DialogDescription className="text-sm text-muted-foreground">
              Approve, reject, or suspend access for user {statusModalUser.name || statusModalUser.username}.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleStatusSubmit} className="space-y-4 py-3 text-sm">
            {statusError && <div className="text-red-600 dark:text-red-400 text-xs font-semibold">{statusError}</div>}

            <div className="space-y-1.5">
              <label htmlFor="userspage-target-account-status" className="font-semibold text-foreground">Target Account Status</label>
              <Select id="userspage-target-account-status"
                value={newStatus}
                onChange={(e) => setNewStatus(e.target.value as AccountStatus)}
                className="h-9 text-xs rounded-md border-border bg-muted focus-visible:ring-1 focus-visible:ring-[#D15033]"
              >
                <option value="APPROVED">APPROVED — Full System Access</option>
                <option value="REJECTED">REJECTED — Denied Access</option>
                <option value="SUSPENDED">SUSPENDED — Blocked from Logging In</option>
              </Select>
            </div>

            <div className="space-y-1.5">
              <label htmlFor="userspage-status-reason-reviewer-remarks" className="font-semibold text-foreground">Status Reason / Reviewer Remarks</label>
              <Input id="userspage-status-reason-reviewer-remarks"
                type="text"
                placeholder="Optional notes or rationale..."
                value={statusRemarks}
                onChange={(e) => setStatusRemarks(e.target.value)}
                className="h-9 text-xs rounded-md border-border bg-muted focus-visible:ring-1 focus-visible:ring-[#D15033]"
              />
            </div>

            <DialogFooter className="pt-3 border-t border-border flex justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setStatusModalUser(null)}
                className="rounded-md border-border bg-card text-muted-foreground hover:bg-accent"
              >
                Cancel
              </Button>
              <Button
                type="submit"
                size="sm"
                disabled={statusSaving}
                className="rounded-md bg-[#1F91A8] hover:bg-[#187588] text-white"
              >
                {statusSaving ? 'Updating...' : 'Save Status'}
              </Button>
            </DialogFooter>
          </form>
        </Dialog>
      )}

      {/* Edit User Modal */}
      {editModalUser && (
        <Dialog open={!!editModalUser} onOpenChange={() => setEditModalUser(null)} contentClassName="max-w-2xl">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-lg font-bold text-foreground">
              <Edit className="h-5 w-5 text-[#1F91A8]" /> Edit User Profile — @{editModalUser.username}
            </DialogTitle>
            <DialogDescription className="text-sm text-muted-foreground">
              Update identity, role level, and agency association.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleEditSubmit} className="space-y-3 py-2 text-sm">
            {editError && <div className="text-red-600 dark:text-red-400 text-xs font-semibold">{editError}</div>}

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1">
                <label htmlFor="userspage-full-name" className="font-semibold text-foreground">Full Name</label>
                <Input id="userspage-full-name"
                  type="text"
                  value={editForm.name || ''}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, name: e.target.value }))}
                  className="h-9 text-xs rounded-md border-border bg-muted focus-visible:ring-1 focus-visible:ring-[#D15033]"
                />
              </div>
              <div className="space-y-1">
                <label htmlFor="userspage-user-role" className="font-semibold text-foreground">User Role</label>
                <Select id="userspage-user-role"
                  value={editForm.role || 'CITIZEN'}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, role: e.target.value as UserRole }))}
                  className="h-9 text-xs rounded-md border-border bg-muted focus-visible:ring-1 focus-visible:ring-[#D15033]"
                >
                  <option value="CITIZEN">CITIZEN</option>
                  <option value="RESPONDER">RESPONDER</option>
                  <option value="ADMIN">ADMIN</option>
                </Select>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1">
                <label htmlFor="userspage-phone-number" className="font-semibold text-foreground">Phone Number</label>
                <Input id="userspage-phone-number"
                  type="text"
                  value={editForm.phone_number || ''}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, phone_number: e.target.value }))}
                  className="h-9 text-xs rounded-md border-border bg-muted focus-visible:ring-1 focus-visible:ring-[#D15033]"
                />
              </div>
              <div className="space-y-1">
                <label htmlFor="userspage-email-address" className="font-semibold text-foreground">Email Address</label>
                <Input id="userspage-email-address"
                  type="email"
                  value={editForm.email || ''}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, email: e.target.value }))}
                  className="h-9 text-xs rounded-md border-border bg-muted focus-visible:ring-1 focus-visible:ring-[#D15033]"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1">
                <label htmlFor="userspage-national-id" className="font-semibold text-foreground">National ID</label>
                <Input id="userspage-national-id"
                  type="text"
                  value={editForm.national_id || ''}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, national_id: e.target.value }))}
                  className="h-9 text-xs rounded-md border-border bg-muted focus-visible:ring-1 focus-visible:ring-[#D15033]"
                />
              </div>
              <div className="space-y-1">
                <label htmlFor="userspage-assigned-agency" className="font-semibold text-foreground">Assigned Agency</label>
                <Select id="userspage-assigned-agency"
                  value={editForm.agency_id ? String(editForm.agency_id) : ''}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, agency_id: e.target.value ? Number(e.target.value) : 0 }))}
                  className="h-9 text-xs rounded-md border-border bg-muted focus-visible:ring-1 focus-visible:ring-[#D15033]"
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

            <DialogFooter className="pt-3 border-t border-border flex justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setEditModalUser(null)}
                className="rounded-md border-border bg-card text-muted-foreground hover:bg-accent"
              >
                Cancel
              </Button>
              <Button
                type="submit"
                size="sm"
                disabled={editSaving}
                className="rounded-md bg-[#1F91A8] hover:bg-[#187588] text-white"
              >
                {editSaving ? 'Saving...' : 'Save Changes'}
              </Button>
            </DialogFooter>
          </form>
        </Dialog>
      )}

      {/* Delete User Safety Modal */}
      {deleteModalUser && (
        <Dialog open={!!deleteModalUser} onOpenChange={() => setDeleteModalUser(null)} role="alertdialog" contentClassName="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-lg font-bold text-red-600 dark:text-red-400">
              <Trash2 className="h-5 w-5" /> Confirm Account Deletion
            </DialogTitle>
            <DialogDescription className="text-sm text-muted-foreground">
              Are you sure you want to permanently delete account <strong className="text-foreground">@{deleteModalUser.username}</strong> ({deleteModalUser.name})? This action cannot be undone.
            </DialogDescription>
          </DialogHeader>

          {deleteError && <div className="text-red-600 dark:text-red-400 text-xs font-semibold py-2">{deleteError}</div>}

          <DialogFooter className="pt-3 border-t border-border flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setDeleteModalUser(null)}
              className="rounded-md border-border bg-card text-muted-foreground hover:bg-accent"
            >
              Cancel
            </Button>
            <Button
              type="button"
              variant="destructive"
              size="sm"
              disabled={deleteSaving}
              onClick={handleDeleteSubmit}
              className="rounded-md"
            >
              {deleteSaving ? 'Deleting...' : 'Delete Account'}
            </Button>
          </DialogFooter>
        </Dialog>
      )}
    </div>
  );
};
