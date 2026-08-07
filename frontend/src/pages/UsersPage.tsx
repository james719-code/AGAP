import React, { useState, useEffect } from 'react';
import { Users, CheckCircle, Clock, Search, Filter, UserCheck, RefreshCw, XCircle, Ban, History } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Dialog, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { apiService } from '@/services/api';
import type { UserRole, UserAccount, AuditLog } from '@/types/api';

type ReviewAction = {
  user: UserAccount;
  status: UserAccount['account_status'];
};

export const UsersPage: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const [roleFilter, setRoleFilter] = useState<string>('ALL');
  const [users, setUsers] = useState<UserAccount[]>([]);
  const [selectedUser, setSelectedUser] = useState<UserAccount | null>(null);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [reviewAction, setReviewAction] = useState<ReviewAction | null>(null);
  const [reviewRemarks, setReviewRemarks] = useState('');
  const [reviewSubmitting, setReviewSubmitting] = useState(false);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const data = await apiService.getUsers();
      setUsers(data);
    } catch (err) {
      console.error('Failed to fetch users:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  useEffect(() => {
    if (!selectedUser) {
      setAuditLogs([]);
      return;
    }
    apiService.getAuditLogs('USER', selectedUser.id, 10)
      .then(setAuditLogs)
      .catch((err) => {
        console.error('Failed to fetch user audit logs:', err);
        setAuditLogs([]);
      });
  }, [selectedUser]);

  const openReviewDialog = (user: UserAccount, status: UserAccount['account_status']) => {
    setReviewAction({ user, status });
    setReviewRemarks('');
  };

  const closeReviewDialog = () => {
    if (reviewSubmitting) return;
    setReviewAction(null);
    setReviewRemarks('');
  };

  const submitReviewAction = async () => {
    if (!reviewAction) return;
    const remarks = reviewRemarks.trim();
    if ((reviewAction.status === 'REJECTED' || reviewAction.status === 'SUSPENDED') && remarks.length === 0) {
      return;
    }
    setReviewSubmitting(true);
    try {
      await apiService.updateUserAccountStatus(reviewAction.user.id, reviewAction.status, remarks);
      await fetchUsers();
      setSelectedUser((current) =>
        current?.id === reviewAction.user.id ? { ...current, account_status: reviewAction.status } : current
      );
      setReviewAction(null);
      setReviewRemarks('');
    } catch (err) {
      console.error('Failed to update user account status:', err);
    } finally {
      setReviewSubmitting(false);
    }
  };

  const changeRole = (id: string | number, newRole: UserRole) => {
    setUsers((prev) =>
      prev.map((u) => (u.id === id ? { ...u, role: newRole } : u))
    );
  };


  const filteredUsers = users.filter((u) => {
    const matchesSearch =
      u.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      u.username.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (u.email || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
      (u.national_id || '').toLowerCase().includes(searchTerm.toLowerCase());
    const matchesRole = roleFilter === 'ALL' || u.role === roleFilter;
    return matchesSearch && matchesRole;
  });

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white dark:bg-slate-900 p-6 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm">
        <div className="flex items-center gap-3">
          <div className="bg-red-500/10 p-3 rounded-2xl border border-red-500/20 text-red-500">
            <Users className="h-6 w-6" />
          </div>
          <div>
            <h1 className="text-xl font-extrabold text-slate-900 dark:text-slate-100 tracking-tight">
              USER & ROLE MANAGEMENT
            </h1>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Admin HQ Verification, Credential Approval & Access Level Matrix
            </p>
          </div>
        </div>
        <div className="flex items-center gap-3 font-mono text-xs">
          <Button onClick={fetchUsers} variant="outline" size="sm" className="h-8 gap-1">
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} /> Refresh
          </Button>
          <Badge variant="outline" className="border-teal-500/30 text-teal-600 dark:text-teal-400 bg-teal-500/10 px-3 py-1">
            <UserCheck className="h-3.5 w-3.5 mr-1" /> {users.filter((u) => u.account_status === 'APPROVED').length} APPROVED USERS
          </Badge>
          <Badge variant="outline" className="border-amber-500/30 text-amber-600 dark:text-amber-400 bg-amber-500/10 px-3 py-1">
            <Clock className="h-3.5 w-3.5 mr-1" /> {users.filter((u) => u.account_status === 'PENDING').length} PENDING
          </Badge>
        </div>
      </div>

      {/* Filter & Search Toolbar */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
        <div className="relative w-full sm:w-80">
          <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
          <Input
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search name, username, email, or National ID..."
            className="pl-9 h-10 border-slate-200 dark:border-slate-800 text-xs bg-white dark:bg-slate-900"
          />
        </div>

        <div className="flex items-center gap-2 w-full sm:w-auto">
          <Filter className="h-4 w-4 text-slate-400" />
          <span className="text-xs font-semibold text-slate-500">Filter Role:</span>
          <select
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
            className="h-10 px-3 text-xs rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 font-medium"
          >
            <option value="ALL">All Roles</option>
            <option value="CITIZEN">Citizen</option>
            <option value="RESPONDER">Responder</option>
            <option value="ADMIN">Admin</option>
          </select>
        </div>
      </div>

      {/* Users Data Table */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          {loading ? (
            <div className="p-8 text-center text-slate-400 text-xs font-mono">Loading system accounts from database...</div>
          ) : (
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/60 text-slate-500 font-bold uppercase tracking-wider text-[11px]">
                  <th className="p-4">User Details</th>
                  <th className="p-4">National / Duty ID</th>
                  <th className="p-4">Operational Role</th>
                  <th className="p-4">Verification Status</th>
                  <th className="p-4 text-right">Admin Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 dark:divide-slate-800/60">
                {filteredUsers.map((user) => (
                  <tr key={user.id} className="hover:bg-slate-50 dark:hover:bg-slate-800/40 transition-colors">
                    <td className="p-4">
                      <div className="font-bold text-slate-900 dark:text-slate-100">
                        {user.name || user.username} <span className="font-mono text-slate-400 font-normal">(@{user.username})</span>
                      </div>
                      <div className="text-slate-500 dark:text-slate-400 font-mono text-[11px]">
                        {user.email || 'No email registered'} {user.phone_number ? `· ${user.phone_number}` : ''}
                      </div>
                    </td>
                    <td className="p-4 font-mono">
                      <span className="bg-slate-100 dark:bg-slate-800 px-2.5 py-1 rounded-lg text-slate-700 dark:text-slate-300">
                        {user.national_id || 'N/A'}
                      </span>
                    </td>
                    <td className="p-4">
                      <select
                        value={user.role}
                        onChange={(e) => changeRole(user.id, e.target.value as UserRole)}
                        className="h-8 px-2 text-xs rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 font-bold text-slate-800 dark:text-slate-200"
                      >
                        <option value="CITIZEN">CITIZEN</option>
                        <option value="RESPONDER">RESPONDER</option>
                        <option value="ADMIN">ADMIN</option>
                      </select>
                    </td>
                    <td className="p-4">
                      {user.account_status === 'APPROVED' ? (
                        <span className="inline-flex items-center gap-1 text-emerald-600 dark:text-emerald-400 font-bold">
                          <CheckCircle className="h-3.5 w-3.5" /> Approved
                        </span>
                      ) : user.account_status === 'REJECTED' ? (
                        <span className="inline-flex items-center gap-1 text-red-600 dark:text-red-400 font-bold">
                          <XCircle className="h-3.5 w-3.5" /> Rejected
                        </span>
                      ) : user.account_status === 'SUSPENDED' ? (
                        <span className="inline-flex items-center gap-1 text-slate-600 dark:text-slate-400 font-bold">
                          <Ban className="h-3.5 w-3.5" /> Suspended
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-amber-600 dark:text-amber-400 font-bold">
                          <Clock className="h-3.5 w-3.5" /> Pending Verification
                        </span>
                      )}
                    </td>
                    <td className="p-4 text-right">
                      <div className="flex justify-end gap-2">
                        <Button
                          onClick={() => openReviewDialog(user, 'APPROVED')}
                          variant="outline"
                          size="sm"
                          disabled={user.account_status === 'APPROVED'}
                          className="h-8 text-xs font-bold border-emerald-500/40 bg-emerald-50 dark:bg-emerald-950/40 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-100"
                        >
                          Approve
                        </Button>
                        <Button
                          onClick={() => openReviewDialog(user, 'REJECTED')}
                          variant="outline"
                          size="sm"
                          disabled={user.account_status === 'REJECTED'}
                          className="h-8 text-xs font-bold border-red-500/40 bg-red-50 dark:bg-red-950/40 text-red-600 dark:text-red-400 hover:bg-red-100"
                        >
                          Reject
                        </Button>
                        <Button
                          onClick={() => openReviewDialog(user, 'SUSPENDED')}
                          variant="outline"
                          size="sm"
                          disabled={user.account_status === 'SUSPENDED'}
                          className="h-8 text-xs font-bold border-slate-300 dark:border-slate-800 text-slate-600 dark:text-slate-400"
                        >
                          Suspend
                        </Button>
                        <Button
                          onClick={() => setSelectedUser(user)}
                          variant="outline"
                          size="sm"
                          className="h-8 text-xs font-bold border-slate-300 dark:border-slate-800 text-slate-600 dark:text-slate-400"
                        >
                          <History className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {selectedUser && (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl shadow-sm overflow-hidden">
          <div className="flex items-center justify-between p-4 border-b border-slate-200 dark:border-slate-800">
            <div>
              <h2 className="text-sm font-bold text-slate-900 dark:text-slate-100">Account Review History</h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {selectedUser.name || selectedUser.username} (@{selectedUser.username})
              </p>
            </div>
            <Button onClick={() => setSelectedUser(null)} variant="outline" size="sm" className="h-8 text-xs">
              Close
            </Button>
          </div>
          <div className="divide-y divide-slate-200 dark:divide-slate-800">
            {auditLogs.length === 0 ? (
              <div className="p-4 text-xs text-slate-500 dark:text-slate-400">No account audit events recorded yet.</div>
            ) : (
              auditLogs.map((log) => (
                <div key={log.id} className="p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs">
                  <div>
                    <div className="font-bold text-slate-900 dark:text-slate-100">{log.action}</div>
                    <div className="text-slate-500 dark:text-slate-400">
                      {log.old_value || 'N/A'} &gt; {log.new_value || 'N/A'}
                    </div>
                  </div>
                  <div className="font-mono text-slate-500 dark:text-slate-400">
                    {new Date(log.created_at).toLocaleString()}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}

      <Dialog open={!!reviewAction} onOpenChange={(open) => (!open ? closeReviewDialog() : undefined)}>
        {reviewAction && (
          <>
            <DialogHeader>
              <DialogTitle>Review Account Status</DialogTitle>
              <DialogDescription>
                {reviewAction.user.name || reviewAction.user.username} will be marked {reviewAction.status.toLowerCase()}.
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-2 py-3">
              <label className="text-xs font-semibold text-slate-600 dark:text-slate-300">
                Reviewer remarks {reviewAction.status === 'APPROVED' ? '(optional)' : '(required)'}
              </label>
              <textarea
                value={reviewRemarks}
                onChange={(e) => setReviewRemarks(e.target.value)}
                rows={4}
                maxLength={500}
                placeholder={
                  reviewAction.status === 'APPROVED'
                    ? 'Add approval notes, document checks, or verification context...'
                    : 'Enter the reason shown in the account review history...'
                }
                className="w-full resize-none rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs text-slate-900 outline-none focus:border-teal-500 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-100"
              />
              <div className="flex justify-between text-[11px] text-slate-500 dark:text-slate-400">
                <span>
                  {reviewAction.status === 'APPROVED'
                    ? 'Remarks are stored with the audit trail.'
                    : 'A reason is required for this action.'}
                </span>
                <span>{reviewRemarks.length}/500</span>
              </div>
            </div>
            <DialogFooter>
              <Button onClick={closeReviewDialog} variant="outline" size="sm" disabled={reviewSubmitting}>
                Cancel
              </Button>
              <Button
                onClick={submitReviewAction}
                size="sm"
                disabled={
                  reviewSubmitting ||
                  ((reviewAction.status === 'REJECTED' || reviewAction.status === 'SUSPENDED') &&
                    reviewRemarks.trim().length === 0)
                }
                className="bg-teal-600 text-white hover:bg-teal-700"
              >
                {reviewSubmitting ? 'Saving...' : `Confirm ${reviewAction.status}`}
              </Button>
            </DialogFooter>
          </>
        )}
      </Dialog>

    </div>
  );
};

