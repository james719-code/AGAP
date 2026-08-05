import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

const badgeVariants = cva(
  "inline-flex items-center rounded-md border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
  {
    variants: {
      variant: {
        default:
          "border-transparent bg-slate-800 text-slate-100 hover:bg-slate-700",
        secondary:
          "border-transparent bg-slate-700 text-slate-200 hover:bg-slate-600",
        destructive:
          "border-transparent bg-red-900/80 text-red-100 hover:bg-red-900",
        outline: "text-slate-300 border-slate-700",
        success: "border-transparent bg-emerald-900/80 text-emerald-100 hover:bg-emerald-900",
        warning: "border-transparent bg-amber-900/80 text-amber-100 hover:bg-amber-900",
        info: "border-transparent bg-sky-900/80 text-sky-100 hover:bg-sky-900",
        purple: "border-transparent bg-purple-900/80 text-purple-100 hover:bg-purple-900",
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

export { Badge, badgeVariants }
