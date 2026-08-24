/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Absolute origin of the API, e.g. `https://api.schirmziit.ch`.
   *  Unset (the self-hosted default) means same-origin relative paths. */
  readonly VITE_API_BASE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
