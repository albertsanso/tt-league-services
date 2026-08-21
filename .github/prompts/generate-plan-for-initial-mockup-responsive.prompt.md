# Summary

Generates a plan for creating a mockup based on the provided prompt.

# Goal

Generate a detailed plan based on `docs/mockups-prompts/initial-mockup-responsive.prompt.md` into the file `docs/mockups-plans/initial-mockup-responsive.plan.md`.
The plan should include a step-by-step outline of the tasks required to create the mockup, including any necessary resources, tools, and estimated
time for each task.

# Hard rules

- Avoid reading files in `docs/mockups-plans/`. The plan must be original and
  based solely on the provided prompt.
- This is a file-generation task, not a request to return an advisory plan in
  chat. Create or overwrite
  `docs/mockups-plans/initial-mockup-responsive.plan.md` in the repository.
- If `docs/mockups-plans/` does not exist, create the directory first.
- Do not output the plan contents in chat. After writing, verify that the exact
  target file exists and is non-empty. If the file cannot be written or
  verified, report the failure instead of presenting an unwritten plan as
  completed.