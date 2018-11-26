(ns exoscale.compute.api.zone
  "Higher order functions to manipulate keypairs.
   In addition to standard facilities, this also
   bundles an opt-in atom-backed store for private keys
   created here."
  (:refer-clojure :exclude [list])
  (:require [exoscale.compute.api.client :as client]
            [exoscale.compute.api.meta   :as meta]
            [manifold.deferred           :as d]
            [exoscale.compute.api.spec]))

(defn sanitize
  [z]
  (meta/describe {:id   (client/parse-uuid (:id z))
                  :name (:name z)}
                 :exoscale.compute/zone
                 z))

(defn by-name
  [config zname]
  (d/chain (client/api-call config :list-zones {:name (name zname)})
           (fn [resp]
             (when-not (= 1 (count resp))
               (throw (IllegalArgumentException.
                       "cannot resolve zone")))
             (sanitize (first resp)))))

(defn list
  "List virtual machines"
  [config]
  (d/chain (client/api-call config :list-zones)
           #(mapv sanitize %)))

(comment

  @(list config)
  @(by-name config :ch-dk-2)
  @(list config)
  @(delete config :kp1)
  @(list config)


)
