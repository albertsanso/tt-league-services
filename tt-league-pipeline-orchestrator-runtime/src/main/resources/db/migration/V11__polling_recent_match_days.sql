-- Adaptive polling lookback (FEAT-00118): the policy gets recentMatchDays, the number of latest pollable match days of
-- each group that adaptive polling builds units from. Stored overrides are read strictly, so every row written
-- before this migration gets the documented default (3); version, updated_by and updated_at are left unchanged.

UPDATE pipeline.poll_policy
SET settings = settings || '{"recentMatchDays": 3}'::jsonb
WHERE NOT settings ? 'recentMatchDays';
