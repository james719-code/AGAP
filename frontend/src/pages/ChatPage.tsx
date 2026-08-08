import React, { useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, Info, Loader2, MessageSquare, Send, Users } from 'lucide-react';
import { useIncidents } from '@/hooks/useIncidents';
import { useAuth } from '@/context/AuthContext';
import { apiService } from '@/services/api';
import type { Incident, IncidentMessage } from '@/types/api';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { ScrollArea } from '@/components/ui/scroll-area';

const activeStatuses = new Set(['RECEIVED', 'ACKNOWLEDGED', 'DISPATCHED', 'EN_ROUTE', 'ON_SCENE']);

// No backend push exists for new incident messages (unlike incident status,
// which does broadcast over the websocket), so we poll while a thread is
// open. Simple and good enough until the backend adds a real event for it.
const MESSAGE_POLL_INTERVAL_MS = 5000;

const formatTime = (value?: string) => {
  if (!value) return 'Not recorded';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const messageText = (message: IncidentMessage) => {
  return message.custom_message || message.message_en || message.predefined_code || 'Message sent';
};

export const ChatPage: React.FC = () => {
  const { incidents, loading: incidentsLoading } = useIncidents();
  const { user } = useAuth();
  const [selectedIncidentId, setSelectedIncidentId] = useState<number | null>(null);
  const [messages, setMessages] = useState<IncidentMessage[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [messageError, setMessageError] = useState<string | null>(null);
  const [inputText, setInputText] = useState('');
  const [sending, setSending] = useState(false);
  const messagesRef = useRef<IncidentMessage[]>([]);
  messagesRef.current = messages;

  const activeIncidents = useMemo(
    () => incidents.filter((incident) => activeStatuses.has(incident.status)),
    [incidents]
  );

  const selectedIncident = useMemo<Incident | null>(() => {
    if (selectedIncidentId == null) return activeIncidents[0] || incidents[0] || null;
    return incidents.find((incident) => incident.id === selectedIncidentId) || null;
  }, [activeIncidents, incidents, selectedIncidentId]);

  useEffect(() => {
    if (selectedIncidentId == null && selectedIncident) {
      setSelectedIncidentId(selectedIncident.id);
    }
  }, [selectedIncident, selectedIncidentId]);

  useEffect(() => {
    if (!selectedIncident) {
      setMessages([]);
      return;
    }

    let cancelled = false;

    const loadMessages = async (isInitial: boolean) => {
      if (isInitial) {
        setMessagesLoading(true);
        setMessageError(null);
      }
      try {
        const data = await apiService.getIncidentMessages(selectedIncident.id);
        if (!cancelled) {
          // Only replace state if something actually changed, so polling
          // doesn't cause a re-render (and scroll jump) every 5s for nothing.
          const changed =
            data.length !== messagesRef.current.length ||
            data.some((m, i) => m.id !== messagesRef.current[i]?.id);
          if (changed) setMessages(data);
        }
      } catch (err) {
        if (!cancelled && isInitial) {
          setMessages([]);
          setMessageError(err instanceof Error ? err.message : 'Failed to load incident messages');
        }
        // Silently ignore poll failures — the next poll will retry, and
        // surfacing a transient network blip as an error every 5s is noisier
        // than useful. The initial load still reports errors normally.
      } finally {
        if (!cancelled && isInitial) setMessagesLoading(false);
      }
    };

    loadMessages(true);
    const intervalId = setInterval(() => loadMessages(false), MESSAGE_POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      clearInterval(intervalId);
    };
  }, [selectedIncident]);

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedIncident || !inputText.trim()) return;

    setSending(true);
    setMessageError(null);
    try {
      const created = await apiService.createIncidentMessage(selectedIncident.id, {
        custom_message: inputText.trim(),
        channel: 'ONLINE',
      });
      setMessages((prev) => [...prev, created]);
      setInputText('');
    } catch (err) {
      setMessageError(err instanceof Error ? err.message : 'Failed to send incident message');
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="h-[calc(100vh-6rem)] w-full grid grid-cols-1 lg:grid-cols-12 gap-4">
      <div className="lg:col-span-3 bg-card border border-border rounded-lg p-4 flex flex-col space-y-4 shadow-sm">
        <Button variant="outline" className="w-full justify-start gap-2 h-10 font-semibold">
          <Users className="h-4 w-4 text-secondary" />
          <span>Incident Threads</span>
        </Button>

        <div className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground px-1">
          ACTIVE INCIDENTS
        </div>

        <ScrollArea className="flex-1 pr-1">
          {incidentsLoading ? (
            <div className="p-4 text-center text-xs text-muted-foreground">Loading incidents...</div>
          ) : incidents.length === 0 ? (
            <div className="p-4 text-center text-xs text-muted-foreground">No incidents available.</div>
          ) : (
            <div className="space-y-2">
              {incidents.map((incident) => (
                <button
                  type="button"
                  key={incident.id}
                  onClick={() => setSelectedIncidentId(incident.id)}
                  className={`w-full p-3 rounded-lg border transition-all text-left flex items-center gap-3 ${
                    selectedIncident?.id === incident.id
                      ? 'bg-secondary/10 border-secondary/40 shadow-sm'
                      : 'border-border hover:bg-muted'
                  }`}
                >
                  <div className="w-10 h-10 rounded-full bg-muted border border-border flex items-center justify-center font-bold text-muted-foreground text-xs">
                    #{incident.id}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <div className="text-xs font-bold text-card-foreground truncate">{incident.incident_type}</div>
                      <Badge variant="outline" className="text-[10px]">{incident.status}</Badge>
                    </div>
                    <div className="text-[11px] text-muted-foreground truncate">
                      {incident.target_id?.barangay || incident.target_id?.raw || 'Unmapped'} - {formatTime(incident.received_at)}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </ScrollArea>
      </div>

      <div className="lg:col-span-6 bg-card border border-border rounded-lg p-4 flex flex-col justify-between shadow-sm">
        <div className="pb-3 border-b border-border flex items-center justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-9 h-9 rounded-full bg-secondary/10 text-[#1F91A8] flex items-center justify-center font-bold text-xs">
              <MessageSquare className="h-4 w-4" />
            </div>
            <div className="min-w-0">
              <h3 className="text-sm font-bold text-card-foreground truncate">
                {selectedIncident ? `Incident #${selectedIncident.id}` : 'No Incident Selected'}
              </h3>
              <p className="text-[11px] text-muted-foreground truncate">
                {selectedIncident ? `${selectedIncident.incident_type} - ${selectedIncident.status}` : 'Select an incident thread'}
              </p>
            </div>
          </div>
          <Badge variant="outline" className="text-[10px] font-medium text-secondary border-secondary/30">
            ONLINE
          </Badge>
        </div>

        <ScrollArea className="flex-1 py-4 px-2">
          {messageError && (
            <div className="mb-3 rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-500">
              {messageError}
            </div>
          )}

          {messagesLoading ? (
            <div className="flex items-center justify-center gap-2 py-8 text-xs text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
              Loading messages...
            </div>
          ) : messages.length === 0 ? (
            <div className="py-8 text-center text-xs text-muted-foreground">
              No messages recorded for this incident.
            </div>
          ) : (
            <div className="space-y-4">
              {messages.map((message) => {
                const isSelf = user != null && String(message.sender_user_id) === String(user.id);
                return (
                  <div key={message.id} className={`flex flex-col ${isSelf ? 'items-end' : 'items-start'}`}>
                    <div className="text-[10px] text-muted-foreground mb-1 px-1">
                      {isSelf ? 'You' : message.sender_name || 'System'} - {formatTime(message.sent_at)}
                    </div>
                    <div
                      className={`max-w-md p-4 rounded-lg text-xs leading-relaxed shadow-sm ${
                        isSelf
                          ? 'bg-[#1F91A8] text-white rounded-br-none'
                          : 'bg-muted text-card-foreground rounded-bl-none border border-border'
                      }`}
                    >
                      {messageText(message)}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </ScrollArea>

        <form onSubmit={handleSendMessage} className="pt-3 border-t border-border">
          <div className="relative flex items-center bg-muted rounded-lg border border-border p-2">
            <Input
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              placeholder={selectedIncident ? 'Send an incident update...' : 'Select an incident first'}
              disabled={!selectedIncident || sending}
              className="border-0 bg-transparent focus-visible:ring-0 text-sm placeholder:text-muted-foreground"
            />
            <Button
              type="submit"
              size="sm"
              disabled={!selectedIncident || sending || !inputText.trim()}
              className="bg-primary hover:bg-primary/90 text-white rounded-md h-8 px-3 ml-2"
            >
              {sending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Send className="h-3.5 w-3.5" />}
            </Button>
          </div>
        </form>
      </div>

      <div className="lg:col-span-3 bg-card border border-border rounded-lg p-4 flex flex-col space-y-4 shadow-sm">
        <div className="flex items-center gap-2 pb-2 border-b border-border">
          <Info className="h-4 w-4 text-[#1F91A8]" />
          <h3 className="text-sm font-bold text-card-foreground">Incident Context</h3>
        </div>

        {selectedIncident ? (
          <>
            <Card className="bg-muted border-border p-3 space-y-2">
              <div className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
                LOCATION
              </div>
              <div className="text-xs font-semibold text-card-foreground">
                Barangay {selectedIncident.target_id?.barangay || 'Unmapped'}, Municipality {selectedIncident.target_id?.muni || selectedIncident.target_id?.raw}
              </div>
              <p className="text-[11px] text-muted-foreground font-mono">
                {selectedIncident.latitude.toFixed(5)}, {selectedIncident.longitude.toFixed(5)}
              </p>
            </Card>

            <Card className="bg-muted border-border p-3 space-y-2">
              <div className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
                DELIVERY
              </div>
              <div className="flex flex-wrap gap-1.5">
                <Badge variant="info" className="text-[10px]">{selectedIncident.channel}</Badge>
                <Badge variant="outline" className="text-[10px]">{selectedIncident.delivery_path}</Badge>
              </div>
              <p className="text-[11px] text-muted-foreground">
                Received {formatTime(selectedIncident.received_at)}
              </p>
            </Card>
          </>
        ) : (
          <Card className="bg-muted border-border p-3 space-y-2">
            <div className="text-xs font-bold text-red-600 dark:text-red-400 flex items-center gap-1.5">
              <AlertTriangle className="h-3.5 w-3.5" /> No incident selected
            </div>
            <p className="text-[11px] text-muted-foreground leading-relaxed">
              Select an incident thread to review operational messages.
            </p>
          </Card>
        )}
      </div>
    </div>
  );
};