clojure-exoscale: Clojure Exoscale library
==========================================

A Clojure library to interact with Exoscale resources.
Documentation at https://exoscale.github.io/clojure-exoscale

## Breaking changes

* Starting from `1.0.0-alpha1` we no longer rely on manifold/aleph, we
  instead use the http client from jdk11, which returns
  CompletableFutures.

  This is mostly compatible with manifold code since it accepts
  CompletableFuture as a deferable value.  There is, however, one
  major difference in the error returned, CompletableFuture exceptions
  will be wrapped with CompletionException when passed to
  manifold.deferred/catch or if you deref directly the return value of
  api calls. You will have to reach the `ex-cause` manually from your
  code or rely on a library that does this for you automatically.
