PUT https://api.github.com/repos/{repo}/contents/{path}
Authorization: Bearer $secret.gh
Accept: application/vnd.github+json
Content-Type: application/json
X-GitHub-Api-Version: 2022-11-28

{{"message":"{msg}","content":"{content}","sha":"{sha}"}}