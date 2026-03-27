/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/main/resources/templates/**/*.html'],
  theme: {
    extend: {
      colors: {
        brand: {
          50:  '#f5f3ff',
          100: '#ede9fe',
          200: '#ddd6fe',
          300: '#c4b5fd',
          400: '#a78bfa',
          500: '#8b5cf6',
          600: '#7c3aed',
          700: '#6d28d9',
          800: '#5b21b6',
          900: '#1e0a4e',
        },
        accent: {
          50:  '#fff7ed',
          100: '#ffedd5',
          200: '#fed7aa',
          300: '#fdba74',
          400: '#fb923c',
          500: '#f97316',
          600: '#ea580c',
        },
        ink: {
          50:  '#f8f7ff',
          100: '#f0eeff',
          200: '#e4e0f8',
          900: '#0f0a1e',
          800: '#1a1030',
          700: '#2d2050',
        }
      },
      fontFamily: {
        display: ['"DM Serif Display"', 'Georgia', 'serif'],
        body:    ['"DM Sans"', 'system-ui', 'sans-serif'],
        mono:    ['"JetBrains Mono"', 'monospace'],
      },
      boxShadow: {
        'glow':    '0 0 30px rgba(124,58,237,0.25)',
        'glow-sm': '0 0 12px rgba(124,58,237,0.18)',
        'card':    '0 2px 16px rgba(15,10,30,0.08)',
        'card-lg': '0 8px 32px rgba(15,10,30,0.14)',
      },
      backgroundImage: {
        'grid-ink': "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='40' height='40'%3E%3Cpath d='M0 0h40v40H0z' fill='none'/%3E%3Cpath d='M0 40L40 0M-5 5L5-5M35 45L45 35' stroke='%238b5cf620' stroke-width='1'/%3E%3C/svg%3E\")",
      }
    },
  },
  plugins: [],
}   