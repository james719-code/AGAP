import React, { useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, BookOpenText, Info, Loader2, MessageSquare, Send, Users } from 'lucide-react';
import { useIncidents } from '@/hooks/useIncidents';
import { useMessageCatalog } from '@/hooks/useMessageCatalog';
import { useAuth } from '@/context/AuthContext';
import { useWebSocket } from '@/context/WebSocketContext';
import { apiService } from '@/services/api';
import type { Incident, IncidentMessage } from '@/types/api';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ScrollArea } from '@/components/ui/scroll-area';
import { formatTargetLocation } from '@/lib/targetIdFormatter';

const activeStatuses = new Set(['RECEIVED', 'ACKNOWLEDGED', 'DISPATCHED', 'EN_ROUTE', 'ON_SCENE']);

// WebSocket delivers new messages immediately. This slower poll recovers from
// brief disconnects and supports deployments running an older backend.
const MESSAGE_POLL_INTERVAL_MS = 30000;

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
  const { messages: catalogMessages, loading: catalogLoading } = useMessageCatalog(false);
  const { user } = useAuth();
  const { isConnected, lastIncidentMessage } = useWebSocket();
  const [selectedIncidentId, setSelectedIncidentId] = useState<number | null>(null);
  const [selectedType, setSelectedType] = useState<Incident['incident_type'] | 'ALL'>('ALL');
  const [messages, setMessages] = useState<IncidentMessage[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [messageError, setMessageError] = useState<string | null>(null);
  const [inputText, setInputText] = useState('');
  const [sending, setSending] = useState(false);
  const [catalogSendingId, setCatalogSendingId] = useState<number | null>(null);
  const messagesRef = useRef<IncidentMessage[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesRef.current = messages;
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages]);

  const activeIncidents = useMemo(
    () => incidents.filter((incident) => activeStatuses.has(incident.status)),
    [incidents]
  );

  const incidentTypes = useMemo(
    () => Array.from(new Set(activeIncidents.map((incident) => incident.incident_type))).sort(),
    [activeIncidents]
  );

  const filteredActiveIncidents = useMemo(
    () => selectedType === 'ALL'
      ? activeIncidents
      : activeIncidents.filter((incident) => incident.incident_type === selectedType),
    [activeIncidents, selectedType]
  );

  const selectedIncident = useMemo<Incident | null>(() => {
    if (selectedIncidentId == null) return filteredActiveIncidents[0] || null;
    return filteredActiveIncidents.find((incident) => incident.id === selectedIncidentId) || filteredActiveIncidents[0] || null;
  }, [filteredActiveIncidents, selectedIncidentId]);

  useEffect(() => {
    if (selectedIncidentId == null && selectedIncident) {
      queueMicrotask(() => setSelectedIncidentId(selectedIncident.id));
    }
  }, [selectedIncident, selectedIncidentId]);

  useEffect(() => {
    if (!selectedIncident) {
      queueMicrotask(() => setMessages([]));
      return;
    }

    let cancelled = false;

    const loadMessages = async (isInitial: boolean) => {
      if (isInitial) {
        setMessagesLoading(true);
        setMessageError(null);
      }
      try {
        const result = await apiService.getIncidentMessages(selectedIncident.id);
        const data = result ?? [];
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
    const intervalId = isConnected
      ? null
      : setInterval(() => {
          if (document.visibilityState === 'visible') void loadMessages(false);
        }, MESSAGE_POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      if (intervalId !== null) clearInterval(intervalId);
    };
  }, [isConnected, selectedIncident]);

  useEffect(() => {
    if (!lastIncidentMessage || lastIncidentMessage.incident_id !== selectedIncident?.id) return;
    queueMicrotask(() => setMessages((prev) => prev.some((message) => message.id === lastIncidentMessage.id)
      ? prev
      : [...prev, lastIncidentMessage]));
  }, [lastIncidentMessage, selectedIncident?.id]);

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
      setMessages((prev) => prev.some((message) => message.id === created.id) ? prev : [...prev, created]);
      setInputText('');
    } catch (err) {
      setMessageError(err instanceof Error ? err.message : 'Failed to send incident message');
    } finally {
      setSending(false);
    }
  };

  const handleSendCatalogMessage = async (catalogId: number) => {
    if (!selectedIncident || catalogSendingId != null) return;
    setCatalogSendingId(catalogId);
    setMessageError(null);
    try {
      const created = await apiService.createIncidentMessage(selectedIncident.id, {
        predefined_message_id: catalogId,
        channel: 'ONLINE',
      });
      setMessages((prev) => prev.some((message) => message.id === created.id) ? prev : [...prev, created]);
    } catch (err) {
      setMessageError(err instanceof Error ? err.message : 'Failed to send catalog message');
    } finally {
      setCatalogSendingId(null);
    }
  };

  return (
    <div className="page-stack">
      <div className="page-header">
        <div>
          <span className="page-eyebrow">Command Communications</span>
          <h2 className="page-title">Incident Chat</h2>
          <p className="page-description">
            Coordinate with responders in incident-specific conversations.
          </p>
        </div>

        <Badge variant="outline" className={`h-9 gap-2 px-3 text-[10px] font-semibold ${isConnected ? 'border-emerald-500/30 text-emerald-500' : 'text-[#5C5A56] dark:text-[#B9B6B0]'}`}>
          <span className={`h-2 w-2 rounded-full ${isConnected ? 'animate-pulse bg-emerald-500' : 'bg-[#5C5A56] dark:bg-[#B9B6B0]'}`} />
          {isConnected ? 'Live' : 'Connecting'}
        </Badge>
      </div>

      <div className="h-[calc(100vh-12rem)] min-h-[560px] w-full grid grid-cols-1 lg:grid-cols-12 gap-4">
      <div className="lg:col-span-3 bg-white dark:bg-[#161A1D] border border-[#E7E3DC] dark:border-[#26292C] rounded-[2px] p-4 flex flex-col space-y-4 shadow-[0_1px_2px_rgba(10,10,10,0.04)]">
        <Button variant="outline" className="w-full justify-start gap-2 h-10 font-semibold">
          <Users className="h-4 w-4 text-[#1F91A8]" />
          <span>Incident Threads</span>
        </Button>

        <div className="text-[10px] font-bold uppercase tracking-wider text-[#5C5A56] dark:text-[#B9B6B0] px-1">
          ACTIVE INCIDENTS
        </div>

        <div className="flex flex-wrap gap-1.5" aria-label="Filter incident threads by emergency type">
          {(['ALL', ...incidentTypes] as const).map((type) => {
            const count = type === 'ALL'
              ? activeIncidents.length
              : activeIncidents.filter((incident) => incident.incident_type === type).length;
            return (
              <button
                type="button"
                key={type}
                onClick={() => setSelectedType(type)}
                className={`rounded-[2px] border px-2 py-1 text-[10px] font-semibold transition-colors ${
                  selectedType === type
                    ? 'border-[#1F91A8] bg-[#1F91A8] text-white'
                    : 'border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] text-[#5C5A56] dark:text-[#B9B6B0] hover:text-[#0A0A0A] dark:hover:text-white'
                }`}
              >
                {type} {count}
              </button>
            );
          })}
        </div>

        <ScrollArea className="flex-1 pr-1">
          {incidentsLoading ? (
            <div className="p-4 text-center text-xs text-[#5C5A56] dark:text-[#B9B6B0]">Loading incidents...</div>
          ) : filteredActiveIncidents.length === 0 ? (
            <div className="p-4 text-center text-xs text-[#5C5A56] dark:text-[#B9B6B0]">No active {selectedType === 'ALL' ? '' : selectedType.toLowerCase()} incidents.</div>
          ) : (
            <div className="space-y-2">
              {filteredActiveIncidents.map((incident) => (
                <button
                  type="button"
                  key={incident.id}
                  onClick={() => setSelectedIncidentId(incident.id)}
                  className={`w-full p-3 rounded-[2px] border transition-all text-left flex items-center gap-3 ${
                    selectedIncident?.id === incident.id
                      ? 'bg-[#1F91A8]/10 border-[#1F91A8]/40 shadow-[0_1px_2px_rgba(10,10,10,0.04)]'
                      : 'border-[#E7E3DC] dark:border-[#26292C] hover:bg-[#F7F5F2] dark:hover:bg-[#181C1D]'
                  }`}
                >
                  <div className="w-10 h-10 rounded-full bg-[#F7F5F2] dark:bg-[#181C1D] border border-[#E7E3DC] dark:border-[#26292C] flex items-center justify-center font-bold text-[#5C5A56] dark:text-[#B9B6B0] text-xs">
                    #{incident.id}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <div className="text-xs font-bold text-[#0A0A0A] dark:text-white truncate">{incident.incident_type}</div>
                      <Badge variant="outline" className="text-[10px]">{incident.status}</Badge>
                    </div>
                    <div className="text-[11px] text-[#5C5A56] dark:text-[#B9B6B0] truncate">
                      {formatTargetLocation(incident.target_id, incident.latitude, incident.longitude).readable} - {formatTime(incident.received_at)}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </ScrollArea>
      </div>

      <div className="lg:col-span-6 bg-white dark:bg-[#161A1D] border border-[#E7E3DC] dark:border-[#26292C] rounded-[2px] p-4 flex flex-col justify-between shadow-[0_1px_2px_rgba(10,10,10,0.04)]">
        <div className="pb-3 border-b border-[#E7E3DC] dark:border-[#26292C] flex items-center justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-9 h-9 rounded-full bg-[#1F91A8]/10 text-[#1F91A8] flex items-center justify-center font-bold text-xs">
              <MessageSquare className="h-4 w-4" />
            </div>
            <div className="min-w-0">
              <h3 className="text-sm font-bold text-[#0A0A0A] dark:text-white truncate">
                {selectedIncident ? `Incident #${selectedIncident.id}` : 'No Incident Selected'}
              </h3>
              <p className="text-[11px] text-[#5C5A56] dark:text-[#B9B6B0] truncate">
                {selectedIncident ? `${selectedIncident.incident_type} - ${selectedIncident.status}` : 'Select an incident thread'}
              </p>
            </div>
          </div>
          <Badge variant="outline" className="text-[10px] font-medium text-[#1F91A8] border-[#1F91A8]/30">
            {isConnected ? 'LIVE' : 'SYNCING'}
          </Badge>
        </div>

        <ScrollArea className="flex-1 py-4 px-2">
          {messageError && (
            <div className="mb-3 rounded-[2px] border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-500">
              {messageError}
            </div>
          )}

          {messagesLoading ? (
            <div className="flex items-center justify-center gap-2 py-8 text-xs text-[#5C5A56] dark:text-[#B9B6B0]">
              <Loader2 className="h-4 w-4 animate-spin" />
              Loading messages...
            </div>
          ) : messages.length === 0 ? (
            <div className="py-8 text-center text-xs text-[#5C5A56] dark:text-[#B9B6B0]">
              No messages recorded for this incident.
            </div>
          ) : (
            <div className="space-y-4">
              {messages.map((message) => {
                // The mobile application presents catalog/status messages as
                // centered system notices and operational chat as neutral,
                // left-aligned bubbles. Keep the web conversation consistent.
                const isSystem = message.message_type === 'STATUS_UPDATE';
                const isSelf = user != null && String(message.sender_user_id) === String(user.id);
                return (
                  <div key={message.id} className={`flex flex-col ${isSystem ? 'items-center' : isSelf ? 'items-end' : 'items-start'}`}>
                    {!isSystem && (
                      <div className="text-[10px] text-[#5C5A56] dark:text-[#B9B6B0] mb-1 px-2">
                        {isSelf ? 'You' : message.sender_name || 'Unknown sender'}
                        {message.sender_role ? ` · ${message.sender_role}` : ''} · {formatTime(message.sent_at)}
                      </div>
                    )}
                    <div
                      className={`max-w-[280px] text-sm leading-relaxed ${
                        isSystem
                          ? 'px-1 py-1 bg-transparent text-[#5C5A56] dark:text-[#B9B6B0] text-center font-bold'
                          : isSelf
                            ? 'px-3 py-3 bg-[#1F91A8] text-white rounded-[2px] rounded-br-sm'
                            : 'px-3 py-3 bg-[#F7F5F2] dark:bg-[#181C1D] text-[#0A0A0A] dark:text-white rounded-[2px] rounded-bl-sm'
                      }`}
                    >
                      {messageText(message)}
                    </div>
                  </div>
                );
              })}
              <div ref={messagesEndRef} />
            </div>
          )}
        </ScrollArea>

        <form onSubmit={handleSendMessage} className="pt-3 border-t border-[#E7E3DC] dark:border-[#26292C]">
          <div className="relative flex items-center bg-white dark:bg-[#161A1D] p-3">
            <textarea
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  e.currentTarget.form?.requestSubmit();
                }
              }}
              placeholder={selectedIncident ? 'Type an operational message...' : 'Select an incident first'}
              disabled={!selectedIncident || sending}
              rows={1}
              className="min-h-12 max-h-24 flex-1 resize-none rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-white dark:bg-[#161A1D] px-3 py-3 text-sm outline-none focus:border-[#D15033] focus:ring-1 focus:ring-[#D15033] placeholder:text-[#5C5A56] dark:text-[#B9B6B0] disabled:opacity-50"
            />
            <Button
              type="submit"
              size="sm"
              disabled={!selectedIncident || sending || !inputText.trim()}
              className="bg-[#D15033] hover:bg-[#D15033]/90 text-white rounded-full h-12 w-12 p-0 ml-3"
            >
              {sending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Send className="h-3.5 w-3.5" />}
            </Button>
          </div>
        </form>
      </div>

      <div className="lg:col-span-3 bg-white dark:bg-[#161A1D] border border-[#E7E3DC] dark:border-[#26292C] rounded-[2px] p-4 flex flex-col min-h-0 space-y-4 shadow-[0_1px_2px_rgba(10,10,10,0.04)]">
        <div className="flex items-center gap-2 pb-2 border-b border-[#E7E3DC] dark:border-[#26292C]">
          <Info className="h-4 w-4 text-[#1F91A8]" />
          <h3 className="text-sm font-bold text-[#0A0A0A] dark:text-white">Incident Context</h3>
        </div>

        {selectedIncident ? (
          <>
            <div className="py-2 space-y-2">
              <div className="text-[10px] font-bold uppercase tracking-wider text-[#5C5A56] dark:text-[#B9B6B0]">
                LOCATION
              </div>
              <div className="text-xs font-semibold text-[#0A0A0A] dark:text-white">
                {formatTargetLocation(selectedIncident.target_id, selectedIncident.latitude, selectedIncident.longitude).readable}
              </div>
              <p className="text-[11px] text-[#5C5A56] dark:text-[#B9B6B0] font-mono">
                {selectedIncident.latitude.toFixed(5)}, {selectedIncident.longitude.toFixed(5)}
              </p>
              <p className="text-[10px] text-[#5C5A56] dark:text-[#B9B6B0]">
                {formatTargetLocation(selectedIncident.target_id, selectedIncident.latitude, selectedIncident.longitude).subText}
              </p>
            </div>

            <div className="border-t border-[#E7E3DC] dark:border-[#26292C] py-3 space-y-2">
              <div className="text-[10px] font-bold uppercase tracking-wider text-[#5C5A56] dark:text-[#B9B6B0]">
                DELIVERY
              </div>
              <div className="flex flex-wrap gap-1.5">
                <Badge variant="info" className="text-[10px]">{selectedIncident.channel}</Badge>
                <Badge variant="outline" className="text-[10px]">{selectedIncident.delivery_path}</Badge>
              </div>
              <p className="text-[11px] text-[#5C5A56] dark:text-[#B9B6B0]">
                Received {formatTime(selectedIncident.received_at)}
              </p>
            </div>
          </>
        ) : (
          <div className="border-t border-[#E7E3DC] dark:border-[#26292C] py-3 space-y-2">
            <div className="text-xs font-bold text-red-600 dark:text-red-400 flex items-center gap-1.5">
              <AlertTriangle className="h-3.5 w-3.5" /> No incident selected
            </div>
            <p className="text-[11px] text-[#5C5A56] dark:text-[#B9B6B0] leading-relaxed">
              Select an incident thread to review operational messages.
            </p>
          </div>
        )}

        <div className="flex items-center gap-2 pt-2 pb-2 border-b border-[#E7E3DC] dark:border-[#26292C]">
          <BookOpenText className="h-4 w-4 text-[#1F91A8]" />
          <h3 className="text-sm font-bold text-[#0A0A0A] dark:text-white">Catalog Messages</h3>
        </div>

        <p className="text-[11px] leading-relaxed text-[#5C5A56] dark:text-[#B9B6B0]">
          Send an approved quick update to the selected incident.
        </p>

        <ScrollArea className="flex-1 min-h-0 pr-1">
          {catalogLoading ? (
            <div className="flex items-center justify-center gap-2 py-4 text-xs text-[#5C5A56] dark:text-[#B9B6B0]">
              <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading catalog...
            </div>
          ) : catalogMessages.length === 0 ? (
            <div className="py-4 text-center text-xs text-[#5C5A56] dark:text-[#B9B6B0]">No active catalog messages.</div>
          ) : (
            <div className="space-y-2">
              {catalogMessages.map((catalog) => (
                <button
                  type="button"
                  key={catalog.id}
                  onClick={() => handleSendCatalogMessage(catalog.id)}
                  disabled={!selectedIncident || catalogSendingId != null}
                  className="w-full rounded-[2px] border border-[#E7E3DC] dark:border-[#26292C] bg-[#F7F5F2] dark:bg-[#181C1D] p-3 text-left transition-colors hover:border-[#1F91A8]/40 hover:bg-[#1F91A8]/5 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <div className="mb-1 flex items-center justify-between gap-2">
                    <span className="truncate font-mono text-[10px] font-bold text-[#1F91A8]">{catalog.code}</span>
                    {catalogSendingId === catalog.id ? (
                      <Loader2 className="h-3 w-3 animate-spin text-[#1F91A8]" />
                    ) : (
                      <Badge variant="outline" className="text-[9px]">{catalog.message_type}</Badge>
                    )}
                  </div>
                  <div className="text-[11px] leading-relaxed text-[#0A0A0A] dark:text-white">{catalog.message_en}</div>
                </button>
              ))}
            </div>
          )}
        </ScrollArea>
      </div>
      </div>
    </div>
  );
};
