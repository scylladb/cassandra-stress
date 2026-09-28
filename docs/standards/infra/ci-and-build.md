## CI and build

### Pinned actions

Pin each GitHub Action and each reusable workflow to a full commit SHA. Add
the tag or the branch of that SHA as a comment after it.

    - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1

### Renovate dependencies

Renovate updates the GitHub Actions, the Docker base images, and the three
Scylla driver versions in `build.xml`. Its pull requests use the
`chore(deps):` subject. Update the other `build.xml` dependencies by hand.

### Ant build

Ant is the build tool. `build.xml` holds the targets. The `Makefile`
targets call Ant. Add a build step as an Ant target.
