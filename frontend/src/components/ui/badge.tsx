import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

const badgeVariants = cva(
  "inline-flex items-center rounded-md border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
  {
    variants: {
      variant: {
        default:
          "border-transparent bg-primary text-primary-foreground hover:bg-primary/90",
        secondary:
          "border-transparent bg-secondary text-secondary-foreground hover:bg-secondary/90",
        destructive:
          "border-transparent bg-destructive/15 text-destructive hover:bg-destructive/25 dark:bg-destructive/25 dark:text-red-300",
        outline: "text-foreground border-border",
        success: "border-transparent bg-emerald-500/15 text-emerald-700 hover:bg-emerald-500/25 dark:text-emerald-300",
        warning: "border-transparent bg-amber-500/15 text-amber-700 hover:bg-amber-500/25 dark:text-amber-300",
        info: "border-transparent bg-[#1F91A8]/15 text-[#1F91A8] hover:bg-[#1F91A8]/25 dark:text-[#4FB4C9]",
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