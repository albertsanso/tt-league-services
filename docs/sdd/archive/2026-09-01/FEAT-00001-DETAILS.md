# Build Plan

1. Replace the Vite starter screen in
   `../../../../tt-data-league-frontend/src/App.jsx`, `src/index.css`, and
   `src/app.css` with the TT League application entry point.
2. Create the application shell, routes, shared UI components, hooks, and
   React Context state described in the theme sub-plan below.
3. Implement the overview page with deterministic mock statistics and a
   replaceable API hook.
4. Update the frontend document metadata and README with the application
   purpose, development commands, lint/build commands, and Maven invocation.
5. Validate the frontend with `npm ci`, `npm run lint`, `npm run build`, and
   `mvn -pl tt-data-league-frontend -am test`. Verify the documented desktop,
   tablet, mobile, keyboard, reduced-motion, drawer, dropdown, and direct-route
   behaviors.

## Theme sub-plan: `../../../frontend/theme-spec.md`

This sub-plan expands the complete theme specification into implementation
work. The theme specification remains the visual source of truth; this file
defines how each of its sections is translated into frontend artifacts.

### 1. Design foundations, tokens, and typography

- Define all surface, accent, text, border, and feedback colors as `:root`
  CSS variables in `../../../../tt-data-league-frontend/src/index.css`.
- Use the light professional visual direction: `#F0F4F8` app background,
  white cards/top bar, `#1E3A5F` institutional accent, restrained geometry,
  thin borders, and at most `shadow-sm` for interaction feedback.
- Use `Inter`, `system-ui`, and `sans-serif` for regular text. Use
  `DM Mono`, `JetBrains Mono`, and `monospace` only for statistics.
- Implement the documented type hierarchy, including page titles, section
  labels, card labels, body text, captions, badges, and the reusable
  `SectionLabel` with its horizontal rule.
- Remove starter dark-mode rules, purple accents, decorative gradients,
  oversized radii, and Vite-specific visual styles.

### 2. Global layout and application shell

- Implement `src/layouts/DashboardLayout.jsx` with a sticky sidebar, fixed
  height top bar, scrollable main content, centered `max-w-[1360px]` content
  area, footer, and nested route `<Outlet />`.
- Add a skip-to-content link and a main-content landmark with a stable `id`.
- Keep layout state in a React Context provider and expose a focused
  `useAppState` hook for sidebar collapse, mobile drawer visibility, user
  dropdown visibility, and notifications.

### 3. Sidebar and navigation

- Create `src/components/sidebar/Sidebar.jsx`, `SidebarItem.jsx`,
  `SidebarSectionLabel.jsx`, and `SidebarFooter.jsx`.
- Implement the expanded `w-60` and collapsed `w-16` states, institutional
  blue surface, active left border, hover states, disabled Analytics item,
  navigation groups, version/project footer, and Lucide icons.
- Define navigation metadata in `src/config/navigation.js` for Overview,
  clubs, players, matches, Analytics, and Settings.
- Use semantic `<nav aria-label="Navegació principal">`, route-aware active
  state, `aria-current="page"`, and keyboard-operable controls.

### 4. Top bar and user controls

- Create `src/components/topbar/TopBar.jsx`, `Breadcrumb.jsx`,
  `NotificationBell.jsx`, and `UserDropdown.jsx`.
- Implement the mobile menu trigger, automatic breadcrumb labels from the
  current route, notification indicator, avatar/user menu, `aria-expanded`,
  focus-visible styles, and Escape-to-close behavior.
- Keep the dropdown visually restrained with a border and subtle shadow while
  preserving its profile, preferences, and logout action structure.

### 5. Overview page and reusable UI

- Create the component structure specified by the theme:
  `src/components/overview/HeroBanner.jsx`,
  `GlobalSearch.jsx`, `QuickAccessGrid.jsx`, `QuickAccessCard.jsx`,
  `CommunityStats.jsx`, `StatCard.jsx`, and `AnalyticsBanner.jsx`.
- Create `src/components/ui/SectionLabel.jsx`, `AccentBar.jsx`, and
  `Badge.jsx` as reusable primitives.
- Implement `src/pages/OverviewPage.jsx` with:
  - the welcome hero and subtle geometric decoration;
  - global search with a minimum of two characters, 300 ms debounce, and
    navigation to `/cerca?q={query}`;
  - quick-access cards linking to `/clubs`, `/jugadors`, and `/partits`;
  - four community statistics cards for players, clubs, matches, and season;
  - the institutional-blue analytics teaser banner.
- Keep all visible UI copy in Catalan and use `Building2`, `Users`, `Swords`,
  and other Lucide icons with stroke width 1.5.

### 6. Routes, pages, and code organization

- Define route metadata in `src/config/routes.js` and lazy-load route pages.
- Add `OverviewPage.jsx`, `ClubsSearchPage.jsx`, `PlayersSearchPage.jsx`,
  `MatchesSearchPage.jsx`, `SearchResultsPage.jsx`, and `SettingsPage.jsx`.
- Provide a consistent shell and clear heading for placeholder pages while
  keeping Analytics disabled until its own feature is implemented.
- Keep each component focused and below the theme specification's 150-line
  guideline.

### 7. Responsive behavior

- At desktop widths (`>=1280px`), use an expanded `w-60` sidebar, `p-8`
  content, four-column statistics, and three-column quick access.
- At tablet widths (`768px` to `1279px`), use an icon-only `w-16` sidebar,
  `p-6` content, and two-column grids.
- Below `768px`, use the top-bar menu button, `p-4` content, one-column
  quick-access cards, a compact two-by-two statistics grid, and a left-side
  drawer with a `bg-black/40` backdrop and 300 ms slide transition.
- Check text wrapping, card heights, focus targets, and drawer dismissal at
  representative desktop, tablet, and mobile viewport widths.

### 8. Accessibility and motion

- Use semantic landmarks, labelled navigation and breadcrumb elements, and
  accessible names for icon-only controls.
- Provide the documented accent focus ring, active-route state, dropdown
  keyboard behavior, mobile drawer keyboard behavior, and visible focus.
- Implement `src/hooks/useCountUp.js` with `IntersectionObserver`, 800 ms
  ease-out animation, and immediate values under
  `prefers-reduced-motion`.
- Apply the documented hover transitions only where useful and avoid motion
  when the user requests reduced motion.

### 9. Data boundary and validation

- Implement `src/hooks/useCommunityStats.js` with the documented
  `/api/stats/community` response shape, deterministic mock data for the
  skeleton, loading state, and explicit error state.
- Keep API access in the hook so page components do not depend on Spring,
  Java, or persistence details.
- Validate the theme checklist: token usage, sidebar state, section labels,
  breadcrumb generation, dropdown behavior, search focus, card hover,
  statistic count-up, responsive layout, and accessibility requirements.

# Implementation Guidelines

- Treat `../../../frontend/theme-spec.md` and the theme sub-plan above as the
  visual and interaction contract for FEAT-00001.
- Use the existing React, React Router, Tailwind CSS, and Lucide dependencies.
  Use React Context for shared UI state rather than adding Zustand or another
  state library.
- Use `react-router-dom` for navigation rather than manual
  `window.location` changes. Preserve the documented Catalan labels and URL
  contracts.
- Keep all theme tokens in CSS variables. Do not introduce gradients,
  decorative illustrations, oversized radii, large shadows, or a dark-mode
  alternative.
- Keep API calls behind hooks with explicit loading and error states. Mock
  data must be replaceable without changing page components or masking a
  failed request.
- Keep the frontend independent from Java and Spring implementation details;
  integrate backend data only through the documented HTTP API boundary.

# Notes

- The current frontend is a Vite React starter. FEAT-00001 replaces the starter
  screen and does not require changes to the Java domain or API modules.
- The existing Maven frontend plugin pins Node/npm and runs install and lint
  during the test phase; the production build runs during package preparation.
- The package currently has no test script. Adding a component test runner is
  out of scope unless separately approved as a dependency decision.
- Backend endpoints may not be available during skeleton development, so the
  overview uses deterministic mock statistics behind `useCommunityStats`.
- FEAT-00001 shipped with the starter screen replaced, route-level pages lazy
  loaded, and the documented npm/Maven validation commands passing.
