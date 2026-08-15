# Summary

Filenames format like `acta_[local_team]_[visitor_team].json` are not always present and may differ. Avoid using that format and those IDs.

# Description

Filenames format like `acta_[local_team]_[visitor_team].json` are not always consistent, and sometimes they may contain additional identifiers or variations.
The goal is to get rid of [local_team] and [visitor_team] identifiers in the filenames, so that they can be processed more easily.

Consider actas filenames just as `acta.json` or `acta_[match_id].json`, and rely on the internal JSON info like season and team or club name to determine the teams involved in the match.

Remove the use of [local_team] and [visitor_team] identifiers in the import/load logic, and instead rely on the internal JSON info like season and team or club name to determine the teams involved in the match.

This topic applies for both BCNESA and RFETM markdown in `.claude/commands/build-bcnesa-treedir-navigator` and `.claude/commands/build-rfetm-treedir-navigator` , 
and code in the `tt-data-league-import` module, under the `org.cttelsamicsterrassa.data.load.traverse` package.

Modify both markdown and code to avoid using [local_team] and [visitor_team] identifiers in the filenames, and instead rely on the internal JSON info like season and team or club name to determine the teams involved in the match.
