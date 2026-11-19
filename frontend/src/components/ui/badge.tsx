import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

const badgeVariants = cva(
  "inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
  {
    variants: {
      variant: {
        default:
          "border-transparent bg-primary text-primary-foreground hover:bg-primary/90",
        secondary:
          "border-transparent bg-secondary text-secondary-foreground hover:bg-secondary/90",
        destructive: "border-transparent bg-[var(--status-danger-bg)] text-[var(--status-danger)]",
        outline: "text-foreground border-border",
        success: "border-transparent bg-[var(--status-success-bg)] text-[var(--status-success)]",
        warning: "border-transparent bg-[var(--status-warning-bg)] text-[var(--status-warning)]",
        info: "border-transparent bg-[var(--status-info-bg)] text-[var(--status-info)]",
        purple: "border-transparent bg-violet-500/15 text-violet-700 hover:bg-violet-500/25 dark:text-violet-300",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return (
    <div className={cn(badgeVariants({ variant }), className)} {...props} />
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export { Badge, badgeVariants }
