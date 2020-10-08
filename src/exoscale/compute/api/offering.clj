(ns exoscale.compute.api.offering
  "Higher order functions to manipulate VMs"
  (:refer-clojure :exclude [update list])
  (:require [exoscale.compute.api.client :as client]
            [exoscale.compute.api.meta   :as meta]
            [clojure.string              :as str]
            [qbits.auspex                :as auspex]))

(defn sanitize-offering
  [{:keys [authorized cpunumber id memory name displaytext] :as resp}]
  (meta/describe
   {:id          (client/parse-uuid id)
    :authorized? authorized
    :cpu         cpunumber
    :memory      memory
    :name        (-> name str/lower-case keyword)
    :description displaytext}
   :exoscale.compute/offering
   resp))

(defn by-name
  "The infamous name resolver, using listVirtualMachines since
   Cloudstack does not allow singular get calls"
  [config offering]
  (auspex/chain (client/api-call config :list-service-offerings {:name (name offering)})
                (fn [resp]
                  (when-not (= 1 (count resp))
                    (throw (IllegalArgumentException.
                            "cannot resolve service offering")))
                  (sanitize-offering (first resp)))))

(defn list
  "List virtual machines"
  [config]
  (auspex/chain (client/api-call config :list-service-offerings)
                (fn [offerings] (mapv sanitize-offering offerings))))

(comment

  @(by-name config :jumbo)
  @(list config)
)
