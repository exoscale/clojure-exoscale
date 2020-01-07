(defproject exoscale/exoscale "0.1.10"
  :description "All things Exoscale, in Clojure"
  :url "https://github.com/exoscale/clojure-exoscale"
  :plugins [[lein-kibit      "0.1.6"]
            [lein-ancient    "0.6.15"]
            [jonase/eastwood "0.3.3"]
            [lein-codox      "0.10.6"]]
  :codox {:source-uri  "https://github.com/exoscale/clojure-exoscale/blob/{version}/{filepath}#L{line}"
          :doc-files   ["doc/intro.md"]
          :metadata    {:doc/format :markdown}}

  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]]

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [cheshire            "5.8.1"]
                 [clj-time            "0.15.1"]
                 [aleph               "0.4.6"]
                 [com.jcraft/jsch     "0.1.54"]
                 [toml                "0.1.3"]])
