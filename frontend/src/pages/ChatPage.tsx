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
    <div className="h-[calc(100vh-6rem)] w-full grid grid-cols-1 lg:grid-cols-12 gap-4">
      {/* Column 1: Active Contacts Sidebar */}
      <div className="lg:col-span-3 bg-card border border-border rounded-lg p-4 flex flex-col space-y-4 shadow-sm">
        <Button variant="outline" className="w-full justify-start gap-2 h-10 font-semibold">
          <Users className="h-4 w-4 text-secondary" />
          <span>Contacts</span>
        </Button>

        <div className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground px-1">
          ACTIVE CONTACTS
        </div>

        <ScrollArea className="flex-1 pr-1">
          <div className="space-y-2">
            {contacts.map((contact) => (
              <div
                key={contact.id}
                onClick={() => setActiveContact(contact)}
                className={`p-3 rounded-lg border transition-all cursor-pointer flex items-center gap-3 ${
                  activeContact.id === contact.id
                    ? 'bg-secondary/10 border-secondary/40 shadow-sm'
                    : 'border-border hover:bg-muted'
                }`}
              >
                <div className="relative">
                  <div className="w-10 h-10 rounded-full bg-muted border border-border flex items-center justify-center font-bold text-muted-foreground text-xs">
                    {contact.name.charAt(0)}
                  </div>
                  <span
                    className={`absolute bottom-0 right-0 h-3 w-3 rounded-full border-2 border-background ${
                      contact.status === 'online'
                        ? 'bg-emerald-500'
                        : contact.status === 'active'
                        ? 'bg-[#1F91A8] animate-pulse'
                        : 'bg-muted-foreground/40'
                    }`}
                  />
                </div>
                <div className="flex-1 overflow-hidden">
                  <div className="text-xs font-bold text-card-foreground truncate">{contact.name}</div>
                  <div className="text-[11px] text-muted-foreground truncate">{contact.lastMsg}</div>
                </div>
              </div>
            ))}
          </div>
        </ScrollArea>
      </div>

      {/* Column 2: Main Live Chat Panel */}
      <div className="lg:col-span-6 bg-card border border-border rounded-lg p-4 flex flex-col justify-between shadow-sm">
        {/* Chat Header */}
        <div className="pb-3 border-b border-border flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-full bg-secondary/10 text-[#1F91A8] flex items-center justify-center font-bold text-xs">
              {activeContact.name.charAt(0)}
            </div>
            <div>
              <h3 className="text-sm font-bold text-card-foreground">{activeContact.name}</h3>
              <p className="text-[11px] text-muted-foreground">{activeContact.role} · 10:02 AM</p>
            </div>
          </div>
          <Badge variant="outline" className="text-[10px] font-medium text-secondary border-secondary/30">
            ENCRYPTED BLE
          </Badge>
        </div>

        {/* Message Stream */}
        <ScrollArea className="flex-1 py-4 px-2">
          <div className="space-y-4">
            {messages.map((msg) => (
              <div
                key={msg.id}
                className={`flex flex-col ${msg.isSelf ? 'items-end' : 'items-start'}`}
              >
                <div className="text-[10px] text-muted-foreground mb-1 px-1">{msg.sender} — {msg.time}</div>
                <div
                  className={`max-w-md p-4 rounded-lg text-xs leading-relaxed shadow-sm ${
                    msg.isSelf
                      ? 'bg-[#1F91A8] text-white rounded-br-none'
                      : 'bg-muted text-card-foreground rounded-bl-none border border-border'
                  }`}
                >
                  {msg.text}
                </div>
              </div>
            ))}
          </div>
        </ScrollArea>

        {/* Chat Input Form */}
        <form onSubmit={handleSendMessage} className="pt-3 border-t border-border">
          <div className="relative flex items-center bg-muted rounded-lg border border-border p-2">
            <button type="button" className="p-2 text-muted-foreground hover:text-foreground">
              <Mic className="h-4 w-4" />
            </button>
            <Input
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              placeholder="Type your message here..."
              className="border-0 bg-transparent focus-visible:ring-0 text-sm placeholder:text-muted-foreground"
            />
            <Button type="submit" size="sm" className="bg-primary hover:bg-primary/90 text-white rounded-md h-8 px-3 ml-2">
              <Send className="h-3.5 w-3.5" />
            </Button>
          </div>
        </form>
      </div>

      {/* Column 3: Disaster Context Panel */}
      <div className="lg:col-span-3 bg-card border border-border rounded-lg p-4 flex flex-col space-y-4 shadow-sm">
        <div className="flex items-center gap-2 pb-2 border-b border-border">
          <Info className="h-4 w-4 text-[#1F91A8]" />
          <h3 className="text-sm font-bold text-card-foreground">Disaster Context</h3>
        </div>

        {/* Resource Allocation Section */}
        <div className="space-y-2">
          <div className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
            RESOURCE ALLOCATION
          </div>
          <Card className="bg-muted border-border p-3 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-[#E8902D] flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-[#F2A540]" /> Rescue Team A - Sector 4
              </span>
            </div>
            <p className="text-[11px] text-muted-foreground">Last updated: 35s ago</p>
          </Card>

          <div className="flex flex-wrap gap-1.5 pt-1">
            <Badge className="bg-[#F2A540]/15 text-[#B97A1F] dark:text-[#F2A540] border-0 text-[10px]">Rescue Team A</Badge>
            <Badge className="bg-secondary/15 text-[#1F91A8] dark:text-[#4FB4C9] border-0 text-[10px]">Medical Unit 2</Badge>
            <Badge className="bg-violet-500/15 text-violet-700 dark:text-violet-300 border-0 text-[10px]">Aerial Drone</Badge>
          </div>
        </div>

        {/* Situation Reports Section */}
        <div className="space-y-2 pt-2 border-t border-border">
          <div className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
            SITUATION REPORTS
          </div>
          <Card className="bg-muted border-border p-3 space-y-1.5">
            <div className="text-xs font-bold text-red-600 dark:text-red-400 flex items-center gap-1.5">
              <AlertTriangle className="h-3.5 w-3.5" /> Levee Breach Warning
            </div>
            <p className="text-[11px] text-muted-foreground leading-relaxed">
              Structural integrity of the North levee is at 60%. Evacuation of lower zone advised.
            </p>
          </Card>
        </div>
      </div>
    </div>
  );
};

