(ns exoscale.compute.api.client
  (:require [exoscale.compute.api.http :as http]
            [clojure.string            :as str]))

(defn capitalizer
  "Capitalize Cloudstack word appropriately"
  [input]
  (if (contains? #{"lb" "ssh" "vpc" "vm"} input)
    (str/upper-case input)
    (str/capitalize input)))

(defn api-name
  "Given a hyphenated name, yield a camel case one"
  [op]
  (cond
    (or (keyword? op) (symbol? op))
    (let [[prelude & rest] (str/split (name op) #"-")]
      (apply str prelude (map capitalizer rest)))

    (string? op)
    op

    :else
    (throw (IllegalArgumentException. "cannot coerce to opcode"))))

(defn parse-uuid
  "Cloudstack only knows string UUIDs, coerce
   to real ones"
  [u]
  (when (some? u)
    (java.util.UUID/fromString u)))

(defn api-call
  ([client opcode]
   (http/request!! client (api-name opcode) {}))
  ([client opcode params]
   (http/request!! client (api-name opcode) params)))
