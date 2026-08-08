import React, { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import {
  LayoutDashboard,
  MapPin,
  BarChart3,
  MessageSquare,
  AlertTriangle,
  Building2,
  Users,
  UserCheck,
  Archive,
  HelpCircle,
  LogOut,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { Button } from "@/components/ui/button";
import { Tooltip } from "@/components/ui/tooltip";
import { DashboardHeader } from "@/components/layout/DashboardHeader";

export const AppShellContent: React.FC = () => {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleSignOut = () => {
    logout();
    navigate("/login");
  };

  // Grouped navigation structure based on operational usage
  const sidebarNavGroups = [
    {
      group: "MAIN DISPATCH",
      items: [
        {
          to: "/dashboard",
          label: "Overview",
          icon: LayoutDashboard,
          exact: true,
        },
        {
          to: "/dashboard/map",
          label: "Live GIS Map",
          icon: MapPin,
          exact: false,
        },
        {
          to: "/dashboard/chat",
          label: "P2P Dispatch Chat",
          icon: MessageSquare,
          exact: false,
        },
      ],
    },
    {
      group: "OPERATIONS",
      items: [
        {
          to: "/dashboard/incidents",
          label: "Incident Triage",
          icon: AlertTriangle,
          exact: false,
        },
        {
          to: "/dashboard/team",
          label: "Responder Fleet",
          icon: Users,
          exact: false,
        },
        {
          to: "/dashboard/users",
          label: "User Verification",
          icon: UserCheck,
          exact: false,
        },
        {
          to: "/dashboard/agencies",
          label: "Agency Network",
          icon: Building2,
          exact: false,
        },
      ],
    },
    {
      group: "INTELLIGENCE",
      items: [
        {
          to: "/dashboard/insights",
          label: "Telemetry Insights",
          icon: BarChart3,
          exact: false,
        },
        {
          to: "/dashboard/archive",
          label: "Distress Archive",
          icon: Archive,
          exact: false,
        },
      ],
    },
  ];

  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col font-sans transition-colors">
      <DashboardHeader />
      {/* Main Layout */}
      <div className="flex flex-1">
        {/* Left Sidebar */}
        <aside
          className={`${sidebarCollapsed ? "w-16" : "w-60"} transition-all duration-300 border-r border-border bg-card/95 flex flex-col justify-between sticky top-[4.25rem] h-[calc(100vh-4.25rem)] shrink-0 z-30 overflow-y-auto`}
        >
          <div className="space-y-4 p-3">
            {/* User Profile Card */}
            {!sidebarCollapsed ? (
              <div className="p-3 bg-muted rounded-lg border border-border flex items-center gap-3">
                <div className="w-9 h-9 rounded-full bg-primary text-primary-foreground flex items-center justify-center font-bold text-xs shrink-0">
                  {user?.name ? user.name.charAt(0) : "A"}
                </div>
                <div className="overflow-hidden">
                  <div className="text-xs font-bold text-card-foreground truncate">
                    {user?.name || "AgapUser"}
                  </div>
                  <div className="text-[10px] text-muted-foreground truncate">
                    Agap User Position
                  </div>
                </div>
              </div>
            ) : (
              <div className="flex justify-center py-1">
                <div className="w-9 h-9 rounded-full bg-primary text-primary-foreground flex items-center justify-center font-bold text-xs">
                  {user?.name ? user.name.charAt(0) : "A"}
                </div>
              </div>
            )}

            {/* Sidebar Toggle */}
            <div className="flex items-center justify-between px-2">
              {!sidebarCollapsed && (
                <span className="text-[10px] uppercase font-bold tracking-widest text-muted-foreground">
                  Command System
                </span>
              )}
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
                className="h-6 w-6 p-0 text-muted-foreground hover:text-foreground ml-auto"
              >
                {sidebarCollapsed ? (
                  <ChevronRight className="h-4 w-4" />
                ) : (
                  <ChevronLeft className="h-4 w-4" />
                )}
              </Button>
            </div>

            {/* Grouped Nav Items */}
            <div className="space-y-4">
              {sidebarNavGroups.map((group) => (
                <div key={group.group} className="space-y-1">
                  {!sidebarCollapsed && (
                    <div className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground px-2 py-0.5">
                      {group.group}
                    </div>
                  )}
                  <nav className="flex flex-col space-y-1 w-full">
                    {group.items.map((item) => {
                      const Icon = item.icon;
                      return (
                        <Tooltip
                          key={item.to}
                          content={sidebarCollapsed ? item.label : ""}
                          side="right"
                          wrapperClassName="w-full block"
                        >
                          <NavLink
                            to={item.to}
                            end={item.exact}
                            className={({ isActive }) =>
                              `w-full flex items-center ${sidebarCollapsed ? "justify-center px-0" : "gap-3 px-3"} py-2 rounded-md text-xs font-semibold transition-all ${
                                isActive
                                  ? "bg-primary/10 text-primary dark:text-[#E98A6E] border border-primary/25"
                                  : "text-muted-foreground hover:text-foreground hover:bg-muted border border-transparent"
                              }`
                            }
                          >
                            <Icon className="h-4 w-4 shrink-0" />
                            {!sidebarCollapsed && (
                              <span className="truncate">{item.label}</span>
                            )}
                          </NavLink>
                        </Tooltip>
                      );
                    })}
                  </nav>
                </div>
              ))}
            </div>
          </div>

          {/* Bottom Actions: Support & Sign Out */}
          <div className="space-y-1.5 p-3 pt-3 border-t border-border text-xs mt-auto shrink-0">
            <button
              onClick={() => alert("AGAP DRRMO Support Desk: +63 917 123 4567")}
              className={`w-full flex items-center ${sidebarCollapsed ? "justify-center px-0" : "gap-3 px-3"} py-2 text-muted-foreground hover:text-foreground text-xs font-medium rounded-md hover:bg-muted`}
            >
              <HelpCircle className="h-4 w-4 shrink-0" />
              {!sidebarCollapsed && <span>Support</span>}
            </button>

            <button
              onClick={handleSignOut}
              className={`w-full flex items-center ${sidebarCollapsed ? "justify-center px-0" : "gap-3 px-3"} py-2 text-red-600 dark:text-red-400 hover:bg-red-500/10 text-xs font-medium rounded-md`}
            >
              <LogOut className="h-4 w-4 shrink-0" />
              {!sidebarCollapsed && <span>Sign Out</span>}
            </button>
          </div>
        </aside>

        {/* Dynamic Page Content */}
        <main className="relative isolate z-0 flex-1 overflow-auto bg-muted/40 dark:bg-black/20 p-6 lg:p-8">
          <div className="mx-auto max-w-[1400px]">
            <Outlet />
          </div>
        </main>
      </div>

    </div>
  );
};

export const AppShell: React.FC = () => {
  return <AppShellContent />;
};
