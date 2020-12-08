(defproject exoscale/exoscale "0.2.2-SNAPSHOT"
  :description "All things Exoscale, in Clojure"
  :url "https://github.com/exoscale/clojure-exoscale"
  :plugins [[lein-kibit      "0.1.6"]
            [lein-ancient    "0.6.15"]
            [jonase/eastwood "0.3.3"]
            [lein-codox      "0.10.6"]]
  :codox {:source-uri "https://github.com/exoscale/clojure-exoscale/blob/{version}/{filepath}#L{line}"
          :doc-files  ["doc/intro.md"]
          :metadata   {:doc/format :markdown}}

  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]]

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [cheshire            "5.10.0"]
                 [exoscale/telex      "0.1.0"]
                 [toml                "0.1.3"]
                 [exoscale/cloak      "0.1.3"]]
  :pedantic? :abort
  :profiles {:dev {:dependencies [[tortue/spy "2.4.0"]]
                   :global-vars  {*warn-on-reflection* true}
                   :pedantic?    :warn}})
