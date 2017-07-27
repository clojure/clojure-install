clojure-install
=====================================

The Clojure install project is used to bootstrap a system to include Clojure,
tools.deps.alpha, and the scripts necessary to use them to obtain more 
dependencies and run a Clojure repl or programs.

The installer assumes only that Java is available. The install tool is written
in Java and uses with the necessary Maven Aether libraries to traverse and
download the initial dependency set.

In general, this project will be obtained and used within system installers rather
than used directly.

## Release Information

Latest release: TBD 

* [All released versions](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.clojure%22%20AND%20a%3A%22clojure-install%22)

[Leiningen](http://github.com/technomancy/leiningen/) dependency information:

```
[org.clojure/clojure-install "TBD"]
```

[Maven](http://maven.apache.org) dependency information:

```
<dependency>
  <groupId>org.clojure</groupId>
  <artifactId>clojure-install</artifactId>
  <version>TBD</version>
</dependency>
```


## Usage

### `clojure.tools.Install`

The `Install` program performs the following steps:

* Read `~/.clojure/clj.props`
* Use those props to form a dependency set
* Download those dependencies and all transitive dependencies to the local Maven repository
* If `~/.clojure/clj.cp` or `~/.clojure/deps.edn` exist, copy them to backup files
* Create `~/.clojure/clj.cp` - used when invoking tools.deps.alpha
* Create `~/.clojure/deps.edn` - user-level deps map, for declaring default deps and artifact providers

This can be re-run to pick up manual changes in `clj.props`.

### `clj.props` file

Example props file:

```
org.clojure/clojure=1.9.0-alpha17
org.clojure/tools.deps.alpha=0.1.14
org.clojure/spec.alpha=0.1.123
```

## Developer Information

* [GitHub project](https://github.com/clojure/clojure-install)
* [How to contribute](https://dev.clojure.org/display/community/Contributing)
* [Bug Tracker](https://dev.clojure.org/jira/browse/INST)
* [Continuous Integration](https://build.clojure.org/job/clojure-install/)
* [Compatibility Test Matrix](https://build.clojure.org/job/clojure-install-test-matrix/)

## License

Copyright © 2017 Rich Hickey and contributors

Distributed under the Eclipse Public License, the same as Clojure.
