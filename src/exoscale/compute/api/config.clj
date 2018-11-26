(ns exoscale.compute.api.config
  "Facilities to load Exoscale configuration from files.
   Provide compatibility with other Exoscale libraries.

   TOML is recommended since it will be compatible with
   Python and Golang Exoscale libraries"
  (:require [toml.core          :as toml]
            [clojure.edn        :as edn]
            [clojure.spec.alpha :as spec]
            [clojure.java.io    :as io]
            [clojure.java.shell :as shell]
            [clojure.string     :as str]
            [exoscale.compute.api.spec])
  (:import java.nio.file.Paths
           java.io.File))

(defn make-path
  "Join path elements"
  [base & elems]
  (Paths/get base (into-array String elems)))

(defn config-home
  "Find out where this system expects configurations to be stored"
  []
  (or (System/getenv "XDG_CONFIG_HOME")
      (make-path (System/getenv "HOME") ".config")))

(defn config-dir
  "Yield the most appropriate location for the exoscale config dir"
  []
  (make-path (config-home) "exoscale"))

(defn files
  "Yield a sorted list of files within a directory, non-recursively"
  [path]
  (sort-by str (.listFiles (io/file path))))

(def known-extensions
  "Known configuration file extensions"
  #{:edn :toml})

(defn valid?
  "Predicate to test for validity"
  [{:keys [extension file prefix]}]
  (and (.isFile ^File file)
       (= "exoscale" prefix)
       (contains? known-extensions extension)))

(defmulti load-target
  "Open protocol to deserialize configuration files"
  :extension)

(defmethod load-target :edn
  [{:keys [file]}]
  (edn/read-string (slurp file)))

(defmethod load-target :toml
  [{:keys [file]}]
  (toml/read (slurp file) :keywordize))

(defmethod load-target :default
  [_]
  (throw (ex-info "unknown configuration file extension" {})))

(defn ^{:no-doc true} ->target
  "Describe file as a map of prefix, extension, and file.
   This simplifies further processing."
  [^File f]
  (let [elems (str/split (str f) #"\.")]
    {:prefix    (str/join "." (drop-last elems))
     :extension (some-> elems last str/lower-case keyword)
     :file      f}))

(defn try-load
  []
  (first
   (for [f     (files (config-dir))
         :let  [target (->target f)]
         :when (valid? target)]
     (try
       (load-target target)
       (catch Exception _)))))

(defn find-account
  "Given a configuration, yield the designated account"
  [{:keys [default defaultaccount accounts]} overrides]
  (if-some [k (or (:default overrides)
                  (System/getenv "EXOSCALE_ACCOUNT")
                  default
                  defaultaccount)]
    (some->> accounts
             (filter #(= (:name %) (name k)))
             (first))
    {}))

(defn environment-overrides
  "Fetch list of possible overrides from the environment"
  []
  {:api-key    (System/getenv "EXOSCALE_API_KEY")
   :api-secret (System/getenv "EXOSCALE_API_SECRET")
   :endpoint   (System/getenv "EXOSCALE_ENDPOINT")})

(defn ^{:no-doc true} process-secret
  "If the api secret is configured to be provided
   by a command, run it"
  [{:keys [secretcommand api-secret] :as cfg}]
  (assoc cfg :api-secret
         (or api-secret
             (->> secretcommand
                  (str/join " ")
                  (shell/sh)
                  (:out)
                  (str/trim)))))

(defn ^{:no-doc true} update-account
  ""
  [{:keys [api-key key api-secret secret]}]
  {:api-key    (or api-key key)
   :api-secret (or api-secret secret)})


(defn validate!
  [config]
  (when-not (spec/valid? ::client-opts config)
    (throw (ex-info "invalid client configuration" {})))
  config)

(defn from-environment
  "Try"
  ([]
   (from-environment {}))
  ([overrides]
   (let [cfg          (try-load)
         account-name (or (:default overrides)
                          (System/getenv "EXOSCALE_ACCOUNT")
                          (:default cfg)
                          (:defaultaccount cfg))
         account      (find-account (:accounts cfg) )]
     (-> (find-account cfg overrides)
         (update-account)
         (merge (environment-overrides))
         (merge (select-keys [:api-secret :api-secret :endpoint] overrides))
         (process-secret)
         (validate!)))))
