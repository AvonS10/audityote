import {
  ArrowDown,
  ArrowUp,
  ArrowUpRight,
  Bell,
  Check,
  ChevronDown,
  ChevronRight,
  ChevronUp,
  ClipboardCheck,
  Download,
  FileText,
  Filter,
  Info,
  LayoutDashboard,
  ListChecks,
  LogOut,
  Map as MapIcon,
  OctagonAlert,
  Plus,
  Search,
  Settings,
  Shield,
  ShieldAlert,
  ShieldCheck,
  Trash2,
  TrendingDown,
  TrendingUp,
  TriangleAlert,
  X,
  type LucideIcon,
} from 'lucide-react'
import type { CSSProperties } from 'react'

/**
 * Icon — a single line icon from the ControlMap set, backed by lucide-react (the design system's
 * icon library: 24px grid, 2px stroke, round caps/joins). Kebab-case names keep the design's API.
 * No emoji, ever — line icons only, inheriting currentColor.
 */
const ICONS = {
  search: Search,
  plus: Plus,
  filter: Filter,
  'chevron-down': ChevronDown,
  'chevron-right': ChevronRight,
  'chevron-up': ChevronUp,
  check: Check,
  x: X,
  shield: Shield,
  'shield-check': ShieldCheck,
  'alert-triangle': TriangleAlert,
  bell: Bell,
  settings: Settings,
  dashboard: LayoutDashboard,
  'file-text': FileText,
  download: Download,
  'list-checks': ListChecks,
  map: MapIcon,
  'trending-up': TrendingUp,
  'clipboard-check': ClipboardCheck,
  trash: Trash2,
  info: Info,
  'log-out': LogOut,
  'arrow-up-right': ArrowUpRight,
  'arrow-up': ArrowUp,
  'arrow-down': ArrowDown,
  'shield-alert': ShieldAlert,
  'alert-octagon': OctagonAlert,
  'trending-down': TrendingDown,
} satisfies Record<string, LucideIcon>

export type IconName = keyof typeof ICONS

interface IconProps {
  name: IconName
  size?: number
  strokeWidth?: number
  color?: string
  style?: CSSProperties
  className?: string
}

export function Icon({ name, size = 16, strokeWidth = 2, color = 'currentColor', style, className }: IconProps) {
  const Glyph = ICONS[name]
  return (
    <Glyph
      size={size}
      strokeWidth={strokeWidth}
      color={color}
      className={className}
      style={{ display: 'block', flex: 'none', ...style }}
      aria-hidden="true"
    />
  )
}
