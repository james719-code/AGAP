import { Search } from 'lucide-react';
import { Input } from '@/components/ui/input';

interface RegistrySearchProps {
  label: string;
  placeholder: string;
  value: string;
  onChange: (value: string) => void;
}

export function RegistrySearch({ label, placeholder, value, onChange }: RegistrySearchProps) {
  return (
    <div className="relative w-full min-w-0 sm:w-72">
      <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
      <Input
        type="search"
        aria-label={label}
        placeholder={placeholder}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="h-control border-border bg-card pl-9"
      />
    </div>
  );
}
