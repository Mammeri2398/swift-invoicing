// Tailwind theme of Swift Invoicing (Material 3 style tokens).
// Built by the Maven "generate-resources" phase into static/css/app.css (see pom.xml).
/** @type {import('tailwindcss').Config} */
module.exports = {
  content: {
    relative: true,
    files: [
      '../resources/templates/**/*.html',
      '../resources/static/js/**/*.js',
    ],
  },
  theme: {
    extend: {
      colors: {
        "background": "#f6faff",
        "error": "#ba1a1a",
        "error-container": "#ffdad6",
        "inverse-on-surface": "#e7f2fe",
        "inverse-primary": "#8aceff",
        "inverse-surface": "#27323a",
        "on-background": "#121d25",
        "on-error": "#ffffff",
        "on-error-container": "#93000a",
        "on-primary": "#ffffff",
        "on-primary-container": "#d7ecff",
        "on-primary-fixed": "#001e2f",
        "on-primary-fixed-variant": "#004b6f",
        "on-secondary": "#ffffff",
        "on-secondary-container": "#175b80",
        "on-secondary-fixed": "#001e2f",
        "on-secondary-fixed-variant": "#004c6e",
        "on-surface": "#121d25",
        "on-surface-variant": "#40484e",
        "on-tertiary": "#ffffff",
        "on-tertiary-container": "#98ffb3",
        "on-tertiary-fixed": "#00210c",
        "on-tertiary-fixed-variant": "#005228",
        "outline": "#70787f",
        "outline-variant": "#c0c7cf",
        "primary": "#00567d",
        "primary-container": "#1d6f9c",
        "primary-fixed": "#c9e6ff",
        "primary-fixed-dim": "#8aceff",
        "secondary": "#236489",
        "secondary-container": "#99d3fd",
        "secondary-fixed": "#c9e6ff",
        "secondary-fixed-dim": "#93cdf8",
        "surface": "#f6faff",
        "surface-bright": "#f6faff",
        "surface-container": "#e4effb",
        "surface-container-high": "#dee9f5",
        "surface-container-highest": "#d9e4ef",
        "surface-container-low": "#ebf5ff",
        "surface-container-lowest": "#ffffff",
        "surface-dim": "#d0dbe7",
        "surface-tint": "#046491",
        "surface-variant": "#d9e4ef",
        "tertiary": "#005e2e",
        "tertiary-container": "#00793e",
        "tertiary-fixed": "#91f8ad",
        "tertiary-fixed-dim": "#75db93"
      },
      borderRadius: {
        "DEFAULT": "0.125rem",
        "lg": "0.25rem",
        "xl": "0.5rem"
      },
      spacing: {
        "gutter": "1rem",
        "gutter-lg": "1.5rem",
        "margin": "1rem",
        "margin-lg": "2rem",
        "margin-md": "1.5rem",
        "space-lg": "1.25rem",
        "space-md": "0.75rem",
        "space-sm": "0.5rem",
        "space-xl": "1.75rem",
        "space-xs": "0.25rem"
      },
      fontFamily: {
        // Arabic letters come from the system fonts after Inter (Segoe UI, Tahoma on Windows)
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'Segoe UI', 'Tahoma', 'Noto Sans Arabic', 'sans-serif'],
      },
      fontSize: {
        "body-sm": [
          "12px",
          {
            "lineHeight": "16px",
            "letterSpacing": "0em",
            "fontWeight": "400"
          }
        ],
        "headline-md": [
          "20px",
          {
            "lineHeight": "28px",
            "letterSpacing": "-0.01em",
            "fontWeight": "600"
          }
        ],
        "title-sm": [
          "16px",
          {
            "lineHeight": "24px",
            "letterSpacing": "-0.005em",
            "fontWeight": "600"
          }
        ],
        "display-lg": [
          "32px",
          {
            "lineHeight": "40px",
            "letterSpacing": "-0.02em",
            "fontWeight": "700"
          }
        ],
        "label-md": [
          "13px",
          {
            "lineHeight": "18px",
            "letterSpacing": "0.01em",
            "fontWeight": "500"
          }
        ],
        "body-lg": [
          "15px",
          {
            "lineHeight": "22px",
            "letterSpacing": "0em",
            "fontWeight": "400"
          }
        ],
        "label-sm": [
          "11px",
          {
            "lineHeight": "14px",
            "letterSpacing": "0.04em",
            "fontWeight": "600"
          }
        ],
        "numeric-md": [
          "14px",
          {
            "lineHeight": "20px",
            "letterSpacing": "0em",
            "fontWeight": "500"
          }
        ],
        "headline-lg": [
          "24px",
          {
            "lineHeight": "32px",
            "letterSpacing": "-0.015em",
            "fontWeight": "600"
          }
        ],
        "numeric-lg": [
          "22px",
          {
            "lineHeight": "28px",
            "letterSpacing": "-0.01em",
            "fontWeight": "700"
          }
        ],
        "body-md": [
          "14px",
          {
            "lineHeight": "20px",
            "letterSpacing": "0em",
            "fontWeight": "400"
          }
        ]
      },
    },
  },
  plugins: [],
};
