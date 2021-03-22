const PR_NUMBER = danger.github.pr.number;
const PR_LINK = `(#${PR_NUMBER})`;

const CHANGELOG_SUMMARY_TITLE = `Instructions and example for changelog`;
const CHANGELOG_BODY = `Please add an entry to \`CHANGELOG.md\` to the "Unreleased" section under the following heading:

To the changelog entry, please add a link to this PR (consider a more descriptive message):`;

const CHANGELOG_END_BODY = `If none of the above apply, you can opt out by adding _#skip-changelog_ to the PR description.`;

function getCleanTitleWithPrLink() {
  const title = danger.github.pr.title;
  return title.split(": ").slice(-1)[0].trim().replace(/\.+$/, "") + PR_LINK;
}

function getChangelogDetailsHtml() {
  return `
<details>
<summary><b>\`${CHANGELOG_SUMMARY_TITLE}\`$</b></summary>

\`${CHANGELOG_BODY}\`

\`\`\`md
- ${getCleanTitleWithPrLink()}
\`\`\`

\`${CHANGELOG_END_BODY}\`
</details>
`;
}

function getChangelogDetailsTxt() {
	return CHANGELOG_SUMMARY_TITLE + '\n' +
		   CHANGELOG_BODY + '\n' +
		   getCleanTitleWithPrLink() + '\n' +
		   CHANGELOG_END_BODY;
}

function HasPermissionToComment(){
	return danger.github.pr.head.repo.git_url == danger.github.pr.base.repo.git_url;
}

async function containsChangelog(path) {
  const contents = await danger.github.utils.fileContents(path);
  return contents.includes(PR_LINK);
}

async function checkChangelog() {
  const skipChangelog =
    danger.github && (danger.github.pr.body + "").includes("#skip-changelog");
  if (skipChangelog) {
    return;
  }

  const hasChangelog = await containsChangelog("CHANGELOG.md");

  if (!hasChangelog)
  {
	if (HasPermissionToComment())
	{
		fail("Please consider adding a changelog entry for the next release.");
		markdown(getChangelogDetailsHtml());
	}
	else
	{
		//Fallback
		console.log("Please consider adding a changelog entry for the next release.");
		console.log(getChangelogDetailsTxt());
		process.exitCode = 1;
	}
  }
}

async function checkIfFeature() {
   const title = danger.github.pr.title;
   if (title.startsWith('feat:') && HasPermissionToComment()){
	 message('Do not forget to update <a href="https://github.com/getsentry/sentry-docs">Sentry-docs</a> with your feature once the pull request gets approved.');
   }
}

async function checkAll() {
  // See: https://spectrum.chat/danger/javascript/support-for-github-draft-prs~82948576-ce84-40e7-a043-7675e5bf5690
  const isDraft = danger.github.pr.mergeable_state === "draft";

  if (isDraft) {
    return;
  }

  await checkIfFeature();
  await checkChangelog();
}

schedule(checkAll);
