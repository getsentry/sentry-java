async function checkDocs() {
  if (danger.github.pr.title.startsWith("feat:")) {
    message(
      'Do not forget to update <a href="https://github.com/getsentry/sentry-docs">Sentry-docs</a> with your feature once the pull request gets approved.'
    );
  }
}

async function checkChangelog() {
  const changelogFile = "CHANGELOG.md";

  // Check if skipped
  const skipChangelog =
    danger.github && (danger.github.pr.body + "").includes("#skip-changelog");

  if (skipChangelog) {
    return;
  }

  // Check if current PR has an entry in changelog
  const changelogContents = await danger.github.utils.fileContents(
    changelogFile
  );

  const hasChangelogEntry = RegExp(`#${danger.github.pr.number}\\b`).test(
    changelogContents
  );

  if (hasChangelogEntry) {
    return;
  }

  // Report missing changelog entry
  fail(
    "Please consider adding a changelog entry for the next release.",
    changelogFile
  );

  const prTitleFormatted = danger.github.pr.title
    .split(": ")
    .slice(-1)[0]
    .trim()
    .replace(/\.+$/, "");

  markdown(
    `
### Instructions and example for changelog

Please add an entry to \`CHANGELOG.md\` to the "Unreleased" section. Make sure the entry includes this PR's number.

Example:

\`\`\`markdown
## Unreleased

- ${prTitleFormatted} ([#${danger.github.pr.number}](${danger.github.pr.html_url}))
\`\`\`

If none of the above apply, you can opt out of this check by adding \`#skip-changelog\` to the PR description.`.trim()
  );
}

async function checkAll() {
  // See: https://spectrum.chat/danger/javascript/support-for-github-draft-prs~82948576-ce84-40e7-a043-7675e5bf5690
  const isDraft = danger.github.pr.mergeable_state === "draft";

  if (isDraft) {
    return;
  }

  await checkDocs();
  await checkChangelog();
}

schedule(checkAll);
