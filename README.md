# WatchMMAFull CloudStream repo

CloudStream repository scaffold for `watchmmafull.com`.

## Included

- `WatchMMAFullProvider`: best-effort provider for homepage listings, search, page metadata, and link extraction from iframes or direct video URLs
- GitHub Actions workflow: builds `.cs3` files and `plugins.json` into the `builds` branch
- Gradle template already wired for CloudStream plugin builds

## Current caveat

`watchmmafull.com` is serving a Cloudflare challenge to normal HTTP requests from this environment, so the provider could not be verified end-to-end here. The repo is ready for live testing, but `load()` and `loadLinks()` may need one more pass if the site uses challenge-dependent markup on-device too.

## Local build

- Linux/macOS: `./gradlew WatchMMAFullProvider:make`
- Windows: `.\gradlew.bat WatchMMAFullProvider:make`

## Publish

1. Create a new GitHub repository named `watchmmafull-cloudstream`.
2. Update the fallback repo URL in `build.gradle.kts` if you choose a different repository path.
3. Push both `main` and `builds` branches.
4. After the first GitHub Actions run, use the generated `builds/plugins.json` in your CloudStream repo manifest.

## Repo manifest

Once the GitHub repository exists, create a `repo.json` like this and replace `your-username`:

```json
{
  "name": "WatchMMAFull Repo",
  "description": "CloudStream plugins for WatchMMAFull",
  "manifestVersion": 1,
  "pluginLists": [
    "https://raw.githubusercontent.com/your-username/watchmmafull-cloudstream/builds/plugins.json"
  ]
}
```
