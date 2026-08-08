import { useCallback, useEffect, useState } from "react";
import { Bell, Clock, Settings, Volume2, VolumeX } from "lucide-react";
import { Link } from "react-router-dom";
import { ThemeToggle } from "@/components/common/ThemeToggle";
import { SettingsModal } from "@/components/settings/SettingsModal";
import { Button } from "@/components/ui/button";
import { Tooltip } from "@/components/ui/tooltip";
import { useWebSocket } from "@/hooks/useWebSocket";
import { audioAlert } from "@/lib/audioAlert";
import { apiService } from "@/services/api";
import agapLogo from "@/assets/logos/logo-text-v3.svg";

export function DashboardHeader() {
  const [apiOnline, setApiOnline] = useState<boolean | null>(null);
  const [soundEnabled, setSoundEnabled] = useState(false);
  const [currentTime, setCurrentTime] = useState("");
  const [unreadCount, setUnreadCount] = useState(0);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const handleIncident = useCallback(() => {
    setUnreadCount((count) => count + 1);
    audioAlert.playEmergencySiren();
  }, []);
  const { isConnected: wsConnected } = useWebSocket(handleIncident);

  useEffect(() => {
    const updateTime = () => setCurrentTime(new Date().toLocaleTimeString("en-US", {
      hour12: false,
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    }));
    updateTime();
    const timer = window.setInterval(updateTime, 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    const checkHealth = async () => {
      try {
        await apiService.getHealth();
        setApiOnline(true);
      } catch {
        setApiOnline(false);
      }
    };
    void checkHealth();
    const timer = window.setInterval(checkHealth, 10000);
    return () => window.clearInterval(timer);
  }, []);

  const toggleSound = () => {
    const enabled = !soundEnabled;
    setSoundEnabled(enabled);
    audioAlert.setEnabled(enabled);
    if (enabled) audioAlert.playEmergencySiren();
  };

  const showNotifications = () => {
    alert(unreadCount > 0
      ? `System Notifications: ${unreadCount} new distress alerts received via BLE mesh.`
      : "System Notifications: All clear. No new distress alerts.");
    setUnreadCount(0);
  };

  return (
    <>
      <header className="h-16 border-b border-border bg-card/95 backdrop-blur px-4 sm:px-6 flex items-center justify-between sticky top-0 z-40 shrink-0">
        <div className="h-1 top-0 left-0 w-full bg-[#D15033] absolute -z-1" />
        <div className="flex items-center gap-4">
          <Link to="/" className="flex items-center hover:opacity-90 transition-opacity">
            <img src={agapLogo} alt="AGAP" className="h-8 w-auto object-contain" />
          </Link>
          <span className="hidden sm:inline-flex text-xs font-semibold px-3 py-1.5 rounded-md bg-muted text-muted-foreground border border-border">DRRMO Command Dispatch</span>
        </div>
        <div className="flex items-center gap-2.5 text-xs">
          <div className="hidden xl:flex items-center gap-2 bg-muted px-3 py-1.5 rounded-md border border-border text-muted-foreground">
            <Clock className="h-3.5 w-3.5" /><span className="tabular-nums font-medium">{currentTime || "00:00:00"}</span>
          </div>
          <div className="hidden lg:flex items-center gap-2 bg-muted px-2.5 py-1.5 rounded-md border border-border text-[11px] font-medium">
            <span className="text-muted-foreground">API:</span>
            <span className={apiOnline ? "text-emerald-600 dark:text-emerald-400 font-semibold" : "text-red-600 dark:text-red-400 font-semibold"}>{apiOnline ? "ONLINE" : "OFFLINE"}</span>
            <span className="text-muted-foreground ml-1">WS:</span>
            <span className={wsConnected ? "text-[#1F91A8] dark:text-[#4FB4C9] font-semibold" : "text-muted-foreground"}>{wsConnected ? "CONNECTED" : "CONNECTING"}</span>
          </div>
          <Tooltip content={soundEnabled ? "Disable Siren Alert" : "Enable Siren Alert"} side="bottom">
            <Button onClick={toggleSound} variant="outline" size="sm" className={`h-9 px-2.5 flex items-center gap-1.5 rounded-md ${soundEnabled ? "bg-[#F2A540]/10 text-[#F2A540] border-[#F2A540]/40" : "text-muted-foreground"}`}>
              {soundEnabled ? <Volume2 className="h-4 w-4 text-[#F2A540]" /> : <VolumeX className="h-4 w-4" />}
            </Button>
          </Tooltip>
          <ThemeToggle className="text-muted-foreground" lightIconClassName="text-[#F2A540]" darkIconClassName="text-[#1F91A8]" />
          <Tooltip content={unreadCount > 0 ? `${unreadCount} New Alerts` : "System Notifications"} side="bottom">
            <Button onClick={showNotifications} variant="outline" size="sm" className="h-9 w-9 p-0 rounded-md text-muted-foreground relative">
              <Bell className="h-4 w-4" />
              {unreadCount > 0 && <span className="absolute top-1.5 right-1.5 h-2.5 w-2.5 rounded-full bg-primary animate-ping" />}
            </Button>
          </Tooltip>
          <Tooltip content="Dispatcher Settings" side="bottom">
            <Button onClick={() => setIsSettingsOpen(true)} variant="outline" size="sm" className="h-9 w-9 p-0 rounded-md text-muted-foreground"><Settings className="h-4 w-4" /></Button>
          </Tooltip>
        </div>
      </header>
      <SettingsModal isOpen={isSettingsOpen} onClose={() => setIsSettingsOpen(false)} />
    </>
  );
}
