import React, { useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, BookOpenText, Info, Loader2, MessageSquare, Send, Users } from 'lucide-react';
import { useIncidents } from '@/hooks/useIncidents';
import { useMessageCatalog } from '@/hooks/useMessageCatalog';
import { useAuth } from '@/context/AuthContext';
import { useWebSocket } from '@/context/WebSocketContext';
import { apiService } from '@/services/api';
import { hostedSecondaryRefreshMs, usesHostedApi } from '@/services/backend';
import { scheduleVisibleRefresh } from '@/hooks/useVisibleRefresh';
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
  const { incidents, loading: incidentsLoading, error: incidentsError, refetch: refetchIncidents } = useIncidents();
  const { messages: catalogMessages, loading: catalogLoading, error: catalogError, refetch: refetchCatalog } = useMessageCatalog(false);
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
  const conversationId = selectedIncident?.id;

  useEffect(() => {
    if (selectedIncidentId == null && selectedIncident) {
      queueMicrotask(() => setSelectedIncidentId(selectedIncident.id));
    }
  }, [selectedIncident, selectedIncidentId]);

  useEffect(() => {
    if (conversationId == null) {
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
        const result = await apiService.getIncidentMessages(conversationId);
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
        return false;
      } finally {
        if (!cancelled && isInitial) setMessagesLoading(false);
      }
    };

    loadMessages(true);
    const stop = isConnected
      ? null
      : scheduleVisibleRefresh(() => loadMessages(false), usesHostedApi ? hostedSecondaryRefreshMs : MESSAGE_POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      stop?.();
    };
  }, [isConnected, conversationId]);

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
          <span className="page-eyebrow">Dispatch</span>
          <h1 className="page-title">Incident Chat</h1>
          <p className="page-description">
            Coordinate with responders in incident-specific conversations.
          </p>
        </div>

        <Badge variant="outline" className={`h-9 gap-2 px-3 text-[10px] font-semibold ${isConnected ? 'border-emerald-500/30 text-emerald-700 dark:text-emerald-400' : 'text-muted-foreground'}`}>
          <span className={`h-2 w-2 rounded-full ${isConnected ? 'bg-emerald-500' : 'bg-[#5C5A56] dark:bg-[#B9B6B0]'}`} />
          {usesHostedApi ? 'Auto-refresh' : isConnected ? 'Live' : 'Connecting'}
        </Badge>
      </div>

      <div className="grid min-w-0 grid-cols-1 gap-4 md:grid-cols-[minmax(13rem,1fr)_minmax(0,2fr)] xl:grid-cols-[minmax(13rem,1fr)_minmax(0,2fr)_minmax(13rem,1fr)]">
      <div className="surface-panel flex min-w-0 flex-col gap-3 p-4 md:h-[min(66dvh,44rem)] md:min-h-96">
        <h2 className="flex items-center gap-2 font-bold">
          <Users className="size-4 text-primary" aria-hidden="true" /> Incident threads
        </h2>

        <div className="flex flex-wrap gap-1.5" role="group" aria-label="Filter incident threads by emergency type">
          {(['ALL', ...incidentTypes] as const).map((type) => {
            const count = type === 'ALL'
              ? activeIncidents.length
              : activeIncidents.filter((incident) => incident.incident_type === type).length;
            return (
              <button
                type="button"
                key={type}
                onClick={() => setSelectedType(type)}
                aria-pressed={selectedType === type}
                className={`min-h-8 rounded-md border px-2 py-1 text-xs font-semibold ${
                  selectedType === type
                    ? 'border-[#1F91A8] bg-[#1F91A8] text-white'
                    : 'border-border bg-muted text-muted-foreground hover:text-foreground'
                }`}
              >
                {type === 'ALL' ? 'All' : type.replaceAll('_', ' ')} {count}
              </button>
            );
          })}
        </div>

        <ScrollArea className="min-h-0 flex-1 max-md:max-h-52">
          {incidentsLoading ? (
            <div className="p-4 text-center text-xs text-muted-foreground">Loading incidents...</div>
          ) : incidentsError ? (
            <div className="state-message state-message-error" role="alert">
              <p>Could not load incident threads.</p>
              <Button type="button" variant="link" onClick={() => void refetchIncidents()}>Retry</Button>
            </div>
          ) : filteredActiveIncidents.length === 0 ? (
            <div className="state-message">
              <p>{selectedType === 'ALL' ? 'No active incidents.' : 'No active incidents of this type.'}</p>
              {selectedType !== 'ALL' && <Button type="button" variant="link" onClick={() => setSelectedType('ALL')}>Show all types</Button>}
            </div>
          ) : (
            <div className="space-y-2">
              {filteredActiveIncidents.map((incident) => (
                <button
                  type="button"
                  key={incident.id}
                  onClick={() => setSelectedIncidentId(incident.id)}
                  aria-pressed={selectedIncident?.id === incident.id}
                  className={`w-full p-3 rounded-md border transition-all text-left flex items-center gap-3 ${
                    selectedIncident?.id === incident.id
                      ? 'bg-[#1F91A8]/10 border-[#1F91A8]/40 shadow-[0_1px_2px_rgba(10,10,10,0.04)]'
                      : 'border-border hover:bg-accent'
                  }`}
                >
                  <div className="flex size-10 shrink-0 items-center justify-center rounded-md border border-border bg-muted text-xs font-bold text-muted-foreground tabular-nums">
                    #{incident.id}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <div className="text-xs font-bold text-foreground truncate">{incident.incident_type}</div>
                      <Badge variant="outline" className="text-[10px]">{incident.status}</Badge>
                    </div>
                    <div className="text-[11px] text-muted-foreground truncate">
                      {formatTargetLocation(incident.target_id, incident.latitude, incident.longitude).readable} - {formatTime(incident.received_at)}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </ScrollArea>
      </div>

      <div className="surface-panel flex h-[min(66dvh,44rem)] min-h-96 min-w-0 flex-col p-4">
        <div className="pb-3 border-b border-border flex items-center justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-9 h-9 rounded-full bg-[#1F91A8]/10 text-[#1F91A8] flex items-center justify-center font-bold text-xs">
              <MessageSquare className="h-4 w-4" />
            </div>
            <div className="min-w-0">
              <h3 className="text-sm font-bold text-foreground truncate">
                {selectedIncident ? `Incident #${selectedIncident.id}` : 'Conversation'}
              </h3>
              <p className="text-[11px] text-muted-foreground truncate">
                {selectedIncident ? `${selectedIncident.incident_type} - ${selectedIncident.status}` : 'Select an incident thread'}
              </p>
            </div>
          </div>
          <Badge variant="outline" className="text-[10px] font-medium text-[#1F91A8] border-[#1F91A8]/30">
            {usesHostedApi ? 'AUTO-REFRESH' : isConnected ? 'LIVE' : 'SYNCING'}
          </Badge>
        </div>

        <ScrollArea className="min-h-0 flex-1 py-4">
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
              {selectedIncident ? 'No messages yet. Send an update below to start the conversation.' : 'Choose an incident thread to see its messages.'}
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
                      <div className="text-[10px] text-muted-foreground mb-1 px-2">
                        {isSelf ? 'You' : message.sender_name || 'Unknown sender'}
                        {message.sender_role ? ` · ${message.sender_role}` : ''} · {formatTime(message.sent_at)}
                      </div>
                    )}
                    <div
                      className={`max-w-[280px] text-sm leading-relaxed ${
                        isSystem
                          ? 'px-1 py-1 bg-transparent text-muted-foreground text-center font-bold'
                          : isSelf
                            ? 'px-3 py-3 bg-[#1F91A8] text-white rounded-md rounded-br-sm'
                            : 'px-3 py-3 bg-muted text-foreground rounded-md rounded-bl-sm'
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

        <form onSubmit={handleSendMessage} className="pt-3 border-t border-border">
          <div className="relative flex items-center bg-card p-3">
            <textarea
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  e.currentTarget.form?.requestSubmit();
                }
              }}
              aria-label="Message"
              placeholder={selectedIncident ? 'Type an operational message...' : 'Select an incident first'}
              disabled={!selectedIncident || sending}
              rows={1}
              className="min-h-12 max-h-24 flex-1 resize-none rounded-md border border-border bg-card px-3 py-3 text-sm outline-none focus:border-primary focus:ring-1 focus:ring-[#D15033] placeholder:text-muted-foreground disabled:opacity-50"
            />
            <Button
              type="submit"
              aria-label="Send message"
              size="sm"
              disabled={!selectedIncident || sending || !inputText.trim()}
              className="size-12 shrink-0 rounded-full p-0"
            >
              {sending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Send className="h-3.5 w-3.5" />}
            </Button>
          </div>
        </form>
      </div>

      <div className="surface-panel flex min-w-0 flex-col gap-3 p-4 md:col-span-2 xl:col-span-1 xl:h-[min(66dvh,44rem)] xl:min-h-96">
        <div className="flex items-center gap-2 pb-2 border-b border-border">
          <Info className="h-4 w-4 text-[#1F91A8]" />
          <h3 className="text-sm font-bold text-foreground">Incident Context</h3>
        </div>

        {selectedIncident ? (
          <>
            <div className="py-2 space-y-2">
              <div className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
                LOCATION
              </div>
              <div className="text-xs font-semibold text-foreground">
                {formatTargetLocation(selectedIncident.target_id, selectedIncident.latitude, selectedIncident.longitude).readable}
              </div>
              <p className="text-[11px] text-muted-foreground font-mono">
                {selectedIncident.latitude.toFixed(5)}, {selectedIncident.longitude.toFixed(5)}
              </p>
              <p className="text-[10px] text-muted-foreground">
                {formatTargetLocation(selectedIncident.target_id, selectedIncident.latitude, selectedIncident.longitude).subText}
              </p>
            </div>

            <div className="border-t border-border py-3 space-y-2">
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
            </div>
          </>
        ) : (
          <div className="border-t border-border py-3 space-y-2">
            <div className="text-xs font-bold text-red-600 dark:text-red-400 flex items-center gap-1.5">
              <AlertTriangle className="h-3.5 w-3.5" /> No incident selected
            </div>
            <p className="text-[11px] text-muted-foreground leading-relaxed">
              Select an incident thread to review operational messages.
            </p>
          </div>
        )}

        <div className="flex items-center gap-2 pt-2 pb-2 border-b border-border">
          <BookOpenText className="h-4 w-4 text-[#1F91A8]" />
          <h3 className="text-sm font-bold text-foreground">Catalog Messages</h3>
        </div>

        <p className="text-[11px] leading-relaxed text-muted-foreground">
          Send an approved quick update to the selected incident.
        </p>

        <ScrollArea className="min-h-0 flex-1 max-xl:max-h-72">
          {catalogLoading ? (
            <div className="flex items-center justify-center gap-2 py-4 text-xs text-muted-foreground">
              <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading catalog...
            </div>
          ) : catalogError ? (
            <div className="state-message state-message-error" role="alert">
              <p>Could not load message templates.</p>
              <Button type="button" variant="link" onClick={refetchCatalog}>Retry</Button>
            </div>
          ) : catalogMessages.length === 0 ? (
            <div className="py-4 text-center text-xs text-muted-foreground">No active catalog messages.</div>
          ) : (
            <div className="space-y-2">
              {catalogMessages.map((catalog) => (
                <button
                  type="button"
                  key={catalog.id}
                  onClick={() => handleSendCatalogMessage(catalog.id)}
                  disabled={!selectedIncident || catalogSendingId != null}
                  className="w-full rounded-md border border-border bg-muted p-3 text-left transition-colors hover:border-[#1F91A8]/40 hover:bg-[#1F91A8]/5 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <div className="mb-1 flex items-center justify-between gap-2">
                    <span className="truncate font-mono text-[10px] font-bold text-[#1F91A8]">{catalog.code}</span>
                    {catalogSendingId === catalog.id ? (
                      <Loader2 className="h-3 w-3 animate-spin text-[#1F91A8]" />
                    ) : (
                      <Badge variant="outline" className="text-[9px]">{catalog.message_type}</Badge>
                    )}
                  </div>
                  <div className="text-[11px] leading-relaxed text-foreground">{catalog.message_en}</div>
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
