/**
 * What runs where, in one place.
 *
 * The statuses live here and the labels live in the locale files, so a change
 * ("iOS measures background audio now") is one edit in one file and cannot end
 * up saying `yes` in German and `no` in French. Keep it in step with
 * `docs/platform-matrix.md`, which carries the same table for developers plus
 * the gaps too small for the site.
 */
export const matrixColumns = ['android', 'ios', 'web'] as const
export type MatrixColumn = (typeof matrixColumns)[number]

/** `no` is "not yet", not "never" — see the row's note for what it waits on. */
export type MatrixStatus = 'yes' | 'partial' | 'no'

export type MatrixGroup = 'measure' | 'view'

export const matrixRowKeys = [
  'appHours',
  'timeOfDay',
  'unlocks',
  'background',
  'appNames',
  'offline',
  'install',
  'roles',
  'overview14',
  'dayDetail',
  'childOwnNumbers',
  'manageChildren',
  'revokeDevice',
  'pairingCode',
  'deleteData',
  'helpLinks',
  'languages',
] as const
export type MatrixRowKey = (typeof matrixRowKeys)[number]

export type MatrixRow = {
  key: MatrixRowKey
  group: MatrixGroup
  /** `null` where the column is not meant to do this at all. */
  status: Record<MatrixColumn, MatrixStatus | null>
}

export const matrix: MatrixRow[] = [
  // What a child's phone can measure. The browser measures nothing, so `null`
  // throughout rather than a column of crosses that read as failures.
  { key: 'appHours', group: 'measure', status: { android: 'yes', ios: 'yes', web: null } },
  { key: 'timeOfDay', group: 'measure', status: { android: 'yes', ios: 'yes', web: null } },
  { key: 'unlocks', group: 'measure', status: { android: 'yes', ios: 'partial', web: null } },
  { key: 'background', group: 'measure', status: { android: 'partial', ios: 'no', web: null } },
  { key: 'appNames', group: 'measure', status: { android: 'yes', ios: 'partial', web: null } },
  { key: 'offline', group: 'measure', status: { android: 'yes', ios: 'yes', web: null } },
  { key: 'install', group: 'measure', status: { android: 'partial', ios: 'no', web: null } },

  // What someone looks at.
  { key: 'roles', group: 'view', status: { android: 'no', ios: 'yes', web: null } },
  { key: 'overview14', group: 'view', status: { android: null, ios: 'yes', web: 'yes' } },
  { key: 'dayDetail', group: 'view', status: { android: null, ios: 'yes', web: 'yes' } },
  { key: 'childOwnNumbers', group: 'view', status: { android: 'yes', ios: 'yes', web: null } },
  { key: 'manageChildren', group: 'view', status: { android: null, ios: 'yes', web: 'yes' } },
  { key: 'revokeDevice', group: 'view', status: { android: null, ios: 'yes', web: 'yes' } },
  { key: 'pairingCode', group: 'view', status: { android: null, ios: 'yes', web: 'yes' } },
  { key: 'deleteData', group: 'view', status: { android: null, ios: 'no', web: 'yes' } },
  { key: 'helpLinks', group: 'view', status: { android: 'yes', ios: 'yes', web: 'yes' } },
  { key: 'languages', group: 'view', status: { android: 'yes', ios: 'yes', web: 'yes' } },
]
