import { Moon, Sun } from "lucide-react";
import { useTheme } from "@/context/ThemeContext";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Tooltip } from "@/components/ui/tooltip";

interface ThemeToggleProps {
  className?: string;
  lightIconClassName?: string;
  darkIconClassName?: string;
  side?: "top" | "right" | "bottom" | "left";
}

export function ThemeToggle({
  className,
  lightIconClassName,
  darkIconClassName,
  side = "bottom",
}: ThemeToggleProps) {
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === "dark";

  return (
    <Tooltip
      content={isDark ? "Switch to Light Mode" : "Switch to Dark Mode"}
      side={side}
    >
      <Button
        type="button"
        onClick={toggleTheme}
        variant="outline"
        size="sm"
        aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
        className={cn("h-9 w-9 p-0 rounded-md", className)}
      >
        {isDark ? (
          <Sun className={cn("h-4 w-4", lightIconClassName)} />
        ) : (
          <Moon className={cn("h-4 w-4", darkIconClassName)} />
        )}
      </Button>
    </Tooltip>
  );
}
