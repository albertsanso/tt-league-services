-- FEAT-008: introduce the season-independent CLUB identity.
-- Apply manually with the PostgreSQL credentials used by the runtime.
-- This script intentionally does not belong to Flyway or Liquibase.
BEGIN;

-- Prechecks fail before any DDL/data change when the expected renamed schema is
-- not present or the source inventory cannot be safely backfilled.
DO $$
BEGIN
    IF to_regclass('public.federated_club') IS NULL THEN
        RAISE EXCEPTION 'Expected public.federated_club; apply the legacy rename migration first';
    END IF;
    IF to_regclass('public.club') IS NOT NULL THEN
        RAISE EXCEPTION 'public.club already exists; do not run FEAT-008 twice';
    END IF;
    IF EXISTS (SELECT 1 FROM public.federated_club WHERE name IS NOT NULL AND btrim(name) = '') THEN
        RAISE EXCEPTION 'Blank federated_club names require explicit data remediation';
    END IF;
END
$$;

CREATE TEMP TABLE feat008_federated_club_before ON COMMIT DROP AS
SELECT id, source, name
FROM public.federated_club;

CREATE TEMP TABLE feat008_team_refs_before ON COMMIT DROP AS
SELECT id, federated_club_id
FROM public.team;

CREATE TABLE public.club (
    id uuid PRIMARY KEY,
    name varchar(255) NOT NULL CONSTRAINT uk_club_name UNIQUE
);

CREATE INDEX idx_club_name ON public.club (name);

-- pgcrypto is a PostgreSQL runtime dependency of this manual migration only.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO public.club (id, name)
SELECT gen_random_uuid(), names.name
FROM (
    SELECT DISTINCT name
    FROM public.federated_club
    WHERE name IS NOT NULL
) names;

DO $$
BEGIN
    IF EXISTS (
        SELECT name
        FROM public.club
        GROUP BY name
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate canonical club names were created';
    END IF;
END
$$;

ALTER TABLE public.federated_club
    ADD COLUMN club_id uuid NULL;

CREATE INDEX idx_federated_club_club_id
    ON public.federated_club (club_id);

ALTER TABLE public.federated_club
    ADD CONSTRAINT fk_federated_club_club
    FOREIGN KEY (club_id) REFERENCES public.club (id);

-- Exact, case-sensitive display-name linking only. NULL legacy rows remain
-- nullable and unlinked for later explicit remediation.
UPDATE public.federated_club federated
SET club_id = canonical.id
FROM public.club canonical
WHERE federated.name = canonical.name;

-- Postchecks verify preservation and the complete exact-name backfill.
DO $$
DECLARE
    expected_federated bigint;
    actual_federated bigint;
    expected_links bigint;
    actual_links bigint;
BEGIN
    SELECT count(*) INTO expected_federated FROM feat008_federated_club_before;
    SELECT count(*) INTO actual_federated FROM public.federated_club;
    IF expected_federated <> actual_federated THEN
        RAISE EXCEPTION 'Federated club row count changed: expected %, got %',
            expected_federated, actual_federated;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM feat008_federated_club_before before_row
        FULL JOIN public.federated_club after_row USING (id)
        WHERE before_row.id IS NULL
           OR after_row.id IS NULL
           OR before_row.source IS DISTINCT FROM after_row.source
           OR before_row.name IS DISTINCT FROM after_row.name
    ) THEN
        RAISE EXCEPTION 'Federated club UUID/source/name preservation check failed';
    END IF;

    SELECT count(*) INTO expected_links
    FROM public.federated_club
    WHERE name IS NOT NULL;
    SELECT count(*) INTO actual_links
    FROM public.federated_club
    WHERE name IS NOT NULL AND club_id IS NOT NULL;
    IF expected_links <> actual_links THEN
        RAISE EXCEPTION 'Not all non-null federated names were linked exactly';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.federated_club federated
        LEFT JOIN public.club canonical ON canonical.id = federated.club_id
        WHERE federated.club_id IS NOT NULL
          AND canonical.name IS DISTINCT FROM federated.name
    ) THEN
        RAISE EXCEPTION 'Canonical name/reference preservation check failed';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM feat008_team_refs_before before_row
        FULL JOIN public.team after_row USING (id)
        WHERE before_row.id IS NULL
           OR after_row.id IS NULL
           OR before_row.federated_club_id IS DISTINCT FROM after_row.federated_club_id
    ) THEN
        RAISE EXCEPTION 'Team federated_club_id reference preservation check failed';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.federated_club federated
        LEFT JOIN public.club canonical ON canonical.id = federated.club_id
        WHERE federated.club_id IS NOT NULL AND canonical.id IS NULL
    ) THEN
        RAISE EXCEPTION 'Foreign-key integrity check failed';
    END IF;
END
$$;

COMMIT;
