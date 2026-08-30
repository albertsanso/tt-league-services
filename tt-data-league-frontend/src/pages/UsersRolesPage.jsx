import { useEffect, useRef, useState } from 'react'
import { Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { createUser, deleteUser, setUserActive, updateUser } from '../api/users.js'
import { useAuth } from '../context/useAuth.js'
import { useRoleCatalog, useUsers } from '../hooks/useUsers.js'

// ---------------------------------------------------------------------------
// Page
// ---------------------------------------------------------------------------

function UsersRolesPage() {
  const { t } = useTranslation()
  const [search, setSearch] = useState('')
  const [activeFilter, setActiveFilter] = useState(null)
  const [page, setPage] = useState(0)
  const [editingUser, setEditingUser] = useState(null)
  const [creatingUser, setCreatingUser] = useState(false)
  const [confirmToggle, setConfirmToggle] = useState(null)
  const [confirmDelete, setConfirmDelete] = useState(null)
  const [mutationError, setMutationError] = useState(null)
  const [mutationSuccess, setMutationSuccess] = useState(null)

  const { token, clearSession } = useAuth()

  const [appliedSearch, setAppliedSearch] = useState('')
  const [appliedActive, setAppliedActive] = useState(null)
  const [refreshKey, setRefreshKey] = useState(0)

  const { data: usersPage, loading, error, retry } = useUsers({
    search: appliedSearch,
    active: appliedActive,
    page,
    size: 20,
    refreshKey,
  })

  const { data: roles } = useRoleCatalog()

  function handleSearch(e) {
    e.preventDefault()
    setPage(0)
    setAppliedSearch(search)
    setAppliedActive(activeFilter)
  }

  function handleClearFilter() {
    setSearch('')
    setActiveFilter(null)
    setPage(0)
    setAppliedSearch('')
    setAppliedActive(null)
  }

  function refresh() {
    setRefreshKey((k) => k + 1)
  }

  function handleEditSaved() {
    setEditingUser(null)
    setCreatingUser(false)
    setMutationSuccess(t('usersAdmin.saveSuccess'))
    setMutationError(null)
    refresh()
  }

  function handleEditError(err) {
    setMutationSuccess(null)
    if (err?.status === 409) {
      setMutationError(t('usersAdmin.conflictError'))
    } else if (err?.status === 400) {
      setMutationError(err.message || t('usersAdmin.validationError'))
    } else {
      setMutationError(t('usersAdmin.saveError'))
    }
  }

  function handleConfirmToggle(user) {
    setConfirmToggle(user)
    setMutationError(null)
    setMutationSuccess(null)
  }

  function handleConfirmDelete(user) {
    setConfirmDelete(user)
    setMutationError(null)
    setMutationSuccess(null)
  }

  async function handleDeleteUser() {
    if (!confirmDelete) return
    const controller = new AbortController()
    try {
      await deleteUser(confirmDelete.id, token, controller.signal, clearSession)
      setConfirmDelete(null)
      setMutationSuccess(t('usersAdmin.deleteSuccess'))
      setMutationError(null)
      refresh()
    } catch (err) {
      setConfirmDelete(null)
      if (err?.status === 409) {
        setMutationError(err.message || t('usersAdmin.deleteActiveError'))
      } else {
        setMutationError(t('usersAdmin.deleteError'))
      }
    }
  }

  async function handleToggleActive() {
    if (!confirmToggle) return
    const controller = new AbortController()
    try {
      await setUserActive(confirmToggle.id, !confirmToggle.active, token, controller.signal, clearSession)
      setConfirmToggle(null)
      setMutationSuccess(t('usersAdmin.activeToggleSuccess'))
      setMutationError(null)
      refresh()
    } catch (err) {
      setConfirmToggle(null)
      if (err?.status === 409) {
        setMutationError(err.message || t('usersAdmin.conflictError'))
      } else {
        setMutationError(t('usersAdmin.saveError'))
      }
    }
  }

  const activeOptions = [
    { value: null, label: t('common.all') },
    { value: true, label: t('usersAdmin.activeOnly') },
    { value: false, label: t('usersAdmin.inactiveOnly') },
  ]

  return (
    <section className="page-block" aria-labelledby="users-admin-title">
      <div>
        <p className="section-label">{t('navigation.sectionAdministration')}</p>
        <h1 id="users-admin-title" className="page-title">{t('administration.administrationUsers.title')}</h1>
        <p className="page-description">{t('administration.administrationUsers.description')}</p>
      </div>

      {mutationSuccess && (
        <p className="form-success" role="status" aria-live="polite">{mutationSuccess}</p>
      )}
      {mutationError && (
        <p className="form-error" role="alert">{mutationError}</p>
      )}

      {/* Create button */}
      {!creatingUser && !editingUser && (
        <div className="users-admin-actions club-action-row">
          <button
            className="primary-button"
            type="button"
            onClick={() => { setCreatingUser(true); setMutationError(null); setMutationSuccess(null) }}
          >
            {t('usersAdmin.createUser')}
          </button>
        </div>
      )}

      {/* Create form */}
      {creatingUser && (
        <UserForm
          roles={roles ?? []}
          onSave={handleEditSaved}
          onError={handleEditError}
          onCancel={() => setCreatingUser(false)}
          token={token}
          clearSession={clearSession}
        />
      )}

      {/* Edit form */}
      {editingUser && (
        <UserForm
          user={editingUser}
          roles={roles ?? []}
          onSave={handleEditSaved}
          onError={handleEditError}
          onCancel={() => setEditingUser(null)}
          token={token}
          clearSession={clearSession}
        />
      )}

      {/* Confirm toggle dialog */}
      {confirmToggle && (
        <div className="confirm-dialog card" role="dialog" aria-labelledby="confirm-toggle-label">
          <p id="confirm-toggle-label">
            {confirmToggle.active
              ? t('usersAdmin.confirmDeactivate', { username: confirmToggle.username })
              : t('usersAdmin.confirmActivate', { username: confirmToggle.username })}
          </p>
          <div className="confirm-dialog-actions">
            <button className="secondary-button" type="button" onClick={() => setConfirmToggle(null)}>
              {t('common.cancel')}
            </button>
            <button className="primary-button" type="button" onClick={handleToggleActive}>
              {t('usersAdmin.confirm')}
            </button>
          </div>
        </div>
      )}

      {/* Confirm delete dialog */}
      {confirmDelete && (
        <div className="confirm-dialog card" role="dialog" aria-labelledby="confirm-delete-label">
          <p id="confirm-delete-label">
            {t('usersAdmin.confirmDelete', { username: confirmDelete.username })}
          </p>
          <div className="confirm-dialog-actions">
            <button className="secondary-button" type="button" onClick={() => setConfirmDelete(null)}>
              {t('common.cancel')}
            </button>
            <button className="danger-button" type="button" onClick={handleDeleteUser}>
              {t('usersAdmin.confirmDeleteAction')}
            </button>
          </div>
        </div>
      )}

      {/* Search / filter */}
      <article className="users-search-card card">
        <form className="users-search-form club-search-form" onSubmit={handleSearch} aria-label={t('usersAdmin.filterAriaLabel')}>
          <fieldset className="users-search-field">
            <legend>{t('usersAdmin.searchStringLabel')}</legend>
            <div className="club-search-input-wrap">
              <Search size={17} aria-hidden="true" />
              <input
                id="users-search"
                className="club-search-input"
                type="search"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder={t('usersAdmin.searchPlaceholder')}
              />
            </div>
          </fieldset>
          <fieldset className="users-active-filter">
            <legend>{t('usersAdmin.statusFilter')}</legend>
            {activeOptions.map((opt) => (
              <label key={String(opt.value)} className="radio-option">
                <input
                  type="radio"
                  name="active-filter"
                  checked={activeFilter === opt.value}
                  onChange={() => setActiveFilter(opt.value)}
                />
                {opt.label}
              </label>
            ))}
          </fieldset>
          <div className="users-search-actions club-action-row">
            <button className="primary-button" type="submit">{t('common.search')}</button>
            <button className="secondary-button" type="button" onClick={handleClearFilter}>
              {t('usersAdmin.clearFilter')}
            </button>
          </div>
        </form>
      </article>

      {/* Results */}
      <UserListSection
        loading={loading}
        error={error}
        usersPage={usersPage}
        page={page}
        onPageChange={setPage}
        onEdit={(u) => { setEditingUser(u); setMutationError(null); setMutationSuccess(null) }}
        onToggle={handleConfirmToggle}
        onDelete={handleConfirmDelete}
        onRetry={retry}
      />
    </section>
  )
}

// ---------------------------------------------------------------------------
// UserListSection
// ---------------------------------------------------------------------------

function UserListSection({ loading, error, usersPage, page, onPageChange, onEdit, onToggle, onDelete, onRetry }) {
  const { t } = useTranslation()

  if (loading) {
    return <p className="club-state card" role="status">{t('usersAdmin.loading')}</p>
  }

  if (error) {
    const status = error.status
    if (status === 401) return <p className="form-error" role="alert">{t('usersAdmin.unauthorized')}</p>
    if (status === 403) return <p className="form-error" role="alert">{t('usersAdmin.forbidden')}</p>
    return (
      <div className="card">
        <p className="form-error" role="alert">{t('usersAdmin.loadError')}</p>
        <button className="secondary-button" type="button" onClick={onRetry}>{t('common.retry')}</button>
      </div>
    )
  }

  if (!usersPage || usersPage.content.length === 0) {
    return <p className="club-state card">{t('usersAdmin.empty')}</p>
  }

  return (
    <div className="users-list" aria-label={t('usersAdmin.listAriaLabel')}>
      <p className="users-count">{t('usersAdmin.count', { count: usersPage.totalElements })}</p>
      <ul className="users-items" role="list">
        {usersPage.content.map((user) => (
          <UserRow
            key={user.id}
            user={user}
            onEdit={onEdit}
            onToggle={onToggle}
            onDelete={onDelete}
          />
        ))}
      </ul>
      {usersPage.totalPages > 1 && (
        <Pagination
          page={page}
          totalPages={usersPage.totalPages}
          onPageChange={onPageChange}
        />
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// UserRow
// ---------------------------------------------------------------------------

function UserRow({ user, onEdit, onToggle, onDelete }) {
  const { t } = useTranslation()
  return (
    <li className={`user-row card ${user.active ? '' : 'user-row--inactive'}`}>
      <div className="user-row-identity">
        <span className="user-row-username">{user.username}</span>
        <span className="user-row-email">{user.email}</span>
      </div>
      <div className="user-row-roles">
        {user.roles.map((role) => (
          <span key={role} className="role-badge">{t(`usersAdmin.role.${role}`, { defaultValue: role })}</span>
        ))}
      </div>
      <div className="user-row-permissions">
        {user.permissions.map((perm) => (
          <span key={perm} className="permission-badge">{t(`usersAdmin.permission.${perm}`, { defaultValue: perm })}</span>
        ))}
      </div>
      <div className="user-row-status">
        <span className={`status-badge ${user.active ? 'status-badge--active' : 'status-badge--inactive'}`}>
          {user.active ? t('usersAdmin.active') : t('usersAdmin.inactive')}
        </span>
      </div>
      <div className="user-row-actions">
        <button
          className="secondary-button"
          type="button"
          onClick={() => onEdit(user)}
          aria-label={t('usersAdmin.editAriaLabel', { username: user.username })}
        >
          {t('usersAdmin.edit')}
        </button>
        <button
          className="secondary-button"
          type="button"
          onClick={() => onToggle(user)}
          aria-label={user.active
            ? t('usersAdmin.deactivateAriaLabel', { username: user.username })
            : t('usersAdmin.activateAriaLabel', { username: user.username })}
        >
          {user.active ? t('usersAdmin.deactivate') : t('usersAdmin.activate')}
        </button>
        {!user.active && (
          <button
            className="danger-button"
            type="button"
            onClick={() => onDelete(user)}
            aria-label={t('usersAdmin.deleteAriaLabel', { username: user.username })}
          >
            {t('usersAdmin.delete')}
          </button>
        )}
      </div>
    </li>
  )
}

// ---------------------------------------------------------------------------
// UserForm — shared create/edit form
// ---------------------------------------------------------------------------

function UserForm({ user, roles, onSave, onError, onCancel, token, clearSession }) {
  const { t } = useTranslation()
  const isEdit = Boolean(user)
  const [username, setUsername] = useState(user?.username ?? '')
  const [email, setEmail] = useState(user?.email ?? '')
  const [password, setPassword] = useState('')
  const [selectedRoles, setSelectedRoles] = useState(
    new Set(user?.roles ?? ['PRACTITIONER']),
  )
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const controllerRef = useRef(null)

  useEffect(() => () => controllerRef.current?.abort(), [])

  function toggleRole(roleName) {
    setSelectedRoles((prev) => {
      const next = new Set(prev)
      if (next.has(roleName)) {
        if (next.size === 1) return prev
        next.delete(roleName)
      } else {
        next.add(roleName)
      }
      return next
    })
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!username.trim() || !email.trim()) {
      setFormError(t('usersAdmin.requiredFields'))
      return
    }
    if (!isEdit && !password.trim()) {
      setFormError(t('usersAdmin.passwordRequired'))
      return
    }

    setFormError('')
    setSubmitting(true)
    const controller = new AbortController()
    controllerRef.current = controller

    try {
      if (isEdit) {
        await updateUser(user.id, {
          username: username.trim(),
          email: email.trim(),
          roles: [...selectedRoles],
        }, token, controller.signal, clearSession)
      } else {
        await createUser({
          username: username.trim(),
          email: email.trim(),
          password: password.trim(),
          roles: [...selectedRoles],
        }, token, controller.signal, clearSession)
      }
      onSave()
    } catch (err) {
      if (err.name !== 'AbortError') {
        if (err.status === 400) {
          setFormError(err.message || t('usersAdmin.validationError'))
        } else {
          onError(err)
        }
      }
    } finally {
      controllerRef.current = null
      setSubmitting(false)
    }
  }

  return (
    <section
      className="user-form card"
      aria-labelledby={isEdit ? 'user-edit-title' : 'user-create-title'}
    >
      <h2
        id={isEdit ? 'user-edit-title' : 'user-create-title'}
        className="section-title"
      >
        {isEdit ? t('usersAdmin.editTitle', { username: user.username }) : t('usersAdmin.createTitle')}
      </h2>

      <form onSubmit={handleSubmit}>
        {formError && <p className="form-error" role="alert">{formError}</p>}

        <label className="auth-field" htmlFor="user-form-username">
          {t('usersAdmin.usernameLabel')}
          <input
            id="user-form-username"
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
            autoComplete="username"
            minLength={3}
            maxLength={20}
          />
        </label>

        <label className="auth-field" htmlFor="user-form-email">
          {t('usersAdmin.emailLabel')}
          <input
            id="user-form-email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="email"
          />
        </label>

        {!isEdit && (
          <label className="auth-field" htmlFor="user-form-password">
            {t('usersAdmin.passwordLabel')}
            <input
              id="user-form-password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="new-password"
            />
          </label>
        )}

        <fieldset className="user-form-roles">
          <legend>{t('usersAdmin.rolesLabel')}</legend>
          {roles.map((role) => (
            <label key={role.name} className="checkbox-option">
              <input
                type="checkbox"
                checked={selectedRoles.has(role.name)}
                onChange={() => toggleRole(role.name)}
              />
              {t(`usersAdmin.role.${role.name}`, { defaultValue: role.name })}
              <span className="role-permissions-hint">
                ({role.permissions.map((p) => t(`usersAdmin.permission.${p}`, { defaultValue: p })).join(', ')})
              </span>
            </label>
          ))}
        </fieldset>

        <div className="user-form-actions">
          <button className="secondary-button" type="button" onClick={onCancel} disabled={submitting}>
            {t('common.cancel')}
          </button>
          <button className="primary-button" type="submit" disabled={submitting}>
            {submitting ? t('usersAdmin.saving') : t('usersAdmin.save')}
          </button>
        </div>
      </form>
    </section>
  )
}

// ---------------------------------------------------------------------------
// Pagination
// ---------------------------------------------------------------------------

function Pagination({ page, totalPages, onPageChange }) {
  const { t } = useTranslation()
  return (
    <nav className="pagination" aria-label={t('usersAdmin.paginationAriaLabel')}>
      <button
        className="secondary-button"
        type="button"
        onClick={() => onPageChange(page - 1)}
        disabled={page === 0}
        aria-label={t('common.previous')}
      >
        {t('common.previous')}
      </button>
      <span>{t('common.pageOf', { page: page + 1, count: totalPages })}</span>
      <button
        className="secondary-button"
        type="button"
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
        aria-label={t('common.next')}
      >
        {t('common.next')}
      </button>
    </nav>
  )
}

export default UsersRolesPage
