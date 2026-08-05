import React, { useState } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Users, Send, Mic, AlertTriangle, Info } from 'lucide-react';

interface Contact {
  id: string;
  name: string;
  role: string;
  status: 'online' | 'offline' | 'active';
  lastMsg: string;
}

interface Message {
  id: string;
  sender: string;
  text: string;
  time: string;
  isSelf: boolean;
}

export const ChatPage: React.FC = () => {
  const [contacts] = useState<Contact[]>([
    { id: 'c1', name: 'Incident Commander', role: 'HQ Control', status: 'online', lastMsg: 'Telemetry verified for Zone 4.' },
    { id: 'c2', name: 'Field Operator A', role: 'Legazpi Patrol', status: 'active', lastMsg: 'Moving towards Maharlika Junction.' },
    { id: 'c3', name: 'DRRMO Base', role: 'Central Ops', status: 'online', lastMsg: 'Medical team dispatched.' },
    { id: 'c4', name: 'Medical Unit 2', role: 'Ambulance Fleet', status: 'offline', lastMsg: 'En route to Daraga border.' },
  ]);

  const [activeContact, setActiveContact] = useState<Contact>(contacts[0]);
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'm1',
      sender: 'Incident Commander',
      text: 'Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.',
      time: '10:02 AM',
      isSelf: false,
    },
    {
      id: 'm2',
      sender: 'AgapUser',
      text: 'Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.',
      time: '10:05 AM',
      isSelf: true,
    },
    {
      id: 'm3',
      sender: 'Field Operator A',
      text: 'Packet hop verified via BLE store-and-forward mesh.',
      time: '10:08 AM',
      isSelf: false,
    },
  ]);

  const [inputText, setInputText] = useState('');

  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputText.trim()) return;

    const newMsg: Message = {
      id: `m-${Date.now()}`,
      sender: 'AgapUser',
      text: inputText,
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      isSelf: true,
    };

    setMessages([...messages, newMsg]);
    setInputText('');
  };

  return (
    <div className="h-[calc(100vh-6rem)] w-full grid grid-cols-1 lg:grid-cols-12 gap-4 font-sans">
      {/* Column 1: Active Contacts Sidebar */}
      <div className="lg:col-span-3 bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 rounded-2xl p-4 flex flex-col space-y-4 shadow-sm">
        <Button variant="outline" className="w-full justify-start gap-2 h-10 border-slate-300 dark:border-slate-800 font-semibold">
          <Users className="h-4 w-4 text-teal-600 dark:text-teal-400" />
          <span>Contacts</span>
        </Button>

        <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400 px-1">
          ACTIVE CONTACTS
        </div>

        <ScrollArea className="flex-1 pr-1">
          <div className="space-y-2">
            {contacts.map((contact) => (
              <div
                key={contact.id}
                onClick={() => setActiveContact(contact)}
                className={`p-3 rounded-xl border transition-all cursor-pointer flex items-center gap-3 ${
                  activeContact.id === contact.id
                    ? 'bg-teal-50 dark:bg-teal-950/40 border-teal-500/40 shadow-sm'
                    : 'border-slate-200 dark:border-slate-800/80 hover:bg-slate-50 dark:hover:bg-slate-800/50'
                }`}
              >
                <div className="relative">
                  <div className="w-10 h-10 rounded-full bg-slate-200 dark:bg-slate-800 flex items-center justify-center font-bold text-slate-700 dark:text-slate-300 text-xs">
                    {contact.name.charAt(0)}
                  </div>
                  <span
                    className={`absolute bottom-0 right-0 h-3 w-3 rounded-full border-2 border-white dark:border-slate-900 ${
                      contact.status === 'online'
                        ? 'bg-emerald-500'
                        : contact.status === 'active'
                        ? 'bg-teal-500 animate-pulse'
                        : 'bg-slate-400'
                    }`}
                  />
                </div>
                <div className="flex-1 overflow-hidden">
                  <div className="text-xs font-bold text-slate-900 dark:text-slate-100 truncate">{contact.name}</div>
                  <div className="text-[11px] text-slate-500 dark:text-slate-400 truncate">{contact.lastMsg}</div>
                </div>
              </div>
            ))}
          </div>
        </ScrollArea>
      </div>

      {/* Column 2: Main Live Chat Panel */}
      <div className="lg:col-span-6 bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 rounded-2xl p-4 flex flex-col justify-between shadow-sm">
        {/* Chat Header */}
        <div className="pb-3 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-full bg-teal-500/10 text-teal-600 dark:text-teal-400 flex items-center justify-center font-bold text-xs">
              {activeContact.name.charAt(0)}
            </div>
            <div>
              <h3 className="text-sm font-bold text-slate-900 dark:text-slate-100">{activeContact.name}</h3>
              <p className="text-[11px] text-slate-400 font-mono">{activeContact.role} · 10:02 AM</p>
            </div>
          </div>
          <Badge variant="outline" className="text-[10px] font-mono border-teal-500/30 text-teal-600 dark:text-teal-400">
            ENCRYPTED BLE
          </Badge>
        </div>

        {/* Message Stream */}
        <ScrollArea className="flex-1 py-4 px-2 space-y-4">
          <div className="space-y-4">
            {messages.map((msg) => (
              <div
                key={msg.id}
                className={`flex flex-col ${msg.isSelf ? 'items-end' : 'items-start'}`}
              >
                <div className="text-[10px] text-slate-400 mb-1 px-1">{msg.sender} — {msg.time}</div>
                <div
                  className={`max-w-md p-4 rounded-2xl text-xs leading-relaxed ${
                    msg.isSelf
                      ? 'bg-teal-700 text-white rounded-br-none shadow-md'
                      : 'bg-slate-100 dark:bg-slate-800 text-slate-900 dark:text-slate-100 rounded-bl-none border border-slate-200 dark:border-slate-700'
                  }`}
                >
                  {msg.text}
                </div>
              </div>
            ))}
          </div>
        </ScrollArea>

        {/* Chat Input Form */}
        <form onSubmit={handleSendMessage} className="pt-3 border-t border-slate-200 dark:border-slate-800">
          <div className="relative flex items-center bg-slate-50 dark:bg-slate-950 rounded-2xl border border-slate-200 dark:border-slate-800 p-2">
            <button type="button" className="p-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200">
              <Mic className="h-4 w-4" />
            </button>
            <Input
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              placeholder="Type your message here..."
              className="border-0 bg-transparent focus-visible:ring-0 text-xs text-slate-900 dark:text-slate-100 placeholder:text-slate-400"
            />
            <Button type="submit" size="sm" className="bg-amber-600 hover:bg-amber-500 text-white rounded-xl h-8 px-3 ml-2">
              <Send className="h-3.5 w-3.5" />
            </Button>
          </div>
        </form>
      </div>

      {/* Column 3: Disaster Context Panel (Matching Mockup Screenshot) */}
      <div className="lg:col-span-3 bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 rounded-2xl p-4 flex flex-col space-y-4 shadow-sm">
        <div className="flex items-center gap-2 pb-2 border-b border-slate-200 dark:border-slate-800">
          <Info className="h-4 w-4 text-teal-600 dark:text-teal-400" />
          <h3 className="text-sm font-bold text-slate-900 dark:text-slate-100">Disaster Context</h3>
        </div>

        {/* Resource Allocation Section */}
        <div className="space-y-2">
          <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
            RESOURCE ALLOCATION
          </div>
          <Card className="bg-slate-50 dark:bg-slate-950 border-slate-200 dark:border-slate-800 p-3 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-amber-600 dark:text-amber-400 flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-amber-500" /> Rescue Team A - Sector 4
              </span>
            </div>
            <p className="text-[11px] text-slate-500 dark:text-slate-400">Last updated: 35s ago</p>
          </Card>

          <div className="flex flex-wrap gap-1.5 pt-1">
            <Badge className="bg-amber-500/20 text-amber-700 dark:text-amber-300 border-0 text-[10px]">Rescue Team A</Badge>
            <Badge className="bg-teal-500/20 text-teal-700 dark:text-teal-300 border-0 text-[10px]">Medical Unit 2</Badge>
            <Badge className="bg-sky-500/20 text-sky-700 dark:text-sky-300 border-0 text-[10px]">Aerial Drone</Badge>
          </div>
        </div>

        {/* Situation Reports Section */}
        <div className="space-y-2 pt-2 border-t border-slate-200 dark:border-slate-800">
          <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
            SITUATION REPORTS
          </div>
          <Card className="bg-slate-50 dark:bg-slate-950 border-slate-200 dark:border-slate-800 p-3 space-y-1.5">
            <div className="text-xs font-bold text-red-500 dark:text-red-400 flex items-center gap-1.5">
              <AlertTriangle className="h-3.5 w-3.5" /> Levee Breach Warning
            </div>
            <p className="text-[11px] text-slate-600 dark:text-slate-300 leading-relaxed">
              Structural integrity of the North levee is at 60%. Evacuation of lower zone advised.
            </p>
          </Card>
        </div>
      </div>
    </div>
  );
};
