(ns exoscale.compute.api.eip
  "Higher order functions to manipulate keypairs.
   In addition to standard facilities, this also
   bundles an opt-in atom-backed store for private keys
   created here."
  (:refer-clojure :exclude [update list])
  (:require [exoscale.compute.api.client :as client]
            [qbits.auspex                :as auspex]
            [clojure.string              :as str]))


(defn sanitize
  [eip]
  (with-meta {:id     (client/parse-uuid (:id eip))
              :zone   (:zonename eip)
              :ipv4   {:address (:ipaddress eip)}
              :tags   (:tags eip)
              :bound? (= (:state eip) "Allocated")
              :state  (-> (:state eip) str/lower-case keyword)}
    {:exoscale.compute/type   :exoscale.compute/eip
     :exoscale.compute/zone   (client/parse-uuid (:zoneid eip))
     :exoscale.compute/source eip}))

(comment
  (defn by-name
    [config eipname]
    (auspex/chain (client/api-call config :list-zones {:name (name zname)})
                  (fn [resp]
                    (when-not (= 1 (count resp))
                      (throw (IllegalArgumentException.
                              "cannot resolve zone")))
                    (sanitize (first resp))))))

(defn list
  "List virtual machines"
  [config]
  (auspex/chain
   (client/api-call config :list-public-ip-addresses {:iselastic true})
   #(mapv sanitize %)))

(comment

  @(list config)


  )
