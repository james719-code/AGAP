import React, { useState, useEffect } from "react";
import {
  Users,
  CheckCircle,
  Clock,
  Search,
  Filter,
  UserCheck,
  RefreshCw,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { apiService, API_BASE_URL } from "@/services/api";
import type { UserRole, UserAccount } from "@/types/api";

export const UsersPage: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState("");
  const [roleFilter, setRoleFilter] = useState<string>("ALL");
  const [users, setUsers] = useState<UserAccount[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const data = await apiService.getUsers();
      setUsers(data);
    } catch (err) {
      console.error("Failed to fetch users:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  const toggleVerify = async (user: UserAccount) => {
    try {
      await fetch(`${API_BASE_URL}/admin/users/verify`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ user_id: user.id }),
      });
      fetchUsers();
    } catch (err) {
      console.error("Failed to verify user:", err);
    }
  };

  const changeRole = (id: string | number, newRole: UserRole) => {
    setUsers((prev) =>
      prev.map((u) => (u.id === id ? { ...u, role: newRole } : u)),
    );
  };

  const filteredUsers = users.filter((u) => {
    const matchesSearch =
      u.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      u.username.toLowerCase().includes(searchTerm.toLowerCase()) ||
      u.email.toLowerCase().includes(searchTerm.toLowerCase()) ||
      u.nationalId.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesRole = roleFilter === "ALL" || u.role === roleFilter;
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
          <Button
            onClick={fetchUsers}
            variant="outline"
            size="sm"
            className="h-8 gap-1"
          >
            <RefreshCw
              className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`}
            />{" "}
            Refresh
          </Button>
          <Badge
            variant="outline"
            className="border-teal-500/30 text-teal-600 dark:text-teal-400 bg-teal-500/10 px-3 py-1"
          >
            <UserCheck className="h-3.5 w-3.5 mr-1" />{" "}
            {users.filter((u) => u.is_verified).length} VERIFIED USERS
          </Badge>
          <Badge
            variant="outline"
            className="border-amber-500/30 text-amber-600 dark:text-amber-400 bg-amber-500/10 px-3 py-1"
          >
            <Clock className="h-3.5 w-3.5 mr-1" />{" "}
            {users.filter((u) => !u.is_verified).length} PENDING VERIFICATION
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
          <span className="text-xs font-semibold text-slate-500">
            Filter Role:
          </span>
          <select
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
            className="h-10 px-3 text-xs rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 font-medium"
          >
            <option value="ALL">All Roles</option>
            <option value="CITIZEN">Citizen</option>
            <option value="RESPONDER">Responder</option>
            <option value="DISPATCHER">Dispatcher</option>
            <option value="ADMIN">Admin</option>
          </select>
        </div>
      </div>

      {/* Users Data Table */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          {loading ? (
            <div className="p-8 text-center text-slate-400 text-xs font-mono">
              Loading system accounts from database...
            </div>
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
                  <tr
                    key={user.id}
                    className="hover:bg-slate-50 dark:hover:bg-slate-800/40 transition-colors"
                  >
                    <td className="p-4">
                      <div className="font-bold text-slate-900 dark:text-slate-100">
                        {user.name || user.username}{" "}
                        <span className="font-mono text-slate-400 font-normal">
                          (@{user.username})
                        </span>
                      </div>
                      <div className="text-slate-500 dark:text-slate-400 font-mono text-[11px]">
                        {user.email || "No email registered"}{" "}
                        {user.phone_number ? `· ${user.phone_number}` : ""}
                      </div>
                    </td>
                    <td className="p-4 font-mono">
                      <span className="bg-slate-100 dark:bg-slate-800 px-2.5 py-1 rounded-lg text-slate-700 dark:text-slate-300">
                        {user.national_id || "N/A"}
                      </span>
                    </td>
                    <td className="p-4">
                      <select
                        value={user.role}
                        onChange={(e) =>
                          changeRole(user.id, e.target.value as UserRole)
                        }
                        className="h-8 px-2 text-xs rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 font-bold text-slate-800 dark:text-slate-200"
                      >
                        <option value="CITIZEN">CITIZEN</option>
                        <option value="RESPONDER">RESPONDER</option>
                        <option value="DISPATCHER">DISPATCHER</option>
                        <option value="ADMIN">ADMIN</option>
                      </select>
                    </td>
                    <td className="p-4">
                      {user.is_verified ? (
                        <span className="inline-flex items-center gap-1 text-emerald-600 dark:text-emerald-400 font-bold">
                          <CheckCircle className="h-3.5 w-3.5" /> Verified
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-amber-600 dark:text-amber-400 font-bold">
                          <Clock className="h-3.5 w-3.5" /> Pending Verification
                        </span>
                      )}
                    </td>
                    <td className="p-4 text-right">
                      <Button
                        onClick={() => toggleVerify(user)}
                        variant="outline"
                        size="sm"
                        className={`h-8 text-xs font-bold ${
                          user.is_verified
                            ? "border-slate-300 dark:border-slate-800 text-slate-600 dark:text-slate-400"
                            : "border-emerald-500/40 bg-emerald-50 dark:bg-emerald-950/40 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-100"
                        }`}
                      >
                        {user.is_verified
                          ? "Revoke Verification"
                          : "Approve & Verify"}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
};
