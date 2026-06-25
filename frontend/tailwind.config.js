/**
 * ControlMap — Tailwind v3 token mapping (from the design system bundle).
 *
 * Maps the design system's CSS custom properties (frontend/src/design/styles.css ->
 * tokens/*.css) to named Tailwind tokens, so the build uses the same semantic names
 * (bg-surface-card, text-strong, border-subtle, bg-primary, rounded-md, shadow-sm, font-display).
 *
 * IMPORTANT: src/index.css still imports the design system's styles.css so these var(--…)
 * tokens resolve at runtime and theming keeps working (class="theme-carbon" → Carbon; none = Sovereign).
 * Gotcha: because tokens point at var(--…), Tailwind's /<alpha> opacity modifier won't apply —
 * use the provided *-soft / *-bg tints instead.
 */
/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        /* Surfaces & app background */
        'bg-app': 'var(--bg-app)',
        'bg-canvas': 'var(--bg-canvas)',
        surface: {
          DEFAULT: 'var(--surface-card)',
          card: 'var(--surface-card)',
          sunken: 'var(--surface-sunken)',
          inset: 'var(--surface-inset)',
          header: 'var(--surface-header)',
        },
        /* Text */
        text: {
          strong: 'var(--text-strong)',
          body: 'var(--text-body)',
          muted: 'var(--text-muted)',
          faint: 'var(--text-faint)',
          'on-dark': 'var(--text-on-dark)',
          'on-primary': 'var(--text-on-primary)',
          link: 'var(--text-link)',
        },
        /* Borders */
        border: {
          subtle: 'var(--border-subtle)',
          DEFAULT: 'var(--border-default)',
          strong: 'var(--border-strong)',
          focus: 'var(--border-focus)',
        },
        /* Primary (brand / action) */
        primary: {
          DEFAULT: 'var(--primary)',
          hover: 'var(--primary-hover)',
          press: 'var(--primary-press)',
          soft: 'var(--primary-soft)',
          'soft-border': 'var(--primary-soft-border)',
        },
        accent: 'var(--accent)',
        /* Severity — semantic, fixed */
        critical: { DEFAULT: 'var(--critical-600)', strong: 'var(--critical-500)', soft: 'var(--critical-100)' },
        high: { DEFAULT: 'var(--high-600)', strong: 'var(--high-500)', soft: 'var(--high-100)' },
        medium: { DEFAULT: 'var(--medium-700)', strong: 'var(--medium-500)', soft: 'var(--medium-100)' },
        low: { DEFAULT: 'var(--low-600)', strong: 'var(--low-500)', soft: 'var(--low-100)' },
        /* Status — workflow, semantic, fixed (fg + matching -bg tint) */
        'status-open': 'var(--status-open)', 'status-open-bg': 'var(--status-open-bg)',
        'status-progress': 'var(--status-progress)', 'status-progress-bg': 'var(--status-progress-bg)',
        'status-submitted': 'var(--status-submitted)', 'status-submitted-bg': 'var(--status-submitted-bg)',
        'status-approved': 'var(--status-approved)', 'status-approved-bg': 'var(--status-approved-bg)',
        'status-returned': 'var(--status-returned)', 'status-returned-bg': 'var(--status-returned-bg)',
        'status-remediated': 'var(--status-remediated)', 'status-remediated-bg': 'var(--status-remediated-bg)',
        'status-accepted': 'var(--status-accepted)', 'status-accepted-bg': 'var(--status-accepted-bg)',
        /* Utility */
        positive: { DEFAULT: 'var(--positive-600)', soft: 'var(--positive-100)' },
        negative: { DEFAULT: 'var(--negative-600)', soft: 'var(--negative-100)' },
      },
      /* textColor / borderColor mapped explicitly so the documented names work as-is
         (text-strong, border-subtle). The `text`/`border` color *groups* above collide with
         Tailwind's text-/border- utility prefixes, so these give the clean, intended classes. */
      textColor: {
        strong: 'var(--text-strong)',
        body: 'var(--text-body)',
        muted: 'var(--text-muted)',
        faint: 'var(--text-faint)',
        'on-dark': 'var(--text-on-dark)',
        'on-primary': 'var(--text-on-primary)',
        link: 'var(--text-link)',
      },
      borderColor: {
        subtle: 'var(--border-subtle)',
        DEFAULT: 'var(--border-default)',
        strong: 'var(--border-strong)',
        focus: 'var(--border-focus)',
      },
      fontFamily: {
        sans: ['var(--font-body)'],
        body: ['var(--font-body)'],
        display: ['var(--font-display)'],
        mono: ['var(--font-data)'],
      },
      fontSize: {
        display: 'var(--fs-display)',
        h1: 'var(--fs-h1)',
        h2: 'var(--fs-h2)',
        h3: 'var(--fs-h3)',
        body: 'var(--fs-body)',
        'body-sm': 'var(--fs-body-sm)',
        caption: 'var(--fs-caption)',
        micro: 'var(--fs-micro)',
        stat: 'var(--fs-stat)',
      },
      spacing: {
        1: 'var(--space-1)', 2: 'var(--space-2)', 3: 'var(--space-3)', 4: 'var(--space-4)',
        5: 'var(--space-5)', 6: 'var(--space-6)', 7: 'var(--space-7)', 8: 'var(--space-8)',
        9: 'var(--space-9)', 10: 'var(--space-10)', 11: 'var(--space-11)', 12: 'var(--space-12)',
        'control-sm': 'var(--control-h-sm)', 'control-md': 'var(--control-h-md)', 'control-lg': 'var(--control-h-lg)',
        sidebar: 'var(--sidebar-w)', topbar: 'var(--topbar-h)', row: 'var(--row-h)',
      },
      borderRadius: {
        xs: 'var(--radius-xs)',
        sm: 'var(--radius-sm)',
        md: 'var(--radius-md)',
        lg: 'var(--radius-lg)',
        full: 'var(--radius-pill)',
      },
      boxShadow: {
        xs: 'var(--shadow-xs)',
        sm: 'var(--shadow-sm)',
        md: 'var(--shadow-md)',
        lg: 'var(--shadow-lg)',
        pop: 'var(--shadow-pop)',
        focus: 'var(--focus-ring)',
      },
      ringColor: { focus: 'var(--border-focus)' },
    },
  },
}
