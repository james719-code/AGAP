import * as React from "react"
import { cn } from "@/lib/utils"

export interface TooltipProps {
  content: React.ReactNode
  children: React.ReactNode
  side?: "top" | "bottom" | "left" | "right"
  className?: string
  wrapperClassName?: string
}

export function Tooltip({ content, children, side = "bottom", className, wrapperClassName }: TooltipProps) {
  const [visible, setVisible] = React.useState(false)

  if (!content) return <>{children}</>;

  const sideClasses = {
    top: "bottom-full mb-2 left-1/2 -translate-x-1/2",
    bottom: "top-full mt-2 left-1/2 -translate-x-1/2",
    left: "right-full mr-2 top-1/2 -translate-y-1/2",
    right: "left-full ml-2 top-1/2 -translate-y-1/2",
  }

  return (
    <div
      className={cn("relative inline-flex", wrapperClassName)}
      onMouseEnter={() => setVisible(true)}
      onMouseLeave={() => setVisible(false)}
      onFocusCapture={() => setVisible(true)}
      onBlurCapture={() => setVisible(false)}
    >
      {children}
      {visible && (
        <div
          className={cn(
            "absolute z-[999] whitespace-nowrap rounded-[2px] border border-border bg-foreground px-2.5 py-1 text-xs font-normal text-background shadow-md animate-in fade-in zoom-in-95 pointer-events-none font-sans",
            sideClasses[side],
            className
          )}
        >
          {content}
        </div>
      )}
    </div>
  )
}
