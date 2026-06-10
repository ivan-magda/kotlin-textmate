# Releasing

Releases publish `kotlin-textmate-core` and `kotlin-textmate-compose` to Maven Central.
Pushing a `v*` tag triggers [`.github/workflows/release.yml`](.github/workflows/release.yml),
which publishes the tagged commit and creates a GitHub release. The publish job waits for
manual approval on the protected `maven-central` environment.

## One-time setup

Configure these in the repository's `maven-central` environment
(Settings → Environments → `maven-central`):

1. Add a **required reviewer** so the publish waits for a click.
2. Add four **environment secrets**:

   | Secret | Value |
   |---|---|
   | `MAVEN_CENTRAL_USERNAME` | Central Portal user token name |
   | `MAVEN_CENTRAL_PASSWORD` | Central Portal user token value |
   | `SIGNING_KEY` | ASCII-armored GPG private key: `gpg --export-secret-keys --armor <KEY_ID>` |
   | `SIGNING_PASSWORD` | passphrase for that key |

   Generate the Central Portal token at <https://central.sonatype.com> → Account → Generate User Token.

## Cutting a release

1. Make sure `main` is green.
2. Set the release version in `gradle.properties` (`VERSION_NAME`, drop `-SNAPSHOT`) and update the install coordinates in `README.md`.
3. Commit as `Release X.Y.Z`.
4. Tag and push:

   ```bash
   git tag -a vX.Y.Z -m "Release X.Y.Z"
   git push origin main vX.Y.Z
   ```

5. Approve the `maven-central` deployment in the Actions run. The workflow verifies the tag matches `VERSION_NAME`, refuses `-SNAPSHOT`, publishes, and creates the GitHub release.
6. Watch the deployment at <https://central.sonatype.com/publishing/deployments>. Sync to `repo1.maven.org` lands within minutes to a couple of hours.
7. Open the next development version: set `VERSION_NAME` to the next `-SNAPSHOT`, commit as `Prepare next development version`, and push.

## Notes

- `VERSION_NAME` is the single source of truth; `TextMateGrammar.VERSION` is generated from it at build time.
- The publish is irreversible — Maven Central coordinates cannot be reused or unpublished.
- To publish from a machine instead of CI, run `./gradlew publishAndReleaseToMavenCentral` with the same credentials as local Gradle properties.
