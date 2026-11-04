import { useAuth } from './useAuth'

export type Capability = 'trigger-runs' | 'operate-match-days' | 'resume-schedules' | 'edit-polling-policy'

interface Requirement {
  readonly kind: 'permission' | 'role'
  readonly value: string
}

/** Mirrors the orchestrator SecurityConfiguration; the server remains the authority. */
export const CAPABILITY_REQUIREMENTS: Readonly<Record<Capability, Requirement>> = {
  'trigger-runs': { kind: 'permission', value: 'matches:write' },
  'operate-match-days': { kind: 'permission', value: 'matches:write' },
  'resume-schedules': { kind: 'permission', value: 'matches:write' },
  'edit-polling-policy': { kind: 'role', value: 'ADMIN' },
}

export function can(
  capability: Capability,
  user: { readonly roles: readonly string[]; readonly permissions: readonly string[] } | undefined,
): boolean {
  if (!user) {
    return false
  }
  const requirement = CAPABILITY_REQUIREMENTS[capability]
  return requirement.kind === 'permission'
    ? user.permissions.includes(requirement.value)
    : user.roles.includes(requirement.value)
}

export function requirementText(capability: Capability): string {
  const requirement = CAPABILITY_REQUIREMENTS[capability]
  return requirement.kind === 'permission'
    ? `Requires the ${requirement.value} permission`
    : `Requires the ${requirement.value} role`
}

export function useCan(capability: Capability): boolean {
  return can(capability, useAuth().user)
}
