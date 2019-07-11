# Test Bundles

The installer integration test needs non-trivial test bundles.

We are using the eventing-example project, which was temporarily modified to create a second version.

The `bnd-indexer-maven-plugin` configuration in `eventing-example/single-framework-example/pom.xml`was amended to temporarily generate mostly non-local URLs:

```
<configuration>
    <localURLs>ALLOWED</localURLs>
</configuration>
```

The generated index was then copied here and edited to replace the remaining `file:` URLs with `./` relative URLs and the jar copied here.

A second version `0.0.2-SNAPSHOT` of the `eventing-example` was temporarily created, and its index and local jars copied here.

A bad index was then created by copying `index-0.0.2.xml` to `index-0.0.2-bad.xml` and editing one URL to a non-existent file. This allows us to test rollback, when an install fails.







