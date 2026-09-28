## CI and build

### Pinned actions

Pin each GitHub Action to a full commit SHA. Add the release tag as a
comment after the SHA.

    - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1

### Renovate dependencies

Renovate updates the dependencies, the actions, and the base images. Its
pull requests use the `chore(deps):` subject. Update a dependency by hand
only when a task needs a version that Renovate did not propose.

### Ant build

Ant is the build tool. `build.xml` holds the targets. The `Makefile`
targets call Ant. Add a build step as an Ant target.
