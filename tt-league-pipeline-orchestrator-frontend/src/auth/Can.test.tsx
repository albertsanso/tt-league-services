import { render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { AuthContext } from './authContext'
import type { AuthContextValue } from './authContext'
import { Can } from './Can'
import type { Capability } from './permissions'

function withUser(roles: string[], permissions: string[], children: ReactNode) {
  const value: AuthContextValue = {
    status: 'signed-in',
    token: 't',
    user: { username: 'u', roles, permissions, expiresAt: Date.now() + 60_000 },
    signIn: async () => undefined,
    signOut: () => undefined,
    getToken: () => 't',
  }
  return render(<AuthContext.Provider value={value}>{children}</AuthContext.Provider>)
}

const users = {
  'matches:write only': { roles: [], permissions: ['matches:write'] },
  'ADMIN only': { roles: ['ADMIN'], permissions: [] },
  both: { roles: ['ADMIN'], permissions: ['matches:write'] },
  neither: { roles: [], permissions: [] },
}

const expectations: Record<Capability, Record<keyof typeof users, boolean>> = {
  'trigger-runs': { 'matches:write only': true, 'ADMIN only': false, both: true, neither: false },
  'operate-match-days': { 'matches:write only': true, 'ADMIN only': false, both: true, neither: false },
  'resume-schedules': { 'matches:write only': true, 'ADMIN only': false, both: true, neither: false },
  'edit-polling-policy': { 'matches:write only': false, 'ADMIN only': true, both: true, neither: false },
}

describe('Can', () => {
  for (const capability of Object.keys(expectations) as Capability[]) {
    for (const [name, user] of Object.entries(users) as [keyof typeof users, (typeof users)[keyof typeof users]][]) {
      const allowed = expectations[capability][name]

      it(`${capability} with ${name}: disable mode ${allowed ? 'enables' : 'disables'} the control`, () => {
        withUser(user.roles, user.permissions, (
          <Can capability={capability} mode="disable">
            <button>Act</button>
          </Can>
        ))
        const button = screen.getByRole('button', { name: 'Act' })
        if (allowed) {
          expect(button).toBeEnabled()
        } else {
          expect(button).toBeDisabled()
        }
      })

      it(`${capability} with ${name}: hide mode ${allowed ? 'shows' : 'hides'} the control`, () => {
        withUser(user.roles, user.permissions, (
          <Can capability={capability} mode="hide">
            <button>Act</button>
          </Can>
        ))
        expect(screen.queryByRole('button', { name: 'Act' }) !== null).toBe(allowed)
      })
    }
  }

  it('does not let ADMIN alone trigger runs', () => {
    withUser(['ADMIN'], [], (
      <Can capability="trigger-runs" mode="disable">
        <button>Run</button>
      </Can>
    ))
    expect(screen.getByRole('button', { name: 'Run' })).toBeDisabled()
  })
})
