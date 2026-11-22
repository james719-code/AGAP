import React, { useEffect, useMemo, useRef, useState } from 'react';
import { BookOpenText, Info, Loader2, MessageSquare, Search, Send, Users } from 'lucide-react';
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
import { incidentStatusLabel, incidentStatusTone } from '@/lib/incidentPresentation';
import { messagesForIncident } from '@/lib/workspaceSelection';

const THREAD_SEVERITY: Record<string, string> = {
  'tone-danger': 'var(--sev-critical)',
  'tone-warning': 'var(--sev-moderate)',
  'tone-info': 'var(--sev-info)',
  'tone-success': 'var(--sev-resolved)',
};

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
  const [threadSearch, setThreadSearch] = useState('');
  const [messages, setMessages] = useState<IncidentMessage[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [messageError, setMessageError] = useState<string | null>(null);
  const [drafts, setDrafts] = useState<Record<number, string>>({});
  const [sending, setSending] = useState(false);
  const [catalogSendingId, setCatalogSendingId] = useState<number | null>(null);
  const messagesRef = useRef<IncidentMessage[]>([]);
  const messageViewportRef = useRef<HTMLDivElement>(null);
  const conversationPanelRef = useRef<HTMLDivElement>(null);
  const conversationRef = useRef<number | undefined>(undefined);

  useEffect(() => {
    messagesRef.current = messages;
    const viewport = messageViewportRef.current;
    if (viewport && viewport.scrollHeight - viewport.scrollTop - viewport.clientHeight < 180) {
      viewport.scrollTop = viewport.scrollHeight;
    }
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
    () => activeIncidents.filter((incident) => {
      if (selectedType !== 'ALL' && incident.incident_type !== selectedType) return false;
      const query = threadSearch.trim().toLowerCase();
      if (!query) return true;
      const location = formatTargetLocation(incident.target_id, incident.latitude, incident.longitude).readable;
      return `${incident.id} ${incident.incident_type} ${location}`.toLowerCase().includes(query);
    }),
    [activeIncidents, selectedType, threadSearch]
  );

  const selectedIncident = useMemo<Incident | null>(() => {
    if (selectedIncidentId == null) return null;
    return activeIncidents.find((incident) => incident.id === selectedIncidentId) || null;
  }, [activeIncidents, selectedIncidentId]);
  const conversationId = selectedIncident?.id;
  const inputText = conversationId == null ? '' : drafts[conversationId] ?? '';
  const visibleMessages = messagesForIncident(messages, conversationId);
  const setInputText = (value: string) => {
    if (conversationId != null) setDrafts((previous) => ({ ...previous, [conversationId]: value }));
  };

  React.useLayoutEffect(() => {
    conversationRef.current = conversationId;
  }, [conversationId]);

  useEffect(() => {
    if (conversationId == null) {
      queueMicrotask(() => { setMessages([]); setMessagesLoading(false); setMessageError(null); });
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
          if (isInitial) requestAnimationFrame(() => {
            if (conversationRef.current === conversationId && messageViewportRef.current) {
              messageViewportRef.current.scrollTop = messageViewportRef.current.scrollHeight;
            }
          });
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
    if (!selectedIncident || !inputText.trim() || sending) return;
    const targetId = selectedIncident.id;

    setSending(true);
    setMessageError(null);
    try {
      const created = await apiService.createIncidentMessage(targetId, {
        custom_message: inputText.trim(),
        channel: 'ONLINE',
      });
      if (conversationRef.current === targetId) setMessages((prev) => prev.some((message) => message.id === created.id) ? prev : [...prev, created]);
      setDrafts((previous) => ({ ...previous, [targetId]: '' }));
    } catch (err) {
      if (conversationRef.current === targetId) setMessageError(err instanceof Error ? err.message : 'Failed to send incident message');
    } finally {
      setSending(false);
    }
  };

  const handleSendCatalogMessage = async (catalogId: number) => {
    if (!selectedIncident || catalogSendingId != null) return;
    const targetId = selectedIncident.id;
    setCatalogSendingId(catalogId);
    setMessageError(null);
    try {
      const created = await apiService.createIncidentMessage(targetId, {
        predefined_message_id: catalogId,
        channel: 'ONLINE',
      });
      if (conversationRef.current === targetId) setMessages((prev) => prev.some((message) => message.id === created.id) ? prev : [...prev, created]);
    } catch (err) {
      if (conversationRef.current === targetId) setMessageError(err instanceof Error ? err.message : 'Failed to send catalog message');
    } finally {
      setCatalogSendingId(null);
    }
  };

  return (
    <div className="chat-stage">
      <h1 className="sr-only">Incident Chat</h1>
      <div className="chat-pane chat-pane-threads flex min-w-0 flex-col gap-3 p-3">
        <div className="flex items-center justify-between gap-2 px-1 pt-1">
          <h2 className="flex items-center gap-2 text-sm font-bold">
            <Users className="size-4 text-primary" aria-hidden="true" /> Threads
            <span className="rounded-md bg-muted px-1.5 py-0.5 font-mono text-[0.6875rem] text-muted-foreground">{activeIncidents.length}</span>
          </h2>
          <span className={`status-pill gap-1.5 before:hidden ${isConnected || usesHostedApi ? 'tone-success' : ''}`}>
            <span className={`live-dot ${isConnected || usesHostedApi ? 'live-dot-ok' : 'live-dot-idle'}`} aria-hidden="true" />
            {usesHostedApi ? 'Auto-refresh' : isConnected ? 'Live' : 'Connecting'}
          </span>
        </div>

        <label className="relative flex items-center">
          <Search className="pointer-events-none absolute left-3 size-4 text-muted-foreground" aria-hidden="true" />
          <input type="search" aria-label="Search incident threads" value={threadSearch} onChange={(event) => setThreadSearch(event.target.value)} placeholder="Search ID, type, or location" className="h-9 w-full min-w-0 rounded-lg border border-transparent bg-muted pl-9 pr-3 text-[0.8125rem]" />
        </label>

        <div className="status-chips -mx-3 flex gap-1.5 overflow-x-auto px-3" role="group" aria-label="Filter incident threads by emergency type">
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
                className={`status-chip ${selectedType === type ? 'status-chip-active' : ''}`}
              >
                {type === 'ALL' ? 'All' : incidentStatusLabel(type)} <span className="font-mono opacity-70">{count}</span>
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
              <p>{threadSearch.trim() ? 'No matching incident threads.' : selectedType === 'ALL' ? 'No active incidents.' : 'No active incidents of this type.'}</p>
              {(selectedType !== 'ALL' || threadSearch) && <Button type="button" variant="link" onClick={() => { setSelectedType('ALL'); setThreadSearch(''); }}>Clear filters</Button>}
            </div>
          ) : (
            <div className="space-y-0.5">
              {filteredActiveIncidents.map((incident) => (
                <button
                  type="button"
                  key={incident.id}
                  onClick={() => {
                    setSelectedIncidentId(incident.id);
                    if (window.matchMedia('(max-width: 767px)').matches) {
                      conversationPanelRef.current?.scrollIntoView({ behavior: 'instant', block: 'start' });
                    }
                  }}
                  aria-pressed={selectedIncident?.id === incident.id}
                  className={`thread-item ${selectedIncident?.id === incident.id ? 'thread-item-active' : ''}`}
                  style={{ ['--thread-sev' as string]: THREAD_SEVERITY[incidentStatusTone(incident.status)] ?? 'transparent' }}
                >
                  <div className="min-w-0">
                    <div className="flex items-center justify-between gap-2">
                      <div className="text-sm font-bold text-foreground truncate">{incidentStatusLabel(incident.incident_type)}</div>
                      <span className="text-xs tabular-nums text-muted-foreground">#{incident.id}</span>
                    </div>
                    <div className="mt-0.5 truncate text-xs text-muted-foreground">
                      {formatTargetLocation(incident.target_id, incident.latitude, incident.longitude).readable}
                    </div>
                    <div className="mt-2 flex flex-wrap items-center justify-between gap-2"><span className={`status-pill ${incidentStatusTone(incident.status)}`}>{incidentStatusLabel(incident.status)}</span><time dateTime={incident.received_at} title={formatTime(incident.received_at)} className="text-xs tabular-nums text-muted-foreground">{new Date(incident.received_at).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</time></div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </ScrollArea>
      </div>

      <div ref={conversationPanelRef} className="chat-pane chat-pane-conversation scroll-mt-20 flex min-w-0 flex-col">
        <div className="flex min-h-14 items-center justify-between gap-3 border-b border-border px-4 py-2.5">
          <div className="flex items-center gap-3 min-w-0">
            <div className="tone-info size-9 shrink-0 rounded-xl flex items-center justify-center">
              <MessageSquare className="h-4 w-4" />
            </div>
            <div className="min-w-0">
              <h3 className="text-sm font-bold text-foreground truncate">
                {selectedIncident ? `Incident #${selectedIncident.id}` : 'Conversation'}
              </h3>
              <p className="text-[11px] text-muted-foreground truncate">
                {selectedIncident ? `${incidentStatusLabel(selectedIncident.incident_type)} · ${incidentStatusLabel(selectedIncident.status)}` : 'Select an incident thread'}
              </p>
            </div>
          </div>
          {selectedIncident && <span className={`status-pill ${incidentStatusTone(selectedIncident.status)}`}>{incidentStatusLabel(selectedIncident.status)}</span>}
        </div>

        <ScrollArea ref={messageViewportRef} className="chat-messages min-h-0 flex-1 px-4 py-5" aria-label="Incident conversation">
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
          ) : visibleMessages.length === 0 ? (
            <div className="flex min-h-44 flex-col items-center justify-center gap-3 px-5 py-8 text-center text-sm text-muted-foreground">
              <span className="grid size-12 place-items-center rounded-xl bg-muted text-muted-foreground"><MessageSquare className="size-6" aria-hidden="true" /></span>
              <p className="font-semibold text-foreground">{selectedIncident ? 'No messages yet' : 'No conversation selected'}</p>
              <p>{selectedIncident ? 'Send an update below to start the conversation.' : 'Choose an incident thread to see its messages.'}</p>
            </div>
          ) : (
            <div className="space-y-4">
              {visibleMessages.map((message) => {
                // The mobile application presents catalog/status messages as
                // centered system notices and operational chat as neutral,
                // left-aligned bubbles. Keep the web conversation consistent.
                const isSystem = message.message_type === 'STATUS_UPDATE';
                const isSelf = user != null && String(message.sender_user_id) === String(user.id);
                return (
                  <div key={message.id} className={`flex flex-col ${isSystem ? 'items-center' : isSelf ? 'items-end' : 'items-start'}`}>
                    {!isSystem && (
                      <div className="text-xs text-muted-foreground mb-1 px-1">
                        {isSelf ? 'You' : message.sender_name || 'Unknown sender'}
                        {message.sender_role ? ` · ${message.sender_role}` : ''} · {formatTime(message.sent_at)}
                      </div>
                    )}
                    <div
                      className={`max-w-[85%] wrap-anywhere whitespace-pre-wrap text-sm leading-relaxed ${
                        isSystem
                          ? 'rounded-full border border-border bg-muted px-3 py-1 text-center text-xs font-semibold text-muted-foreground'
                          : isSelf
                            ? 'rounded-2xl rounded-br-md bg-primary px-4 py-2.5 text-primary-foreground'
                            : 'rounded-2xl rounded-bl-md border border-border bg-muted px-4 py-2.5 text-foreground'
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

        <form onSubmit={handleSendMessage} className="border-t border-border p-3">
          <div className="relative flex items-end gap-2 rounded-2xl border border-border bg-background p-1.5 focus-within:border-primary">
            <textarea
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
                  e.preventDefault();
                  e.currentTarget.form?.requestSubmit();
                }
              }}
              aria-label="Message"
              placeholder={selectedIncident ? 'Type an operational message...' : 'Select an incident first'}
              disabled={!selectedIncident || sending}
              rows={1}
              className="min-h-10 min-w-0 max-h-24 flex-1 resize-none border-0 bg-transparent px-2.5 py-2.5 text-sm outline-none placeholder:text-muted-foreground disabled:opacity-50"
            />
            <Button
              type="submit"
              aria-label="Send message"
              size="sm"
              disabled={!selectedIncident || sending || !inputText.trim()}
              className="size-10 shrink-0 rounded-xl p-0"
            >
              {sending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Send className="h-3.5 w-3.5" />}
            </Button>
          </div>
          <p className="mt-1.5 px-1 text-[0.6875rem] text-muted-foreground">Enter to send · Shift + Enter for a new line</p>
        </form>
      </div>

      <div className="chat-pane chat-pane-context flex min-w-0 flex-col gap-3 p-4">
        <div className="flex items-center gap-2 pb-2 border-b border-border">
          <Info className="h-4 w-4 text-[var(--status-info)]" />
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
                <Badge variant="info" className="text-xs">{selectedIncident.channel === 'BLE_MESH' ? 'Bluetooth mesh' : 'Online'}</Badge>
                <Badge variant="outline" className="text-xs">{selectedIncident.delivery_path === 'BLE_CLOUD_SHORTCUT' ? 'Cloud shortcut' : 'Direct uplink'}</Badge>
              </div>
              <p className="text-[11px] text-muted-foreground">
                Received {formatTime(selectedIncident.received_at)}
              </p>
            </div>
          </>
        ) : (
          <div className="border-t border-border py-3 space-y-2">
            <div className="text-sm font-semibold text-muted-foreground flex items-center gap-1.5">
              <Info className="h-3.5 w-3.5" /> No incident selected
            </div>
            <p className="text-[11px] text-muted-foreground leading-relaxed">
              Select an incident thread to review operational messages.
            </p>
          </div>
        )}

        <div className="flex items-center gap-2 pt-2 pb-2 border-b border-border">
          <BookOpenText className="h-4 w-4 text-[var(--status-info)]" />
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
                  className="w-full rounded-lg border border-border p-3 text-left transition-colors hover:border-primary/50 hover:bg-accent disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <div className="mb-1 flex items-center justify-between gap-2">
                    <span className="truncate font-mono text-xs font-semibold text-[var(--status-info)]">{catalog.code}</span>
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
  );
};
