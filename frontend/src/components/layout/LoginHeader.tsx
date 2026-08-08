import { Globe, Shield } from "lucide-react";
import { Link } from "react-router-dom";
import { ThemeToggle } from "@/components/common/ThemeToggle";
import { Button } from "@/components/ui/button";

export function LoginHeader() {
  return (
    <header className="flex items-center justify-between w-full pb-6">
      <div className="lg:hidden flex items-center gap-2">
        <Shield className="h-6 w-6 text-red-500" />
        <span className="text-lg font-bold text-slate-900 dark:text-slate-100">AGAP</span>
      </div>
      <div className="flex items-center gap-3 ml-auto">
        <ThemeToggle
          className="rounded-lg border-slate-300 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-300 hover:text-slate-900 dark:hover:text-slate-100 shadow-sm"
          lightIconClassName="text-amber-400"
          darkIconClassName="text-sky-500"
        />
        <Link to="/">
          <Button variant="outline" size="sm" className="h-9 border-slate-300 dark:border-slate-800 bg-white dark:bg-slate-900 text-xs font-medium gap-1.5 shadow-sm">
            <Globe className="h-3.5 w-3.5" /> Landing Page
          </Button>
        </Link>
      </div>
    </header>
  );
}
