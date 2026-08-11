import { ArrowUpRight, Smartphone } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';

interface DownloadDialogProps { open: boolean; onOpenChange: (open: boolean) => void }

export function DownloadDialog({ open, onOpenChange }: DownloadDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <div className="space-y-5 py-1">
        <div className="flex items-center gap-3">
          <div className="h-11 w-11 rounded-sm bg-[#D15033]/10 dark:bg-[#D15033]/15 flex items-center justify-center shrink-0"><Smartphone className="h-5 w-5 text-[#D15033]" /></div>
          <div>
            <h3 className="text-base font-semibold text-[#0A0A0A] dark:text-white flex items-center gap-2">AGAP for Android<Badge className="rounded-sm bg-[#F2A540] text-[#0A0A0A] border-0 text-[10px] font-semibold">Coming soon</Badge></h3>
            <p className="text-xs text-[#6B6864] dark:text-[#9C9994] mt-0.5">Offline emergency communication app</p>
          </div>
        </div>
        <div className="rounded-sm border border-[#E7E3DC] dark:border-[#26292C] text-xs text-[#0A0A0A] dark:text-[#F2F0EC] overflow-hidden">
          {[
            ['Target OS', 'Android 9.0+ (API 28)'],
            ['Required hardware', 'Bluetooth 5.0 (extended advertising)'],
            ['Offline transfer', 'Wi-Fi Direct and Bluetooth'],
            ['Status', 'Testing'],
          ].map(([label, value], index) => (
            <div key={label} className={`flex items-center justify-between px-4 py-2.5 ${index < 3 ? 'border-b border-[#E7E3DC] dark:border-[#26292C]' : ''} ${index % 2 === 0 ? 'bg-[#F7F5F2] dark:bg-[#181C1F]' : ''}`}>
              <span className="text-[#6B6864] dark:text-[#9C9994]">{label}</span><span className={`font-medium ${label === 'Status' ? 'text-[#D15033]' : ''}`}>{value}</span>
            </div>
          ))}
        </div>
        <p className="text-xs text-[#4B4946] dark:text-[#B9B6B0] leading-relaxed">The Android app is currently being tested. You can explore the dispatch interface in the meantime.</p>
        <div className="flex items-center justify-end gap-3 pt-1">
          <Button variant="outline" className="rounded-sm" onClick={() => onOpenChange(false)}>Close</Button>
          <Link to="/dashboard" onClick={() => onOpenChange(false)}><Button className="rounded-sm bg-[#D15033] hover:bg-[#B5432A] text-white font-semibold flex items-center gap-2">Open Dashboard<ArrowUpRight className="h-4 w-4" /></Button></Link>
        </div>
      </div>
    </Dialog>
  );
}
