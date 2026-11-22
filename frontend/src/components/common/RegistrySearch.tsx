import { Search, X } from 'lucide-react';
import { Input } from '@/components/ui/input';

interface RegistrySearchProps {
  label: string;
  placeholder: string;
  value: string;
  onChange: (value: string) => void;
}

export function RegistrySearch({ label, placeholder, value, onChange }: RegistrySearchProps) {
  return (
    <div className="workspace-search relative w-full min-w-0 sm:w-80">
      <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
      <Input
        type="search"
        aria-label={label}
        placeholder={placeholder}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="h-control border-border bg-card pl-9 pr-10"
      />
      {value && (
        <button type="button" onClick={() => onChange('')} aria-label={`Clear ${label.toLowerCase()}`} className="absolute right-1 top-1/2 grid size-9 -translate-y-1/2 place-items-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground">
          <X className="size-4" aria-hidden="true" />
        </button>
      )}
    </div>
  );
}
