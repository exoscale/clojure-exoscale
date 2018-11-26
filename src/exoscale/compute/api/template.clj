(ns exoscale.compute.api.template
  "Higher order functions to manipulate keypairs.
   In addition to standard facilities, this also
   bundles an opt-in atom-backed store for private keys
   created here."
  (:refer-clojure :exclude [update list])
  (:require [exoscale.compute.api.client :as client]
            [exoscale.compute.api.meta   :as meta]
            [manifold.deferred           :as d])  )

(defn sanitize
  [t]
  (-> (select-keys t [:name :size :details :created])
      (assoc :id (client/parse-uuid (:id t)))
      (assoc :zone (:zonename t))
      (meta/describe :exoscale.compute/template t)))

(defn by-zone-name
  [config zid tname]
  (d/chain (client/api-call config :list-templates {:name           tname
                                                    :templatefilter "featured"
                                                    :zoneid         (str zid)})
           (fn [resp]
             (when (empty? resp)
               (throw (ex-info "could not find any template to satisfy criteria"
                               {:zoneid zid
                                :name   tname})))
             (-> (sort-by :created resp) (last) (sanitize)))))

(defn by-zone-id
  [config zid tid]
  (d/chain (client/api-call config :list-templates {:id             (str tid)
                                                    :zoneid         (str zid)
                                                    :templatefilter "featured"})
           (fn [resp]
             (when (not= 1 (count resp))
               (throw (ex-info "could not find any template to satisfy criteria"
                               {:id     tid
                                :zoneid zid
                                :resp   resp})))
             (sanitize (first resp)))))

(defn list
  "List virtual machines"
  [config]
  (d/chain (client/api-call config :list-templates {:templatefilter "featured"})
           #(mapv sanitize %)))

(comment

  @(by-zone-name config #uuid "b0fcd72f-47ad-4779-a64f-fe4de007ec72" "Linux Debian 9 64-bit")
  @(by-id config "40485b50-b43a-4e12-a118-3888377a507d")
  @(list config)
  @(by-name config :ch-dk-2)
  @(list config)
  @(delete config :kp1)
  @(list config)


)
